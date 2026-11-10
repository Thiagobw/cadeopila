package br.com.cadeopila.userservice.core.domain;

/**
 * Mascaramento de CPF reutilizável pelo domínio: expõe apenas os dois últimos dígitos
 * (ex.: {@code ***.***.***-01}). Centraliza a regra para que o CPF cru nunca vaze em
 * respostas da API ({@link User#maskedCpf()}), logs ou no erro 409 de CPF duplicado.
 */
public final class CpfMasker {

    private static final String FULLY_MASKED = "***.***.***-**";

    private CpfMasker() {
    }

    public static String mask(String cpf) {
        if (cpf == null || cpf.length() < 11) {
            return FULLY_MASKED;
        }
        return "***.***.***-" + cpf.substring(9);
    }
}
