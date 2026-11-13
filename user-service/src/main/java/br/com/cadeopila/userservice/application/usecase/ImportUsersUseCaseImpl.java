package br.com.cadeopila.userservice.application.usecase;

import br.com.cadeopila.userservice.core.domain.ImportResult;
import br.com.cadeopila.userservice.core.exception.UserAlreadyExistsException;
import br.com.cadeopila.userservice.core.ports.in.CreateUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.ImportUsersUseCase;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Importa usuários linha a linha REUSANDO o {@link CreateUserUseCase} existente.
 *
 * <p><b>Resiliência (requisito crítico):</b> este método NÃO é {@code @Transactional}. Cada chamada a
 * {@code createUser.create(...)} roda na própria transação (o {@code @Transactional} está no
 * {@code CreateUserUseCaseImpl}). Assim, uma linha inválida — 400 de validação ou 409 de duplicidade —
 * é capturada e vira item de erro, sem abortar nem marcar como rollback as demais. Se este laço fosse
 * transacional, a primeira falha envenenaria toda a importação.</p>
 *
 * <p>A validação usa Bean Validation (requisito #9) via {@link Validator}. Para preservar o core como
 * "JDK only", as constraints ficam aqui, no modelo {@link UserImportModel} (espelho do contrato web
 * {@code CreateUserRequest} — esse permanece a fonte canônica do endpoint JSON individual).</p>
 */
@Service
public class ImportUsersUseCaseImpl implements ImportUsersUseCase {

    private final CreateUserUseCase createUser;
    private final Validator validator;

    public ImportUsersUseCaseImpl(CreateUserUseCase createUser, Validator validator) {
        this.createUser = createUser;
        this.validator = validator;
    }

    @Override
    public ImportResult importUsers(List<CsvRow> rows) {
        var errors = new ArrayList<ImportResult.LineError>();
        var createdUsers = new ArrayList<ImportResult.CreatedUser>();
        int success = 0;

        for (CsvRow row : rows) {
            try {
                validateOrThrow(row.command());            // 400 (Bean Validation)
                var created = createUser.create(row.command()); // reusa a lógica existente; pode lançar 409
                success++;
                if (created != null) {
                    // Guarda defensiva: em runtime o create nunca devolve null; a checagem só blinda o laço
                    // contra uma implementação (ou mock) que o faça, sem quebrar a contagem de sucesso.
                    createdUsers.add(new ImportResult.CreatedUser(created.getId(), created.getEmail()));
                }
            } catch (ImportValidationException e) {
                errors.add(new ImportResult.LineError(row.lineNumber(), e.getMessage()));
            } catch (UserAlreadyExistsException e) {
                errors.add(new ImportResult.LineError(row.lineNumber(), e.getMessage()));
            } catch (RuntimeException e) {
                // Qualquer outra falha inesperada (ex.: violação de constraint no banco) não derruba o lote.
                errors.add(new ImportResult.LineError(row.lineNumber(), "Não foi possível importar esta linha."));
            }
        }
        return ImportResult.of(rows.size(), success, createdUsers, errors);
    }

    private void validateOrThrow(CreateUserUseCase.Command cmd) {
        var violations = validator.validate(
                new UserImportModel(cmd.name(), cmd.email(), cmd.cpf(), cmd.rawPassword()));
        if (!violations.isEmpty()) {
            var reason = violations.stream()
                    .map(ConstraintViolation::getMessage)
                    .sorted()
                    .collect(Collectors.joining("; "));
            throw new ImportValidationException(reason);
        }
    }

    /** Falha de validação local, convertida em erro de linha (não propaga para o controller). */
    private static final class ImportValidationException extends RuntimeException {
        private ImportValidationException(String message) {
            super(message);
        }
    }

    /** Alvo das constraints de Bean Validation para o canal CSV (espelha {@code CreateUserRequest}). */
    public record UserImportModel(
            @NotBlank(message = "Nome é obrigatório")
            @Size(max = 100, message = "Nome deve ter no máximo 100 caracteres")
            String name,

            @NotBlank(message = "Email é obrigatório")
            @Email(message = "Email deve ser válido")
            @Size(max = 150, message = "Email deve ter no máximo 150 caracteres")
            String email,

            @NotBlank(message = "CPF é obrigatório")
            @Pattern(regexp = "\\d{11}", message = "CPF deve conter exatamente 11 dígitos numéricos")
            String cpf,

            @NotBlank(message = "Senha é obrigatória")
            @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres")
            String password
    ) {}
}
