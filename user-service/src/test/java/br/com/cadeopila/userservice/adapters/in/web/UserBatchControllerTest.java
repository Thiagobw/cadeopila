package br.com.cadeopila.userservice.adapters.in.web;

import br.com.cadeopila.userservice.core.domain.ImportResult;
import br.com.cadeopila.userservice.core.ports.in.ImportUsersUseCase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserBatchController.class)
@AutoConfigureMockMvc(addFilters = false) // slice de controller: segurança/Basic Auth é coberta em outro teste
class UserBatchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserCsvParser csvParser;
    @MockitoBean
    private ImportUsersUseCase importUsers;

    @Test
    @DisplayName("POST /batch devolve 200 com o resumo da importação")
    void importDevolveResumo() throws Exception {
        when(csvParser.parse(any())).thenReturn(List.of());
        when(importUsers.importUsers(any())).thenReturn(
                ImportResult.of(3, 2, List.of(new ImportResult.LineError(4, "Email deve ser válido"))));

        var file = new MockMultipartFile("file", "usuarios.csv", "text/csv",
                "conteudo".getBytes(StandardCharsets.UTF_8));

        mockMvc.perform(multipart("/api/v1/users/batch").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalProcessed").value(3))
                .andExpect(jsonPath("$.totalSuccess").value(2))
                .andExpect(jsonPath("$.totalFailed").value(1))
                .andExpect(jsonPath("$.errors[0].line").value(4));
    }
}
