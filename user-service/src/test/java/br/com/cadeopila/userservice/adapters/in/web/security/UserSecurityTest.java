package br.com.cadeopila.userservice.adapters.in.web.security;

import br.com.cadeopila.userservice.adapters.in.web.UserController;
import br.com.cadeopila.userservice.adapters.in.web.mapper.UserWebMapper;
import br.com.cadeopila.userservice.core.domain.PageResult;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.ports.in.CreateUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.DeleteUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.FindUserByIdUseCase;
import br.com.cadeopila.userservice.core.ports.in.ListUsersUseCase;
import br.com.cadeopila.userservice.core.ports.in.UpdateUserUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import({SecurityConfig.class, RestAuthenticationEntryPoint.class, UserWebMapper.class})
class UserSecurityTest {

    private MockMvc mockMvc;

    @MockitoBean
    private CreateUserUseCase createUser;
    @MockitoBean
    private FindUserByIdUseCase findUserById;
    @MockitoBean
    private ListUsersUseCase listUsers;
    @MockitoBean
    private UpdateUserUseCase updateUser;
    @MockitoBean
    private DeleteUserUseCase deleteUser;

    @BeforeEach
    void setUp(WebApplicationContext context) {
        // O módulo webmvc-test do Spring Boot 4 não aplica springSecurity() ao MockMvc auto-configurado;
        // montamos manualmente para exercer a cadeia real de filtros (Basic Auth) e o bridge do @WithMockUser.
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/users sem credenciais deve retornar 401")
    void listSemAuthDeveRetornar401() throws Exception {
        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("DELETE /api/v1/users/{id} sem credenciais deve retornar 401")
    void deleteSemAuthDeveRetornar401() throws Exception {
        mockMvc.perform(delete("/api/v1/users/" + UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/users (cadastro) é público: NÃO retorna 401")
    void createEhPublico() throws Exception {
        var now = LocalDateTime.now();
        when(createUser.create(any())).thenReturn(
                new User(UUID.randomUUID(), "Maria", "maria@email.com", "12345678901", "HASH", now, now));

        var body = """
                {"name":"Maria","email":"maria@email.com","cpf":"12345678901","password":"senha123"}
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users autenticado deve retornar 200")
    void listAutenticadoDeveRetornar200() throws Exception {
        when(listUsers.list(any())).thenReturn(new PageResult<>(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/v1/users"))
                .andExpect(status().isOk());
    }
}
