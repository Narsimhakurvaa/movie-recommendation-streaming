package com.cinevault.catalogue.web;

import com.cinevault.catalogue.dto.MovieDtos.GenreResponse;
import com.cinevault.catalogue.dto.MovieDtos.MovieSummary;
import com.cinevault.catalogue.service.MovieService;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Genre listing and per-genre browsing. */
@RestController
@RequestMapping("/api/genres")
@Validated
@Tag(name = "Genres", description = "Genre taxonomy and browsing")
public class GenreController {

    private final MovieService movieService;

    public GenreController(MovieService movieService) {
        this.movieService = movieService;
    }

    @GetMapping
    @Operation(summary = "List genres",
            description = "All genres with the number of films in each. Cached, as the "
                    + "taxonomy changes only on catalogue import.")
    public ResponseEntity<List<GenreResponse>> findAll() {
        return ResponseEntity.ok(movieService.findAllGenres());
    }

    @GetMapping("/{slug}/movies")
    @Operation(summary = "Browse one genre")
    public ResponseEntity<PageResponse<MovieSummary>> findByGenre(
            @PathVariable String slug,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(movieService.findByGenre(slug, PageRequest.of(page, size),
                principal == null ? null : principal.userId()));
    }
}
