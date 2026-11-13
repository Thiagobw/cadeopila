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

    /** Echa um User criado a partir do comando, com id gerado — como faz o CreateUserUseCase real. */
    private static User userFromCommand(CreateUserUseCase.Command cmd) {
        return new User(UUID.randomUUID(), cmd.name(), cmd.email(), cmd.cpf(), "hash",
                LocalDateTime.now(), LocalDateTime.now());
    }

    @Test
    @DisplayName("Todas as linhas válidas: total de sucesso bate, create é chamado por linha e created[] traz id+e-mail")
    void todasValidas() {
        when(createUser.create(any())).thenAnswer(inv -> userFromCommand(inv.getArgument(0)));

        var result = useCase.importUsers(List.of(
                row(2, "Ana", "ana@email.com", "11122233344", "Senha@Teste123"),
                row(3, "Bruno", "bruno@email.com", "55566677788", "Senha@Teste123")));

        assertThat(result.totalProcessed()).isEqualTo(2);
        assertThat(result.totalSuccess()).isEqualTo(2);
        assertThat(result.totalFailed()).isZero();
        verify(createUser, times(2)).create(any());

        // Fecha o ovo-e-galinha: cada linha válida volta com o UUID gerado + e-mail (sem senha/hash/CPF).
        assertThat(result.created())
                .extracting(ImportResult.Created::line, ImportResult.Created::email)
                .containsExactly(tuple(2, "ana@email.com"), tuple(3, "bruno@email.com"));
        assertThat(result.created()).allSatisfy(c -> assertThat(c.id()).isNotNull());
    }

    @Test
    @DisplayName("E-mail inválido vira erro 400 na linha, NÃO interrompe as demais e fica fora de created[]")
    void emailInvalidoNaoDerrubaLote() {
        when(createUser.create(any())).thenAnswer(inv -> userFromCommand(inv.getArgument(0)));

        var result = useCase.importUsers(List.of(
                row(2, "Ana", "ana@email.com", "11122233344", "Senha@Teste123"),
                row(3, "Carlos", "carlos-email-invalido", "99988877766", "Senha@Teste123"),
                row(4, "Bruno", "bruno@email.com", "55566677788", "Senha@Teste123")));

        assertThat(result.totalSuccess()).isEqualTo(2);
        assertThat(result.totalFailed()).isEqualTo(1);
        assertThat(result.errors()).singleElement()
                .satisfies(e -> {
                    assertThat(e.line()).isEqualTo(3);
                    assertThat(e.reason()).contains("Email");
                });
        verify(createUser, times(2)).create(any()); // só as válidas chegam ao create
        // created[] tem só as válidas (Carlos, linha 3, fica de fora).
        assertThat(result.created())
                .extracting(ImportResult.Created::line, ImportResult.Created::email)
                .containsExactly(tuple(2, "ana@email.com"), tuple(4, "bruno@email.com"));
    }

    @Test
    @DisplayName("Duplicidade (409) é capturada por linha, a importação continua e só a criada entra em created[]")
    void duplicidadeNaoDerrubaLote() {
        when(createUser.create(any()))
                .thenAnswer(inv -> userFromCommand(inv.getArgument(0)))
                .thenThrow(new UserAlreadyExistsException("email", "bruno@email.com"));

        var result = useCase.importUsers(List.of(
                row(2, "Ana", "ana@email.com", "11122233344", "Senha@Teste123"),
                row(3, "Bruno", "bruno@email.com", "55566677788", "Senha@Teste123")));

        assertThat(result.totalSuccess()).isEqualTo(1);
        assertThat(result.totalFailed()).isEqualTo(1);
        assertThat(result.errors().get(0).line()).isEqualTo(3);
        assertThat(result.created()).singleElement().satisfies(c -> {
            assertThat(c.line()).isEqualTo(2);
            assertThat(c.email()).isEqualTo("ana@email.com");
            assertThat(c.id()).isNotNull();
        });
    }
}
