package br.com.cadeopila.userservice.adapters.in.web;

import br.com.cadeopila.userservice.adapters.in.web.mapper.UserWebMapper;
import br.com.cadeopila.userservice.core.domain.PageResult;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserAlreadyExistsException;
import br.com.cadeopila.userservice.core.ports.in.CreateUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.DeleteUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.FindUserByIdUseCase;
import br.com.cadeopila.userservice.core.ports.in.ListUsersUseCase;
import br.com.cadeopila.userservice.core.ports.in.UpdateUserUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@Import(UserWebMapper.class)
class UserControllerTest {

    @Autowired
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

    @Test
    @DisplayName("GET com page negativa deve retornar 400 indicando o campo page")
    void listComPageNegativaDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("page"));
    }

    @Test
    @DisplayName("GET com size zero deve retornar 400 indicando o campo size")
    void listComSizeZeroDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("size", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("size"));
    }

    @Test
    @DisplayName("GET com size acima do limite deve retornar 400 indicando o campo size")
    void listComSizeAcimaDoLimiteDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("size", "200"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("size"));
    }

    @Test
    @DisplayName("GET com page não numérica deve retornar 400 indicando o campo page")
    void listComPageNaoNumericaDeveRetornar400() throws Exception {
        mockMvc.perform(get("/api/v1/users").param("page", "abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("page"));
    }

    @Test
    @DisplayName("GET com paginação válida deve retornar 200")
    void listComPaginacaoValidaDeveRetornar200() throws Exception {
        when(listUsers.list(any())).thenReturn(new PageResult<>(List.of(), 0, 0, 0, 20));

        mockMvc.perform(get("/api/v1/users").param("page", "0").param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    @DisplayName("POST com email duplicado deve retornar 409")
    void createComEmailDuplicadoDeveRetornar409() throws Exception {
        when(createUser.create(any()))
                .thenThrow(new UserAlreadyExistsException("email", "maria@email.com"));

        var body = """
                {"name":"Maria","email":"maria@email.com","cpf":"12345678901","password":"senha123"}
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST com CPF duplicado deve retornar 409 com o CPF mascarado no corpo (sem expor o CPF cru)")
    void createComCpfDuplicadoDeveRetornar409ComCpfMascarado() throws Exception {
        // O caso de uso já mascara o CPF na origem; o handler apenas repassa o valor seguro.
        when(createUser.create(any()))
                .thenThrow(new UserAlreadyExistsException("cpf", "***.***.***-01"));

        var body = """
                {"name":"Maria","email":"maria@email.com","cpf":"12345678901","password":"senha123"}
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.field").value("cpf"))
                .andExpect(jsonPath("$.value").value("***.***.***-01"))
                .andExpect(jsonPath("$.detail").value(endsWith("***.***.***-01")));
    }

    @Test
    @DisplayName("POST com senha acima de 72 caracteres deve retornar 400 indicando o campo password")
    void createComSenhaAcimaDe72DeveRetornar400() throws Exception {
        var senhaLonga = "a".repeat(73);
        var body = """
                {"name":"Maria","email":"maria@email.com","cpf":"12345678901","password":"%s"}
                """.formatted(senhaLonga);

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.violations[0].field").value("password"));
    }

    @Test
    @DisplayName("POST válido deve retornar 201 com Location e email normalizado na chamada ao caso de uso")
    void createValidoDeveRetornar201ComEmailNormalizado() throws Exception {
        var id = UUID.randomUUID();
        var now = LocalDateTime.now();
        when(createUser.create(any())).thenAnswer(inv -> {
            CreateUserUseCase.Command cmd = inv.getArgument(0);
            assertThat(cmd.email()).isEqualTo("maria.souza@email.com");
            return new User(id, cmd.name(), cmd.email(), "12345678901", "HASH", now, now);
        });

        var body = """
                {"name":"Maria Souza","email":"  Maria.Souza@Email.COM  ","cpf":"12345678901","password":"senha123"}
                """;

        mockMvc.perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", endsWith("/api/v1/users/" + id)))
                .andExpect(jsonPath("$.email").value("maria.souza@email.com"));
    }
}
