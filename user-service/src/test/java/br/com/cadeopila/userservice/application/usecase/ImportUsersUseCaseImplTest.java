package br.com.cadeopila.userservice.application.usecase;

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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    @DisplayName("Todas as linhas válidas: total de sucesso bate e create é chamado por linha")
    void todasValidas() {
        var result = useCase.importUsers(List.of(
                row(2, "Ana", "ana@email.com", "11122233344", "Senha@Teste123"),
                row(3, "Bruno", "bruno@email.com", "55566677788", "Senha@Teste123")));

        assertThat(result.totalProcessed()).isEqualTo(2);
        assertThat(result.totalSuccess()).isEqualTo(2);
        assertThat(result.totalFailed()).isZero();
        verify(createUser, times(2)).create(any());
    }

    @Test
    @DisplayName("E-mail inválido vira erro 400 na linha e NÃO interrompe as demais (resiliência)")
    void emailInvalidoNaoDerrubaLote() {
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
    }

    @Test
    @DisplayName("Duplicidade (409) é capturada por linha e a importação continua")
    void duplicidadeNaoDerrubaLote() {
        when(createUser.create(any()))
                .thenReturn(null)
                .thenThrow(new UserAlreadyExistsException("email", "bruno@email.com"));

        var result = useCase.importUsers(List.of(
                row(2, "Ana", "ana@email.com", "11122233344", "Senha@Teste123"),
                row(3, "Bruno", "bruno@email.com", "55566677788", "Senha@Teste123")));

        assertThat(result.totalSuccess()).isEqualTo(1);
        assertThat(result.totalFailed()).isEqualTo(1);
        assertThat(result.errors().get(0).line()).isEqualTo(3);
    }
}
