package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.ImportResult;
import br.com.cadeopila.userservice.core.domain.User;
import br.com.cadeopila.userservice.core.exception.UserAlreadyExistsException;
import br.com.cadeopila.userservice.core.ports.in.CreateUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.ImportUsersUseCase;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportUsersUseCaseImplTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    private final CreateUserUseCase createUser = mock(CreateUserUseCase.class);
    private final ImportUsersUseCaseImpl useCase = new ImportUsersUseCaseImpl(createUser, validator);

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private ImportUsersUseCase.CsvRow row(int line, String name, String email, String cpf, String pass) {
        return new ImportUsersUseCase.CsvRow(line, new CreateUserUseCase.Command(name, email, cpf, pass));
    }

    /** User mínimo como o {@code CreateUserUseCaseImpl} devolveria: id gerado + e-mail normalizado. */
    private User createdUser(UUID id, String email) {
        var now = LocalDateTime.now();
        return new User(id, "Nome", email, "00000000000", "hash", now, now);
    }

    @Test
    @DisplayName("Todas válidas: sucesso bate, create é chamado por linha e os ids/emails criados voltam")
    void todasValidas() {
        var anaId = UUID.randomUUID();
        var brunoId = UUID.randomUUID();
        when(createUser.create(any()))
                .thenReturn(createdUser(anaId, "ana@email.com"))
                .thenReturn(createdUser(brunoId, "bruno@email.com"));

        var result = useCase.importUsers(List.of(
                row(2, "Ana", "ana@email.com", "11122233344", "Senha@Teste123"),
                row(3, "Bruno", "bruno@email.com", "55566677788", "Senha@Teste123")));

        assertThat(result.totalProcessed()).isEqualTo(2);
        assertThat(result.totalSuccess()).isEqualTo(2);
        assertThat(result.totalFailed()).isZero();
        assertThat(result.createdUsers())
                .extracting(ImportResult.CreatedUser::id, ImportResult.CreatedUser::email)
                .containsExactly(
                        tuple(anaId, "ana@email.com"),
                        tuple(brunoId, "bruno@email.com"));
        verify(createUser, times(2)).create(any());
    }

    @Test
    @DisplayName("E-mail inválido vira erro 400 na linha e NÃO interrompe as demais (resiliência)")
    void emailInvalidoNaoDerrubaLote() {
        when(createUser.create(any()))
                .thenReturn(createdUser(UUID.randomUUID(), "ana@email.com"))
                .thenReturn(createdUser(UUID.randomUUID(), "bruno@email.com"));

        var result = useCase.importUsers(List.of(
                row(2, "Ana", "ana@email.com", "11122233344", "Senha@Teste123"),
                row(3, "Carlos", "carlos-email-invalido", "99988877766", "Senha@Teste123"),
                row(4, "Bruno", "bruno@email.com", "55566677788", "Senha@Teste123")));

        assertThat(result.totalSuccess()).isEqualTo(2);
        assertThat(result.totalFailed()).isEqualTo(1);
        assertThat(result.createdUsers()).hasSize(2); // só as válidas entram na lista de criados
        assertThat(result.errors()).singleElement()
                .satisfies(e -> {
                    assertThat(e.line()).isEqualTo(3);
                    assertThat(e.reason()).contains("Email");
                });
        verify(createUser, times(2)).create(any()); // só as válidas chegam ao create
    }

    @Test
    @DisplayName("Duplicidade (409) é capturada por linha, não entra em createdUsers e a importação continua")
    void duplicidadeNaoDerrubaLote() {
        var anaId = UUID.randomUUID();
        when(createUser.create(any()))
                .thenReturn(createdUser(anaId, "ana@email.com"))
                .thenThrow(new UserAlreadyExistsException("email", "bruno@email.com"));

        var result = useCase.importUsers(List.of(
                row(2, "Ana", "ana@email.com", "11122233344", "Senha@Teste123"),
                row(3, "Bruno", "bruno@email.com", "55566677788", "Senha@Teste123")));

        assertThat(result.totalSuccess()).isEqualTo(1);
        assertThat(result.totalFailed()).isEqualTo(1);
        assertThat(result.errors().get(0).line()).isEqualTo(3);
        assertThat(result.createdUsers())
                .singleElement()
                .satisfies(u -> {
                    assertThat(u.id()).isEqualTo(anaId);
                    assertThat(u.email()).isEqualTo("ana@email.com");
                });
    }
}
