package br.com.cadeopila.userservice.adapters.in.web;

/**
 * Problema estrutural do arquivo CSV (vazio, sem cabeçalho, coluna obrigatória ausente, ilegível).
 * Diferente de erro de linha: aqui nem dá para começar a importar. Mapeado para 400 no handler global.
 */
public class CsvImportException extends RuntimeException {
    public CsvImportException(String message) {
        super(message);
    }
}
