package br.com.cadeopila.userservice.adapters.in.web;

import br.com.cadeopila.userservice.core.ports.in.CreateUserUseCase;
import br.com.cadeopila.userservice.core.ports.in.ImportUsersUseCase;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Converte um CSV ({@link MultipartFile}) em comandos puros de criação, isolando aqui TODA a mecânica de
 * arquivo (requisito de hexagonal): BOM UTF-8, detecção de separador ({@code ;} ou {@code ,}), aspas e
 * número de linha. Não valida regra de negócio — isso é da camada application; linhas com campos faltando
 * viram comandos com valores vazios que o use case rejeita (400) sem derrubar o lote.
 *
 * <p>Cabeçalho obrigatório com as colunas {@code name,email,cpf,password} (em qualquer ordem).</p>
 */
@Component
public class UserCsvParser {

    private static final int BOM = 0xFEFF;
    private static final List<String> REQUIRED_COLUMNS = List.of("name", "email", "cpf", "password");

    public List<ImportUsersUseCase.CsvRow> parse(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CsvImportException("O arquivo CSV está vazio ou não foi enviado.");
        }

        List<String> lines = readLines(file);
        int headerIndex = indexOfFirstNonBlank(lines);
        if (headerIndex < 0) {
            throw new CsvImportException("O arquivo CSV não possui cabeçalho.");
        }

        String headerLine = stripBom(lines.get(headerIndex));
        char delimiter = detectDelimiter(headerLine);
        Map<String, Integer> columns = mapColumns(splitLine(headerLine, delimiter));

        var rows = new ArrayList<ImportUsersUseCase.CsvRow>();
        for (int i = headerIndex + 1; i < lines.size(); i++) {
            String rawLine = lines.get(i);
            if (rawLine == null || rawLine.isBlank()) {
                continue; // tolera linhas em branco (inclusive a final)
            }
            List<String> fields = splitLine(stripBom(rawLine), delimiter);
            var command = new CreateUserUseCase.Command(
                    field(fields, columns.get("name")),
                    field(fields, columns.get("email")),
                    field(fields, columns.get("cpf")),
                    field(fields, columns.get("password")));
            rows.add(new ImportUsersUseCase.CsvRow(i + 1, command)); // linha 1-based do arquivo
        }

        if (rows.isEmpty()) {
            throw new CsvImportException("O arquivo CSV não contém linhas de dados.");
        }
        return rows;
    }

    private List<String> readLines(MultipartFile file) {
        // UTF-8 explícito: o encoding do upload não depende do default da JVM.
        try (var reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().toList();
        } catch (IOException e) {
            throw new CsvImportException("Não foi possível ler o arquivo CSV.");
        }
    }

    private int indexOfFirstNonBlank(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i) != null && !stripBom(lines.get(i)).isBlank()) {
                return i;
            }
        }
        return -1;
    }

    private char detectDelimiter(String headerLine) {
        long semicolons = headerLine.chars().filter(c -> c == ';').count();
        long commas = headerLine.chars().filter(c -> c == ',').count();
        return semicolons > commas ? ';' : ',';
    }

    private Map<String, Integer> mapColumns(List<String> header) {
        var columns = new LinkedHashMap<String, Integer>();
        for (int i = 0; i < header.size(); i++) {
            columns.put(header.get(i).trim().toLowerCase(Locale.ROOT), i);
        }
        var missing = REQUIRED_COLUMNS.stream().filter(c -> !columns.containsKey(c)).toList();
        if (!missing.isEmpty()) {
            throw new CsvImportException("Cabeçalho inválido: faltam as colunas " + missing
                    + ". Esperado: name,email,cpf,password.");
        }
        return columns;
    }

    private String field(List<String> fields, Integer index) {
        if (index == null || index >= fields.size()) {
            return null;
        }
        return fields.get(index);
    }

    private String stripBom(String line) {
        return (line != null && !line.isEmpty() && line.charAt(0) == BOM) ? line.substring(1) : line;
    }

    /** Split simples que respeita campos entre aspas duplas e o escape {@code ""}. */
    private List<String> splitLine(String line, char delimiter) {
        var fields = new ArrayList<String>();
        var current = new StringBuilder();
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    current.append('"'); // aspas escapadas
                    i++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (c == delimiter && !inQuotes) {
                fields.add(current.toString().trim());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        fields.add(current.toString().trim());
        return fields;
    }
}
