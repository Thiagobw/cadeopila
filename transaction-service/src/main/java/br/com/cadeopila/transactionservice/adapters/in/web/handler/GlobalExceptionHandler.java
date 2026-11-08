package br.com.cadeopila.transactionservice.adapters.in.web.handler;

import br.com.cadeopila.transactionservice.core.exception.AccountNotFoundException;
import br.com.cadeopila.transactionservice.core.exception.AccountUnavailableException;
import br.com.cadeopila.transactionservice.core.exception.InvalidPaginationException;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;
import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionFilterException;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotDeletableException;
import br.com.cadeopila.transactionservice.core.exception.TransactionNotFoundException;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final String INVALID_TITLE = "Dados inválidos";
    private static final String INVALID_DETAIL = "A requisição contém campos com valores inválidos.";

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        var problem = badRequest();
        List<Map<String, String>> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", resolveMessage(fe)))
                .toList();
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException ex) {
        var problem = badRequest();
        List<Map<String, String>> violations = ex.getConstraintViolations().stream()
                .map(cv -> Map.of("field", lastPathNode(cv.getPropertyPath()), "message", cv.getMessage()))
                .toList();
        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        var problem = badRequest();
        String typeName = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconhecido";
        problem.setProperty("violations", List.of(
                Map.of("field", ex.getName(), "message", "tipo inválido: era esperado " + typeName)));
        return problem;
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ProblemDetail handleMissingParam(MissingServletRequestParameterException ex) {
        var problem = badRequest();
        problem.setProperty("violations", List.of(
                Map.of("field", ex.getParameterName(), "message", "é obrigatório")));
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        var problem = badRequest();
        problem.setDetail("Corpo da requisição ausente, malformado ou com valor fora do domínio.");
        return problem;
    }

    @ExceptionHandler({InvalidTransactionException.class, InvalidTransactionFilterException.class,
            InvalidPaginationException.class})
    public ProblemDetail handleInvalidDomainInput(RuntimeException ex) {
        var problem = badRequest();
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(TransactionNotFoundException.class)
    public ProblemDetail handleNotFound(TransactionNotFoundException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Recurso não encontrado");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(TransactionNotDeletableException.class)
    public ProblemDetail handleNotDeletable(TransactionNotDeletableException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.UNPROCESSABLE_ENTITY);
        problem.setTitle("Operação não permitida");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.CONFLICT);
        problem.setTitle("Conflito de concorrência");
        problem.setDetail("A transação foi modificada por outra operação; tente novamente.");
        return problem;
    }

    @ExceptionHandler(AccountNotFoundException.class)
    public ProblemDetail handleAccountNotFound(AccountNotFoundException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.NOT_FOUND);
        problem.setTitle("Recurso não encontrado");
        problem.setDetail(ex.getMessage());
        return problem;
    }

    @ExceptionHandler(AccountUnavailableException.class)
    public ProblemDetail handleAccountUnavailable(AccountUnavailableException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.SERVICE_UNAVAILABLE);
        problem.setTitle("Serviço indisponível");
        problem.setDetail("Não foi possível consultar a API de contas. Tente novamente mais tarde.");
        return problem;
    }

    private ProblemDetail badRequest() {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(INVALID_TITLE);
        problem.setDetail(INVALID_DETAIL);
        return problem;
    }

    private String resolveMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "valor inválido";
    }

    private String lastPathNode(Path path) {
        String last = null;
        for (Path.Node node : path) {
            last = node.getName();
        }
        return last != null ? last : path.toString();
    }
}
