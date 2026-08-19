package com.cinevault.interaction.web;

import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.interaction.dto.InteractionDtos.RecordInteractionRequest;
import com.cinevault.interaction.dto.InteractionDtos.WatchHistoryResponse;
import com.cinevault.interaction.service.WatchHistoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Watch history.
 *
 * <p>Doubles as the implicit-signal intake for the recommendation engine: the
 * client reports what the user did, and those events feed genre and keyword
 * affinity alongside explicit ratings.
 */
@RestController
@RequestMapping("/api/history")
@Validated
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Watch history", description = "Interaction tracking and history")
public class WatchHistoryController {

    private final WatchHistoryService watchHistoryService;

    public WatchHistoryController(WatchHistoryService watchHistoryService) {
        this.watchHistoryService = watchHistoryService;
    }

    @GetMapping
    @Operation(summary = "View your history", description = "Most recent activity first.")
    public ResponseEntity<PageResponse<WatchHistoryResponse>> findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(watchHistoryService.findForUser(principal.userId(),
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "occurredAt"))));
    }

    @PostMapping("/movies/{movieId}")
    @Operation(summary = "Record an interaction",
            description = "Reports an action such as watching a trailer or completing a "
                    + "film. Repeated low-value events are deduplicated so idle browsing "
                    + "cannot drown out deliberate signals.")
    public ResponseEntity<Void> record(@PathVariable Long movieId,
                                       @Valid @RequestBody RecordInteractionRequest request,
                                       @CurrentUser JwtPrincipal principal) {
        watchHistoryService.record(principal.userId(), movieId,
                request.interactionType(), request.progressPercent());
        return ResponseEntity.accepted().build();
    }
}
