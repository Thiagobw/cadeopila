package br.com.cadeopila.transactionservice.adapters.in.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

@Schema(description = "Página de resultados. 'content' traz os itens; os demais campos descrevem a paginação.")
public record PagedResponse<T>(

        @Schema(description = "Itens desta página")
        List<T> content,

        @Schema(description = "Total de itens em todas as páginas (segundo os filtros)", example = "51")
        long totalElements,

        @Schema(description = "Total de páginas", example = "3")
        int totalPages,

        @Schema(description = "Número da página atual (base 0)", example = "0")
        int page,

        @Schema(description = "Tamanho da página", example = "20")
        int size
) {}
