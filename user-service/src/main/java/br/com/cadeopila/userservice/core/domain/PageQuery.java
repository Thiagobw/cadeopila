package br.com.cadeopila.userservice.core.domain;

import br.com.cadeopila.userservice.core.exception.InvalidPaginationException;

public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;

    public PageQuery {
        if (page < 0) {
            throw new InvalidPaginationException("page: deve ser maior ou igual a 0");
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new InvalidPaginationException("size: deve ser entre 1 e " + MAX_SIZE);
        }
    }
}
