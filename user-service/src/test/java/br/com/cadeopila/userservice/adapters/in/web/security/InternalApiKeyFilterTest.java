package br.com.cadeopila.userservice.adapters.in.web.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class InternalApiKeyFilterTest {

    private static final String KEY = "chave-interna-secreta";
    private final InternalApiKeyFilter filter = new InternalApiKeyFilter(KEY);

    private MockHttpServletRequest validateRequest() {
        return new MockHttpServletRequest("POST", "/api/v1/auth/validate");
    }

    @Test
    @DisplayName("Com a chave correta deve seguir a cadeia (200)")
    void comChaveCorretaDeveSeguir() throws Exception {
        var request = validateRequest();
        request.addHeader(InternalApiKeyFilter.INTERNAL_KEY_HEADER, KEY);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(chain.getRequest()).isNotNull();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Sem o header deve bloquear com 401 e não seguir a cadeia")
    void semHeaderDeveBloquear() throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(validateRequest(), response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Com chave diferente deve bloquear com 401")
    void comChaveDiferenteDeveBloquear() throws Exception {
        var request = validateRequest();
        request.addHeader(InternalApiKeyFilter.INTERNAL_KEY_HEADER, "chave-errada");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    @DisplayName("Chave não configurada no serviço fecha o endpoint (secure-by-default)")
    void semChaveConfiguradaDeveBloquear() throws Exception {
        var filterSemChave = new InternalApiKeyFilter("");
        var request = validateRequest();
        request.addHeader(InternalApiKeyFilter.INTERNAL_KEY_HEADER, "qualquer");
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filterSemChave.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(chain.getRequest()).isNull();
    }
}
