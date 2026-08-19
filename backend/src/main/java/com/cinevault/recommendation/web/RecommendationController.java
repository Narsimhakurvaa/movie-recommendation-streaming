package com.cinevault.recommendation.web;

import com.cinevault.catalogue.dto.MovieDtos.MovieSummary;
import com.cinevault.catalogue.service.MovieService;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.recommendation.dto.RecommendationDtos.RecommendationItem;
import com.cinevault.recommendation.service.RecommendationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Recommendation endpoints.
 *
 * <p>Personalised routes require authentication; the curated rails
 * (trending, popular, top rated, new releases, similar) are public so the home
 * page is useful before anyone signs in.
 */
@RestController
@RequestMapping("/api/recommendations")
@Validated
@Tag(name = "Recommendations", description = "Personalised and curated recommendations")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final MovieService movieService;

    public RecommendationController(RecommendationService recommendationService,
                                    MovieService movieService) {
        this.recommendationService = recommendationService;
        this.movieService = movieService;
    }

    @GetMapping
    @Operation(summary = "Recommendations for the caller",
            description = "Personalised when signed in, popularity-ranked otherwise. "
                    + "Each item carries a score, the strategy that produced it, and a "
                    + "reason derived from the signals that actually drove the ranking.")
    public ResponseEntity<PageResponse<RecommendationItem>> recommendations(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @CurrentUser JwtPrincipal principal) {
        if (principal == null) {
            return ResponseEntity.ok(recommendationService.recommendForAnonymous(page, size));
        }
        var result = recommendationService.recommendFor(principal.userId(), page, size);
        // Log only the first page: later pages are usually pagination noise
        // rather than genuine impressions.
        if (page == 0) {
            recommendationService.recordServed(principal.userId(), result.content());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/personalized")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Personalised recommendations",
            description = "Explicitly authenticated variant of the default endpoint.")
    public ResponseEntity<PageResponse<RecommendationItem>> personalized(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @CurrentUser JwtPrincipal principal) {
        var result = recommendationService.recommendFor(principal.userId(), page, size);
        if (page == 0) {
            recommendationService.recordServed(principal.userId(), result.content());
        }
        return ResponseEntity.ok(result);
    }

    @GetMapping("/similar/{movieId}")
    @Operation(summary = "Similar movies",
            description = "Metadata similarity against a seed film: shared genres, "
                    + "keywords, cast, director and language, with a light quality tilt. "
                    + "Consults no user profile, so it works for anonymous visitors.")
    public ResponseEntity<PageResponse<RecommendationItem>> similar(
            @PathVariable Long movieId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ResponseEntity.ok(recommendationService.findSimilar(movieId, page, size));
    }

    @GetMapping("/trending")
    @Operation(summary = "Trending now",
            description = "Recent releases ranked by popularity.")
    public ResponseEntity<PageResponse<MovieSummary>> trending(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(movieService.findTrending(PageRequest.of(page, size), userId(principal)));
    }

    @GetMapping("/popular")
    @Operation(summary = "Popular movies")
    public ResponseEntity<PageResponse<MovieSummary>> popular(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(movieService.findPopular(PageRequest.of(page, size), userId(principal)));
    }

    @GetMapping("/top-rated")
    @Operation(summary = "Top rated",
            description = "Highest rated films that clear a minimum vote count, so a "
                    + "single enthusiastic rating cannot top the chart.")
    public ResponseEntity<PageResponse<MovieSummary>> topRated(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(movieService.findTopRated(PageRequest.of(page, size), userId(principal)));
    }

    @GetMapping("/new-releases")
    @Operation(summary = "New releases")
    public ResponseEntity<PageResponse<MovieSummary>> newReleases(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(movieService.findNewReleases(PageRequest.of(page, size), userId(principal)));
    }

    private static Long userId(JwtPrincipal principal) {
        return principal == null ? null : principal.userId();
    }
}
