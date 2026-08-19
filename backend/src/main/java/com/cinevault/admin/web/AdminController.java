package com.cinevault.admin.web;

import com.cinevault.admin.dto.AdminDtos.AdminUserResponse;
import com.cinevault.admin.dto.AdminDtos.DashboardStatistics;
import com.cinevault.admin.dto.AdminDtos.SetUserEnabledRequest;
import com.cinevault.admin.dto.AdminDtos.SyncResult;
import com.cinevault.admin.service.AdminService;
import com.cinevault.admin.service.CatalogueSyncService;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.interaction.dto.InteractionDtos.ModerateReviewRequest;
import com.cinevault.interaction.dto.InteractionDtos.ReviewResponse;
import com.cinevault.interaction.service.ReviewService;
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
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administration.
 *
 * <p>Protected twice over: the URL prefix is restricted to {@code ROLE_ADMIN}
 * in the security configuration, and every handler carries
 * {@code @PreAuthorize}. The redundancy is intentional - a future change to the
 * URL rules cannot silently open these endpoints.
 */
@RestController
@RequestMapping("/api/admin")
@Validated
@PreAuthorize("hasRole('ADMIN')")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Administration", description = "Dashboard, moderation and catalogue management")
public class AdminController {

    private final AdminService adminService;
    private final ReviewService reviewService;
    private final CatalogueSyncService catalogueSyncService;

    public AdminController(AdminService adminService,
                           ReviewService reviewService,
                           CatalogueSyncService catalogueSyncService) {
        this.adminService = adminService;
        this.reviewService = reviewService;
        this.catalogueSyncService = catalogueSyncService;
    }

    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Dashboard statistics",
            description = "User, catalogue and engagement metrics, plus the most popular "
                    + "titles, most active users and recommendation mix.")
    public ResponseEntity<DashboardStatistics> statistics() {
        return ResponseEntity.ok(adminService.dashboard());
    }

    @GetMapping("/users")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List users",
            description = "Searchable by name or email, filterable by enabled status.")
    public ResponseEntity<PageResponse<AdminUserResponse>> users(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) Boolean enabled,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok(adminService.findUsers(search, enabled,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PatchMapping("/users/{userId}/enabled")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Enable or disable a user",
            description = "Disabling also revokes every refresh token, so access ends "
                    + "immediately rather than when the access token expires.")
    public ResponseEntity<AdminUserResponse> setEnabled(
            @PathVariable Long userId,
            @Valid @RequestBody SetUserEnabledRequest request,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(adminService.setEnabled(principal.userId(), userId,
                request.enabled(), request.reason()));
    }

    @GetMapping("/reviews")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Moderation queue",
            description = "All reviews, including hidden ones. Filter by status.")
    public ResponseEntity<PageResponse<ReviewResponse>> reviews(
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(reviewService.findForModeration(status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt")),
                principal.userId()));
    }

    @PatchMapping("/reviews/{reviewId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Moderate a review",
            description = "Changes visibility without destroying the author's content, "
                    + "so the action is reversible.")
    public ResponseEntity<ReviewResponse> moderate(
            @PathVariable Long reviewId,
            @Valid @RequestBody ModerateReviewRequest request,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(reviewService.moderate(reviewId, request.status(),
                request.moderationNote(), principal.userId()));
    }

    @PostMapping("/catalogue/sync")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Synchronise the catalogue",
            description = "Imports popular and trending titles from the configured "
                    + "metadata provider. Existing films are updated, not duplicated.")
    public ResponseEntity<SyncResult> sync(
            @RequestParam(defaultValue = "1") @Min(1) @Max(10) int pages) {
        return ResponseEntity.ok(catalogueSyncService.synchronise(pages));
    }

    @GetMapping("/provider")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Metadata provider status",
            description = "Which provider is active and whether it is usable.")
    public ResponseEntity<java.util.Map<String, Object>> providerStatus() {
        return ResponseEntity.ok(catalogueSyncService.providerStatus());
    }
}
