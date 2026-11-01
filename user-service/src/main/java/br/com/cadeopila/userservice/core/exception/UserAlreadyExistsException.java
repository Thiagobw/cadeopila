package br.com.cadeopila.userservice.core.exception;

public class UserAlreadyExistsException extends RuntimeException {

    private final String field;
    private final String value;

    public UserAlreadyExistsException(String field, String value) {
        super("Já existe um usuário com " + field + ": " + value);
        this.field = field;
        this.value = value;
    }

    public String getField() { return field; }
    public String getValue() { return value; }
}
