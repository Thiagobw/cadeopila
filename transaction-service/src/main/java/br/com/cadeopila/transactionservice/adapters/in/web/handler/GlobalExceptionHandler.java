package br.com.cadeopila.transactionservice.adapters.in.web.handler;

import br.com.cadeopila.transactionservice.core.exception.InvalidTransactionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
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
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(INVALID_TITLE);
        problem.setDetail(INVALID_DETAIL);

        List<Map<String, String>> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> Map.of("field", fe.getField(), "message", resolveMessage(fe)))
                .toList();

        problem.setProperty("violations", violations);
        return problem;
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ProblemDetail handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(INVALID_TITLE);
        problem.setDetail(INVALID_DETAIL);

        String typeName = ex.getRequiredType() != null ? ex.getRequiredType().getSimpleName() : "desconhecido";
        problem.setProperty("violations", List.of(
                Map.of("field", ex.getName(), "message", "tipo inválido: era esperado " + typeName)));
        return problem;
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail handleUnreadable(HttpMessageNotReadableException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(INVALID_TITLE);
        problem.setDetail("Corpo da requisição ausente, malformado ou com valor fora do domínio.");
        return problem;
    }

    @ExceptionHandler(InvalidTransactionException.class)
    public ProblemDetail handleInvalidTransaction(InvalidTransactionException ex) {
        var problem = ProblemDetail.forStatus(HttpStatus.BAD_REQUEST);
        problem.setTitle(INVALID_TITLE);
        problem.setDetail(ex.getMessage());
        return problem;
    }

    private String resolveMessage(FieldError fieldError) {
        return fieldError.getDefaultMessage() != null ? fieldError.getDefaultMessage() : "valor inválido";
    }
}
