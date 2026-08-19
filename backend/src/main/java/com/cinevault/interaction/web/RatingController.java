package com.cinevault.interaction.web;

import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.interaction.dto.InteractionDtos.RatingRequest;
import com.cinevault.interaction.dto.InteractionDtos.RatingResponse;
import com.cinevault.interaction.service.RatingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Rating a film on a 1-5 scale.
 *
 * <p>POST and PUT both upsert. Re-rating is a normal action rather than a
 * conflict, and offering both verbs matches what clients expect without
 * introducing two different behaviours.
 */
@RestController
@RequestMapping("/api/movies/{movieId}/ratings")
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Ratings", description = "Rate films from 1 to 5")
public class RatingController {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @PostMapping
    @Operation(summary = "Rate a film",
            description = "Creates or replaces the caller's rating and returns the "
                    + "recalculated platform average.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Rating recorded"),
            @ApiResponse(responseCode = "400", description = "Score outside 1-5"),
            @ApiResponse(responseCode = "404", description = "No such movie")
    })
    public ResponseEntity<RatingResponse> rate(@PathVariable Long movieId,
                                               @Valid @RequestBody RatingRequest request,
                                               @CurrentUser JwtPrincipal principal) {
        var response = ratingService.rate(principal.userId(), movieId, request.score());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping
    @Operation(summary = "Update a rating",
            description = "Identical upsert behaviour to POST.")
    public ResponseEntity<RatingResponse> update(@PathVariable Long movieId,
                                                 @Valid @RequestBody RatingRequest request,
                                                 @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(ratingService.rate(principal.userId(), movieId, request.score()));
    }

    @DeleteMapping
    @Operation(summary = "Remove a rating")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Rating removed"),
            @ApiResponse(responseCode = "404", description = "The caller had not rated this film")
    })
    public ResponseEntity<Void> delete(@PathVariable Long movieId,
                                       @CurrentUser JwtPrincipal principal) {
        ratingService.deleteRating(principal.userId(), movieId);
        return ResponseEntity.noContent().build();
    }
}
