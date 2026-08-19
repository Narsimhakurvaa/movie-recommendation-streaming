package com.cinevault.catalogue.web;

import com.cinevault.catalogue.dto.MovieDtos.MovieDetail;
import com.cinevault.catalogue.dto.MovieDtos.MovieSuggestionResponse;
import com.cinevault.catalogue.dto.MovieDtos.MovieSummary;
import com.cinevault.catalogue.dto.MovieSearchCriteria;
import com.cinevault.catalogue.service.MovieService;
import com.cinevault.common.dto.ApiError;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.interaction.domain.InteractionType;
import com.cinevault.interaction.service.WatchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Catalogue browsing.
 *
 * <p>Every endpoint here is readable anonymously; a signed-in caller
 * additionally receives their own watchlist and rating state on each card.
 */
@RestController
@RequestMapping("/api/movies")
@Validated
@Tag(name = "Movies", description = "Catalogue browsing, search and detail")
public class MovieController {

    private final MovieService movieService;
    private final WatchHistoryService watchHistoryService;

    public MovieController(MovieService movieService, WatchHistoryService watchHistoryService) {
        this.movieService = movieService;
        this.watchHistoryService = watchHistoryService;
    }

    @GetMapping
    @Operation(summary = "Browse the catalogue",
            description = "Filter by genre, year, rating, language and runtime, with "
                    + "allowlisted sorting. All filters are optional and combinable.")
    @ApiResponse(responseCode = "200", description = "A page of movies")
    public ResponseEntity<PageResponse<MovieSummary>> discover(
            @Valid @ModelAttribute MovieSearchCriteria criteria,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(movieService.discover(
                criteria, PageRequest.of(page, size), userId(principal)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search by title",
            description = "Same filter and sort surface as browsing, with a required term.")
    public ResponseEntity<PageResponse<MovieSummary>> search(
            @RequestParam @Size(min = 1, max = 120) String query,
            @Valid @ModelAttribute MovieSearchCriteria criteria,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser JwtPrincipal principal) {
        var withQuery = new MovieSearchCriteria(query, criteria.genres(), criteria.matchAllGenres(),
                criteria.yearFrom(), criteria.yearTo(), criteria.minRating(), criteria.maxRating(),
                criteria.languages(), criteria.minRuntime(), criteria.maxRuntime(),
                criteria.includeAdult(), criteria.sort());
        return ResponseEntity.ok(movieService.search(
                withQuery, PageRequest.of(page, size), userId(principal)));
    }

    @GetMapping("/suggest")
    @Operation(summary = "Search suggestions",
            description = "Lightweight type-ahead results. Returns an empty list for "
                    + "queries shorter than two characters.")
    public ResponseEntity<List<MovieSuggestionResponse>> suggest(
            @RequestParam @Size(max = 120) String query,
            @RequestParam(defaultValue = "8") @Min(1) @Max(20) int limit) {
        return ResponseEntity.ok(movieService.suggest(query, limit));
    }

    @GetMapping("/{movieId}")
    @Operation(summary = "Movie detail",
            description = "Full metadata including cast, crew, trailer and the caller's "
                    + "own watchlist and rating state. Viewing is recorded as a "
                    + "recommendation signal for signed-in users.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "The movie"),
            @ApiResponse(responseCode = "404", description = "No such movie",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<MovieDetail> findById(
            @PathVariable @Parameter(description = "Movie identifier") Long movieId,
            @CurrentUser JwtPrincipal principal) {
        MovieDetail detail = movieService.findById(movieId, userId(principal));
        if (principal != null) {
            // Implicit interest signal; deduplicated by the history service.
            watchHistoryService.record(principal.userId(), movieId,
                    InteractionType.VIEWED_DETAILS, null);
        }
        return ResponseEntity.ok(detail);
    }

    @GetMapping("/slug/{slug}")
    @Operation(summary = "Movie detail by slug",
            description = "Identical to detail-by-id, for human-readable URLs.")
    public ResponseEntity<MovieDetail> findBySlug(@PathVariable String slug,
                                                  @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(movieService.findBySlug(slug, userId(principal)));
    }

    private static Long userId(JwtPrincipal principal) {
        return principal == null ? null : principal.userId();
    }
}
