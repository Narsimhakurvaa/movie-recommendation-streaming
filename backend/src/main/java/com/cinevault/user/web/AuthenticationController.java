package com.cinevault.user.web;

import com.cinevault.common.dto.ApiError;
import com.cinevault.common.security.CurrentUser;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.user.dto.AuthDtos.AuthResponse;
import com.cinevault.user.dto.AuthDtos.ChangePasswordRequest;
import com.cinevault.user.dto.AuthDtos.LoginRequest;
import com.cinevault.user.dto.AuthDtos.MessageResponse;
import com.cinevault.user.dto.AuthDtos.PasswordResetConfirmation;
import com.cinevault.user.dto.AuthDtos.PasswordResetRequest;
import com.cinevault.user.dto.AuthDtos.RefreshRequest;
import com.cinevault.user.dto.AuthDtos.RegisterRequest;
import com.cinevault.user.service.AccountRecoveryService;
import com.cinevault.user.service.AuthenticationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Authentication endpoints.
 *
 * <p>Thin by design: every decision lives in the services, so this class only
 * adapts HTTP to method calls and back.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Registration, login and token lifecycle")
public class AuthenticationController {

    private final AuthenticationService authenticationService;
    private final AccountRecoveryService accountRecoveryService;

    public AuthenticationController(AuthenticationService authenticationService,
                                    AccountRecoveryService accountRecoveryService) {
        this.authenticationService = authenticationService;
        this.accountRecoveryService = accountRecoveryService;
    }

    @PostMapping("/register")
    @Operation(summary = "Create an account",
            description = "Registers a user and immediately returns a token pair. "
                    + "Optional favourite genres seed cold-start recommendations.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account created"),
            @ApiResponse(responseCode = "400", description = "Validation failed",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "409", description = "Email already registered",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request,
                                                 HttpServletRequest httpRequest) {
        AuthResponse response = authenticationService.register(request,
                httpRequest.getHeader("User-Agent"), clientIp(httpRequest));
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "Sign in",
            description = "Exchanges credentials for an access token and a refresh token.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "401", description = "Invalid credentials or disabled account",
                    content = @Content(schema = @Schema(implementation = ApiError.class))),
            @ApiResponse(responseCode = "429", description = "Too many attempts",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                              HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authenticationService.login(request,
                httpRequest.getHeader("User-Agent"), clientIp(httpRequest)));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate tokens",
            description = "Exchanges a refresh token for a new pair. The presented token is "
                    + "consumed; presenting it twice revokes every session for that account.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "New token pair issued"),
            @ApiResponse(responseCode = "401", description = "Token invalid, expired or already used",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                HttpServletRequest httpRequest) {
        return ResponseEntity.ok(authenticationService.refresh(request.refreshToken(),
                httpRequest.getHeader("User-Agent"), clientIp(httpRequest)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Sign out",
            description = "Revokes the supplied refresh token. Idempotent.")
    public ResponseEntity<MessageResponse> logout(@Valid @RequestBody RefreshRequest request) {
        authenticationService.logout(request.refreshToken());
        return ResponseEntity.ok(new MessageResponse("Signed out"));
    }

    @PostMapping("/logout-all")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Sign out everywhere",
            description = "Revokes every active session for the current account.")
    public ResponseEntity<MessageResponse> logoutEverywhere(@CurrentUser JwtPrincipal principal) {
        authenticationService.logoutEverywhere(principal.userId());
        return ResponseEntity.ok(new MessageResponse("Signed out of all devices"));
    }

    @PostMapping("/password-reset/request")
    @Operation(summary = "Request a password reset",
            description = "Always returns 200, whether or not the address is registered, "
                    + "so the endpoint cannot be used to discover accounts.")
    public ResponseEntity<MessageResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        accountRecoveryService.requestPasswordReset(request.email());
        return ResponseEntity.ok(new MessageResponse(
                "If an account exists for that address, a reset link has been sent."));
    }

    @PostMapping("/password-reset/confirm")
    @Operation(summary = "Complete a password reset",
            description = "Sets a new password and revokes all existing sessions.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password updated"),
            @ApiResponse(responseCode = "401", description = "Link invalid or expired",
                    content = @Content(schema = @Schema(implementation = ApiError.class)))
    })
    public ResponseEntity<MessageResponse> confirmPasswordReset(
            @Valid @RequestBody PasswordResetConfirmation request) {
        accountRecoveryService.confirmPasswordReset(request.token(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse(
                "Password updated. Please sign in with your new password."));
    }

    @PostMapping("/verify-email/request")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Request an email verification link")
    public ResponseEntity<MessageResponse> requestEmailVerification(
            @CurrentUser JwtPrincipal principal) {
        accountRecoveryService.requestEmailVerification(principal.userId());
        return ResponseEntity.ok(new MessageResponse("Verification link sent"));
    }

    @PostMapping("/verify-email/confirm")
    @Operation(summary = "Confirm an email address")
    public ResponseEntity<MessageResponse> confirmEmailVerification(
            @Valid @RequestBody RefreshRequest request) {
        accountRecoveryService.confirmEmailVerification(request.refreshToken());
        return ResponseEntity.ok(new MessageResponse("Email address verified"));
    }

    @PostMapping("/change-password")
    @PreAuthorize("isAuthenticated()")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Change password",
            description = "Requires the current password. Revokes all sessions on success.")
    public ResponseEntity<MessageResponse> changePassword(
            @CurrentUser JwtPrincipal principal,
            @Valid @RequestBody ChangePasswordRequest request) {
        accountRecoveryService.changePassword(principal.userId(),
                request.currentPassword(), request.newPassword());
        return ResponseEntity.ok(new MessageResponse(
                "Password changed. Please sign in again."));
    }

    /** First hop of X-Forwarded-For when behind a proxy, else the socket address. */
    private static String clientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }
}
