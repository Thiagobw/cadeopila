package br.com.cadeopila.userservice.adapters.in.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserCsvParserTest {

    private final UserCsvParser parser = new UserCsvParser();

    private MockMultipartFile csv(String content) {
        return new MockMultipartFile("file", "usuarios.csv", "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("CSV válido separado por vírgula gera comandos com os campos e número de linha corretos")
    void parseVirgula() {
        var rows = parser.parse(csv("""
                name,email,cpf,password
                Ana Souza,ana@email.com,11122233344,Senha@Teste123
                Bruno Lima,bruno@email.com,55566677788,Senha@Teste123
                """));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).lineNumber()).isEqualTo(2);
        assertThat(rows.get(0).command().name()).isEqualTo("Ana Souza");
        assertThat(rows.get(0).command().email()).isEqualTo("ana@email.com");
        assertThat(rows.get(1).command().cpf()).isEqualTo("55566677788");
    }

    @Test
    @DisplayName("Detecta separador ponto-e-vírgula e remove BOM UTF-8")
    void parsePontoVirgulaComBom() {
        var bom = (char) 0xFEFF;
        var rows = parser.parse(csv(bom + "name;email;cpf;password\nAna;ana@email.com;11122233344;Senha@Teste123\n"));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).command().name()).isEqualTo("Ana");
        assertThat(rows.get(0).command().email()).isEqualTo("ana@email.com");
    }

    @Test
    @DisplayName("Colunas em ordem diferente são mapeadas pelo cabeçalho")
    void parseColunasForaDeOrdem() {
        var rows = parser.parse(csv("""
                email,name,password,cpf
                ana@email.com,Ana,Senha@Teste123,11122233344
                """));

        assertThat(rows.get(0).command().name()).isEqualTo("Ana");
        assertThat(rows.get(0).command().cpf()).isEqualTo("11122233344");
    }

    @Test
    @DisplayName("Linhas em branco são ignoradas")
    void ignoraLinhasEmBranco() {
        var rows = parser.parse(csv("name,email,cpf,password\nAna,ana@email.com,11122233344,Senha@Teste123\n\n"));
        assertThat(rows).hasSize(1);
    }

    @Test
    @DisplayName("Campos entre aspas com separador interno são respeitados")
    void respeitaAspas() {
        var rows = parser.parse(csv("""
                name,email,cpf,password
                "Souza, Ana",ana@email.com,11122233344,Senha@Teste123
                """));

        assertThat(rows.get(0).command().name()).isEqualTo("Souza, Ana");
    }

    @Test
    @DisplayName("Arquivo vazio lança CsvImportException")
    void arquivoVazio() {
        assertThatThrownBy(() -> parser.parse(csv("")))
                .isInstanceOf(CsvImportException.class);
    }

    @Test
    @DisplayName("Cabeçalho sem coluna obrigatória lança CsvImportException")
    void cabecalhoIncompleto() {
        assertThatThrownBy(() -> parser.parse(csv("name,email,password\nAna,ana@email.com,Senha@Teste123\n")))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("cpf");
    }
}
