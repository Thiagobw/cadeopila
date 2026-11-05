package br.com.cadeopila.userservice.core.domain;

import java.util.List;

public record PageResult<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int pageNumber,
        int pageSize
) {}
