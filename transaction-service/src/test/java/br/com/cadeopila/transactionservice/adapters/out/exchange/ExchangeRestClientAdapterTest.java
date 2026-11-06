package br.com.cadeopila.transactionservice.adapters.out.exchange;

import br.com.cadeopila.transactionservice.core.domain.ExchangeRate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ExchangeRestClientAdapterTest {

    private static final String BASE_URL = "http://brasilapi";
    private static final LocalDate DATE = LocalDate.of(2024, 5, 10);

    private MockRestServiceServer server;
    private ExchangeRestClientAdapter adapter;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        adapter = new ExchangeRestClientAdapter(builder.build());
    }

    private String url(LocalDate date) {
        return BASE_URL + "/cambio/v1/cotacao/USD/" + date;
    }

    @Test
    @DisplayName("Deve usar a cotação de venda do boletim FECHAMENTO e converter o horário de Brasília para Instant")
    void deveUsarFechamentoEConverterZona() {
        server.expect(requestTo(url(DATE)))
                .andRespond(withSuccess("""
                        {"cotacoes":[
                          {"cotacao_venda":5.10,"data_hora_cotacao":"2024-05-10 10:00:00.000","tipo_boletim":"ABERTURA"},
                          {"cotacao_venda":5.15,"data_hora_cotacao":"2024-05-10 13:00:00.000","tipo_boletim":"FECHAMENTO PTAX"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        Optional<ExchangeRate> rate = adapter.quote("USD", DATE);

        assertThat(rate).isPresent();
        assertThat(rate.get().currency()).isEqualTo("USD");
        assertThat(rate.get().rate()).isEqualByComparingTo("5.15");
        // 13:00 em America/Sao_Paulo (UTC-3) = 16:00Z
        assertThat(rate.get().quotedAt()).isEqualTo(Instant.parse("2024-05-10T16:00:00Z"));
        server.verify();
    }

    @Test
    @DisplayName("Sem boletim FECHAMENTO deve usar o boletim mais recente do dia")
    void semFechamentoDeveUsarMaisRecente() {
        server.expect(requestTo(url(DATE)))
                .andRespond(withSuccess("""
                        {"cotacoes":[
                          {"cotacao_venda":5.10,"data_hora_cotacao":"2024-05-10 10:00:00.000","tipo_boletim":"ABERTURA"},
                          {"cotacao_venda":5.12,"data_hora_cotacao":"2024-05-10 12:00:00.000","tipo_boletim":"INTERMEDIÁRIO"}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        Optional<ExchangeRate> rate = adapter.quote("USD", DATE);

        assertThat(rate).isPresent();
        assertThat(rate.get().rate()).isEqualByComparingTo("5.12");
        assertThat(rate.get().quotedAt()).isEqualTo(Instant.parse("2024-05-10T15:00:00Z"));
        server.verify();
    }

    @Test
    @DisplayName("Com lista vazia deve retroceder um dia e usar a cotação do dia anterior")
    void listaVaziaDeveRetroceder() {
        server.expect(requestTo(url(DATE)))
                .andRespond(withSuccess("{\"cotacoes\":[]}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(url(DATE.minusDays(1))))
                .andRespond(withSuccess("""
                        {"cotacoes":[{"cotacao_venda":5.09,"data_hora_cotacao":"2024-05-09 13:00:00.000","tipo_boletim":"FECHAMENTO PTAX"}]}
                        """, MediaType.APPLICATION_JSON));

        Optional<ExchangeRate> rate = adapter.quote("USD", DATE);

        assertThat(rate).isPresent();
        assertThat(rate.get().rate()).isEqualByComparingTo("5.09");
        server.verify();
    }

    @Test
    @DisplayName("Após 7 dias sem dados deve retornar vazio (7 tentativas)")
    void seteDiasSemDadosDeveRetornarVazio() {
        for (int i = 0; i < 7; i++) {
            server.expect(requestTo(url(DATE.minusDays(i))))
                    .andRespond(withSuccess("{\"cotacoes\":[]}", MediaType.APPLICATION_JSON));
        }

        assertThat(adapter.quote("USD", DATE)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Erro 5xx deve parar na hora e retornar vazio (tolerante a falha)")
    void erro5xxDeveRetornarVazioSemRetroceder() {
        server.expect(requestTo(url(DATE))).andRespond(withServerError());

        assertThat(adapter.quote("USD", DATE)).isEmpty();
        server.verify();
    }

    @Test
    @DisplayName("Erro 4xx (ex.: data inválida) deve parar na hora e retornar vazio")
    void erro4xxDeveRetornarVazio() {
        server.expect(requestTo(url(DATE)))
                .andRespond(withStatus(org.springframework.http.HttpStatus.BAD_REQUEST));

        assertThat(adapter.quote("USD", DATE)).isEmpty();
        server.verify();
    }
}
