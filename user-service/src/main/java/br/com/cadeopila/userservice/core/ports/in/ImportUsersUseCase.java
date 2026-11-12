package br.com.cadeopila.userservice.core.ports.in;

import br.com.cadeopila.userservice.core.domain.ImportResult;

import java.util.List;

/**
 * Importação em massa de usuários (GAP #1). Recebe comandos já extraídos do arquivo pela camada web,
 * cada um com o número da linha de origem para rastrear erros. O core permanece JDK-only: a validação
 * (Bean Validation) acontece na implementação, na camada application.
 */
public interface ImportUsersUseCase {

    ImportResult importUsers(List<CsvRow> rows);

    /**
     * Um {@link CreateUserUseCase.Command} puro + a linha de onde veio no arquivo (1-based, inclui o
     * cabeçalho na contagem). O comando em si continua puro; o wrapper só carrega a origem para o relatório.
     */
    record CsvRow(int lineNumber, CreateUserUseCase.Command command) {}
}
