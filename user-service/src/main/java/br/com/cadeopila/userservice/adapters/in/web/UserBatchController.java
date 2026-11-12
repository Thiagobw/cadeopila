package br.com.cadeopila.userservice.adapters.in.web;

import br.com.cadeopila.userservice.adapters.in.web.dto.ImportResultResponse;
import br.com.cadeopila.userservice.core.ports.in.ImportUsersUseCase;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Cadastro em massa via CSV (GAP #1). O controller só orquestra: delega o parsing ao {@link UserCsvParser}
 * (camada web) e a criação resiliente ao {@link ImportUsersUseCase} (application). Devolve 200 com o resumo
 * — a resposta sempre volta 200 porque linhas rejeitadas são esperadas e reportadas, não um erro da chamada.
 */
@Tag(name = "Usuários", description = "Gerenciamento de perfis de usuário")
@RestController
@RequestMapping("/api/v1/users")
public class UserBatchController {

    private final UserCsvParser csvParser;
    private final ImportUsersUseCase importUsers;

    public UserBatchController(UserCsvParser csvParser, ImportUsersUseCase importUsers) {
        this.csvParser = csvParser;
        this.importUsers = importUsers;
    }

    @Operation(summary = "Importar usuários em massa via CSV",
            description = "Recebe um arquivo CSV (multipart/form-data, campo 'file') com as colunas "
                    + "name,email,cpf,password. Tolera BOM UTF-8 e separador ';' ou ','. "
                    + "Linhas inválidas (CPF/e-mail inválido, duplicado) são rejeitadas individualmente "
                    + "sem interromper a importação; o corpo traz o total de sucesso/falha e os erros por linha.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Importação processada (ver resumo no corpo)"),
            @ApiResponse(responseCode = "400", description = "Arquivo ausente, vazio ou com cabeçalho inválido")
    })
    @PostMapping(path = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultResponse> importCsv(@RequestParam("file") MultipartFile file) {
        var rows = csvParser.parse(file);
        var result = importUsers.importUsers(rows);
        return ResponseEntity.ok(ImportResultResponse.from(result));
    }
}
