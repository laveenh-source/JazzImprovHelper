package dev.laveenh.jazzanalyzer.api.dto;

import java.util.List;
import java.util.function.Function;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <S, T> PageResponse<T> of(List<S> rows, long total, int page, int size, Function<S, T> mapper) {
        int totalPages = (int) ((total + size - 1) / size);
        return new PageResponse<>(rows.stream().map(mapper).toList(), page, size, total, totalPages);
    }
}
