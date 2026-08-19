package com.cinevault.interaction.web;

import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.interaction.dto.InteractionDtos.ReviewRequest;
import com.cinevault.interaction.dto.InteractionDtos.ReviewResponse;
import com.cinevault.interaction.service.ReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Reviews.
 *
 * <p>Reading is public; writing requires authentication. Ownership is checked
 * in the service layer, not here, so the rule holds for every caller.
 */
@RestController
@Validated
@Tag(name = "Reviews", description = "Written reviews and moderation")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @GetMapping("/api/movies/{movieId}/reviews")
    @Operation(summary = "Reviews for a film",
            description = "Publicly readable. Hidden reviews are excluded.")
    public ResponseEntity<PageResponse<ReviewResponse>> findForMovie(
            @PathVariable Long movieId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "newest") String sort,
            @CurrentUser JwtPrincipal principal) {
        Sort ordering = "oldest".equalsIgnoreCase(sort)
                ? Sort.by(Sort.Direction.ASC, "createdAt")
                : Sort.by(Sort.Direction.DESC, "createdAt");
        return ResponseEntity.ok(reviewService.findForMovie(movieId,
                PageRequest.of(page, size, ordering),
                principal == null ? null : principal.userId()));
    }

    @PostMapping("/api/movies/{movieId}/reviews")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Write a review",
            description = "One review per film per user. Bodies must be at least 20 "
                    + "characters and contain meaningful text.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Review published"),
            @ApiResponse(responseCode = "400", description = "Body failed validation"),
            @ApiResponse(responseCode = "409", description = "Already reviewed by this user")
    })
    public ResponseEntity<ReviewResponse> create(@PathVariable Long movieId,
                                                 @Valid @RequestBody ReviewRequest request,
                                                 @CurrentUser JwtPrincipal principal) {
        var response = reviewService.create(principal.userId(), movieId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/api/reviews/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Edit your review")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Review updated"),
            @ApiResponse(responseCode = "403", description = "Not the author"),
            @ApiResponse(responseCode = "404", description = "No such review")
    })
    public ResponseEntity<ReviewResponse> update(@PathVariable Long reviewId,
                                                 @Valid @RequestBody ReviewRequest request,
                                                 @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(reviewService.update(principal.userId(), reviewId, request));
    }

    @DeleteMapping("/api/reviews/{reviewId}")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a review",
            description = "Authors may delete their own; administrators may delete any.")
    public ResponseEntity<Void> delete(@PathVariable Long reviewId,
                                       @CurrentUser JwtPrincipal principal) {
        reviewService.delete(principal.userId(), reviewId, principal.isAdmin());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/reviews/mine")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Your reviews")
    public ResponseEntity<PageResponse<ReviewResponse>> mine(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(reviewService.findByUser(principal.userId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}
