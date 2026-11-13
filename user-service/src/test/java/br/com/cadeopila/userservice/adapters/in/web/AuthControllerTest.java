package br.com.cadeopila.userservice.adapters.in.web;

import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.InvalidCredentialsException;
import br.com.cadeopila.userservice.core.ports.in.ValidateCredentialsUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false) // o gate M2M (InternalApiKeyFilter) é testado em InternalApiKeyFilterTest
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ValidateCredentialsUseCase validateCredentials;

    @Test
    @DisplayName("POST válido deve retornar 200 com o userId")
    void validarComCredenciaisValidasDeveRetornar200() throws Exception {
        var id = UUID.randomUUID();
        var now = LocalDateTime.now();
        when(validateCredentials.validate(any()))
                .thenReturn(new User(id, "Maria", "maria@email.com", "12345678901", "HASH", now, now));

        var body = """
                {"email":"maria@email.com","password":"senha123"}
                """;

        mockMvc.perform(post("/api/v1/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(id.toString()));
    }

    @Test
    @DisplayName("Credenciais inválidas devem retornar 401")
    void validarComCredenciaisInvalidasDeveRetornar401() throws Exception {
        when(validateCredentials.validate(any())).thenThrow(new InvalidCredentialsException());

        var body = """
                {"email":"maria@email.com","password":"errada"}
                """;

        mockMvc.perform(post("/api/v1/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("E-mail em branco deve retornar 400 indicando o campo email")
    void validarComEmailEmBrancoDeveRetornar400() throws Exception {
        var body = """
                {"email":"","password":"senha123"}
                """;

        mockMvc.perform(post("/api/v1/auth/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("email"));
    }
}
