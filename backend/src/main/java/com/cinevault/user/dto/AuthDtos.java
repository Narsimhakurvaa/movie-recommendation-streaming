package com.cinevault.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Set;

/**
 * Request and response payloads for authentication.
 *
 * <p>Grouped in one file because they form a single cohesive contract and are
 * always read together; splitting seven short records across seven files would
 * add navigation cost without adding clarity.
 */
public final class AuthDtos {

    private AuthDtos() {
    }

    /**
     * Registration request.
     *
     * <p>The password policy is enforced here <em>and</em> mirrored in the
     * frontend. Backend validation is authoritative: the client-side copy only
     * exists to give immediate feedback.
     */
    @Schema(name = "RegisterRequest")
    public record RegisterRequest(
            @NotBlank @Email @Size(max = 320)
            @Schema(example = "ada@example.com") String email,

            @NotBlank
            @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
            @Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter")
            @Pattern(regexp = ".*[a-z].*", message = "Password must contain a lowercase letter")
            @Pattern(regexp = ".*\\d.*", message = "Password must contain a digit")
            @Schema(example = "Str0ngPassphrase!") String password,

            @NotBlank @Size(min = 2, max = 80)
            @Schema(example = "Ada Lovelace") String displayName,

            @Schema(description = "Genre slugs chosen during onboarding; seeds cold-start recommendations",
                    example = "[\"science-fiction\", \"drama\"]")
            List<String> favouriteGenreSlugs) {
    }

    @Schema(name = "LoginRequest")
    public record LoginRequest(
            @NotBlank @Email @Schema(example = "ada@example.com") String email,
            @NotBlank @Schema(example = "Str0ngPassphrase!") String password) {
    }

    @Schema(name = "RefreshRequest")
    public record RefreshRequest(
            @NotBlank @Schema(description = "The opaque refresh token issued at login")
            String refreshToken) {
    }

    /**
     * Issued token pair.
     *
     * @param accessToken  short-lived bearer token for the Authorization header
     * @param refreshToken opaque token, rotated on every use
     * @param expiresIn    access token lifetime in seconds
     */
    @Schema(name = "AuthResponse")
    public record AuthResponse(
            String accessToken,
            String refreshToken,
            @Schema(example = "Bearer") String tokenType,
            @Schema(example = "900") long expiresIn,
            AuthenticatedUser user) {

        public static AuthResponse of(String accessToken, String refreshToken,
                                      long expiresIn, AuthenticatedUser user) {
            return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
        }
    }

    /** The signed-in user, as returned alongside a token pair. */
    @Schema(name = "AuthenticatedUser")
    public record AuthenticatedUser(
            Long id,
            String email,
            String displayName,
            String avatarUrl,
            Set<String> roles,
            boolean emailVerified,
            boolean onboardingCompleted) {
    }

    @Schema(name = "PasswordResetRequest")
    public record PasswordResetRequest(
            @NotBlank @Email String email) {
    }

    @Schema(name = "PasswordResetConfirmation")
    public record PasswordResetConfirmation(
            @NotBlank String token,
            @NotBlank
            @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
            @Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter")
            @Pattern(regexp = ".*[a-z].*", message = "Password must contain a lowercase letter")
            @Pattern(regexp = ".*\\d.*", message = "Password must contain a digit")
            String newPassword) {
    }

    @Schema(name = "ChangePasswordRequest")
    public record ChangePasswordRequest(
            @NotBlank String currentPassword,
            @NotBlank
            @Size(min = 12, max = 128, message = "Password must be between 12 and 128 characters")
            @Pattern(regexp = ".*[A-Z].*", message = "Password must contain an uppercase letter")
            @Pattern(regexp = ".*[a-z].*", message = "Password must contain a lowercase letter")
            @Pattern(regexp = ".*\\d.*", message = "Password must contain a digit")
            String newPassword) {
    }

    /** Generic acknowledgement for endpoints that must not leak account state. */
    @Schema(name = "MessageResponse")
    public record MessageResponse(String message) {
    }
}
