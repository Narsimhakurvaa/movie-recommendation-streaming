package com.cinevault.interaction.web;

import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.interaction.dto.InteractionDtos.AddToWatchlistRequest;
import com.cinevault.interaction.dto.InteractionDtos.WatchlistItemResponse;
import com.cinevault.interaction.dto.InteractionDtos.WatchlistStatus;
import com.cinevault.interaction.service.WatchlistService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's saved films. */
@RestController
@RequestMapping("/api/watchlist")
@Validated
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Watchlist", description = "Films saved for later")
public class WatchlistController {

    private final WatchlistService watchlistService;

    public WatchlistController(WatchlistService watchlistService) {
        this.watchlistService = watchlistService;
    }

    @GetMapping
    @Operation(summary = "View the watchlist",
            description = "Sorted by when each film was added, newest first by default.")
    public ResponseEntity<PageResponse<WatchlistItemResponse>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(defaultValue = "desc") String direction,
            @CurrentUser JwtPrincipal principal) {
        Sort sort = Sort.by("asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC : Sort.Direction.DESC, "addedAt");
        return ResponseEntity.ok(watchlistService.findForUser(
                principal.userId(), PageRequest.of(page, size, sort)));
    }

    @PostMapping("/{movieId}")
    @Operation(summary = "Save a film")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Saved"),
            @ApiResponse(responseCode = "404", description = "No such movie"),
            @ApiResponse(responseCode = "409", description = "Already saved")
    })
    public ResponseEntity<WatchlistItemResponse> add(
            @PathVariable Long movieId,
            @Valid @RequestBody(required = false) AddToWatchlistRequest request,
            @CurrentUser JwtPrincipal principal) {
        var response = watchlistService.add(principal.userId(), movieId,
                request == null ? null : request.note());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @DeleteMapping("/{movieId}")
    @Operation(summary = "Remove a film")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Removed"),
            @ApiResponse(responseCode = "404", description = "Not in the watchlist")
    })
    public ResponseEntity<Void> remove(@PathVariable Long movieId,
                                       @CurrentUser JwtPrincipal principal) {
        watchlistService.remove(principal.userId(), movieId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{movieId}/status")
    @Operation(summary = "Check whether a film is saved",
            description = "Lets the UI render the correct save-button state without "
                    + "fetching the whole watchlist.")
    public ResponseEntity<WatchlistStatus> status(@PathVariable Long movieId,
                                                  @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(watchlistService.status(principal.userId(), movieId));
    }
}
