package com.cinevault.common.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * A stable pagination envelope.
 *
 * <p>Spring's {@code Page} is not returned directly because its JSON shape is
 * an implementation detail of Spring Data (and Spring itself warns that it is
 * subject to change). Pinning our own contract means a Spring upgrade cannot
 * silently break every client.
 *
 * @param content       the page of items
 * @param page          zero-based page index
 * @param size          requested page size
 * @param totalElements total matching items across all pages
 * @param totalPages    total number of pages
 * @param first         whether this is the first page
 * @param last          whether this is the last page
 */
@Schema(name = "PageResponse", description = "Paginated collection of results")
public record PageResponse<T>(
        List<T> content,
        @Schema(example = "0") int page,
        @Schema(example = "20") int size,
        @Schema(example = "137") long totalElements,
        @Schema(example = "7") int totalPages,
        boolean first,
        boolean last) {

    /** Wraps a Spring {@code Page} whose contents are already DTOs. */
    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    /** Wraps a Spring {@code Page} of entities, mapping each to a DTO. */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(page.getContent().stream().map(mapper).toList(),
                page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    /** An empty page, used for short-circuit paths that skip the database. */
    public static <T> PageResponse<T> empty(int page, int size) {
        return new PageResponse<>(List.of(), page, size, 0L, 0, true, true);
    }
}
