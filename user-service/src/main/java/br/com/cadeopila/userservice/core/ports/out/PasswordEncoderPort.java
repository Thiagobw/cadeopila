package br.com.cadeopila.userservice.core.ports.out;

public interface PasswordEncoderPort {

    String encode(String rawPassword);

    boolean matches(String rawPassword, String encodedPassword);
}
