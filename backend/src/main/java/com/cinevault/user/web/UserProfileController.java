package com.cinevault.user.web;

import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.interaction.repository.RecommendationLogRepository;
import com.cinevault.recommendation.dto.RecommendationDtos.RecommendationHistoryItem;
import com.cinevault.catalogue.service.MovieMapper;
import com.cinevault.user.dto.ProfileDtos.UpdatePreferencesRequest;
import com.cinevault.user.dto.ProfileDtos.UpdateProfileRequest;
import com.cinevault.user.dto.ProfileDtos.UserProfileResponse;
import com.cinevault.user.service.UserProfileService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The signed-in user's own profile, preferences and recommendation history. */
@RestController
@RequestMapping("/api/profile")
@Validated
@PreAuthorize("isAuthenticated()")
@SecurityRequirement(name = "bearerAuth")
@Tag(name = "Profile", description = "Profile, preferences and recommendation history")
public class UserProfileController {

    private final UserProfileService userProfileService;
    private final RecommendationLogRepository recommendationLogRepository;
    private final MovieMapper movieMapper;

    public UserProfileController(UserProfileService userProfileService,
                                 RecommendationLogRepository recommendationLogRepository,
                                 MovieMapper movieMapper) {
        this.userProfileService = userProfileService;
        this.recommendationLogRepository = recommendationLogRepository;
        this.movieMapper = movieMapper;
    }

    @GetMapping
    @Operation(summary = "Your profile",
            description = "Profile fields, activity counts, preferences and favourite genres.")
    public ResponseEntity<UserProfileResponse> profile(@CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(userProfileService.findProfile(principal.userId()));
    }

    @PutMapping
    @Operation(summary = "Update your profile")
    public ResponseEntity<UserProfileResponse> updateProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(userProfileService.updateProfile(principal.userId(), request));
    }

    @PutMapping("/preferences")
    @Operation(summary = "Update your preferences",
            description = "Minimum rating, languages and adult settings are applied as "
                    + "filters when generating recommendations; favourite genres feed "
                    + "the cold-start strategy.")
    public ResponseEntity<UserProfileResponse> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request,
            @CurrentUser JwtPrincipal principal) {
        return ResponseEntity.ok(userProfileService.updatePreferences(principal.userId(), request));
    }

    @GetMapping("/recommendation-history")
    @Operation(summary = "Recommendations you have been shown",
            description = "The recorded history of served recommendations, with the score "
                    + "and reason captured at the time.")
    public ResponseEntity<PageResponse<RecommendationHistoryItem>> recommendationHistory(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @CurrentUser JwtPrincipal principal) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "generatedAt"));
        return ResponseEntity.ok(PageResponse.from(
                recommendationLogRepository.findByUserId(principal.userId(), pageable),
                entry -> new RecommendationHistoryItem(
                        movieMapper.toSummary(entry.getMovie()),
                        entry.getScore() == null ? 0d : entry.getScore().doubleValue(),
                        entry.getReason(),
                        entry.getRecommendationType(),
                        entry.getGeneratedAt())));
    }
}
