package com.cinevault.user.service;

import com.cinevault.common.exception.AuthenticationFailedException;
import com.cinevault.common.exception.BadRequestException;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.common.security.JwtService;
import com.cinevault.user.domain.AccountToken;
import com.cinevault.user.domain.AccountTokenType;
import com.cinevault.user.domain.User;
import com.cinevault.user.repository.AccountTokenRepository;
import com.cinevault.user.repository.RefreshTokenRepository;
import com.cinevault.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/**
 * Password reset and email verification.
 *
 * <h2>Delivery</h2>
 * <p>This service owns token lifecycle only; it hands the generated token to a
 * {@link NotificationSender}. The default implementation logs the link rather
 * than sending mail, so the whole flow is exercisable locally without an SMTP
 * dependency, and swapping in a real provider is a single bean replacement.
 *
 * <h2>Enumeration resistance</h2>
 * <p>{@link #requestPasswordReset} always reports success, whether or not the
 * address exists. An endpoint that answered honestly would be a free
 * account-existence oracle.
 */
@Service
public class AccountRecoveryService {

    private static final Logger log = LoggerFactory.getLogger(AccountRecoveryService.class);

    /** Short enough to limit exposure, long enough to survive a slow inbox. */
    private static final Duration PASSWORD_RESET_TTL = Duration.ofMinutes(30);
    private static final Duration EMAIL_VERIFICATION_TTL = Duration.ofDays(3);

    private final UserRepository userRepository;
    private final AccountTokenRepository accountTokenRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final NotificationSender notificationSender;

    public AccountRecoveryService(UserRepository userRepository,
                                  AccountTokenRepository accountTokenRepository,
                                  RefreshTokenRepository refreshTokenRepository,
                                  PasswordEncoder passwordEncoder,
                                  JwtService jwtService,
                                  NotificationSender notificationSender) {
        this.userRepository = userRepository;
        this.accountTokenRepository = accountTokenRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.notificationSender = notificationSender;
    }

    /**
     * Starts a password reset. Always succeeds from the caller's perspective.
     */
    @Transactional
    public void requestPasswordReset(String email) {
        userRepository.findByEmailIgnoreCase(email).ifPresentOrElse(user -> {
            // Invalidate outstanding tokens so only the newest link works.
            accountTokenRepository.consumeOutstanding(
                    user.getId(), AccountTokenType.PASSWORD_RESET, Instant.now());

            String rawToken = jwtService.generateRefreshToken();
            accountTokenRepository.save(new AccountToken(user, jwtService.hashToken(rawToken),
                    AccountTokenType.PASSWORD_RESET, Instant.now().plus(PASSWORD_RESET_TTL)));

            notificationSender.sendPasswordReset(user.getEmail(), rawToken);
            log.info("Issued password reset token for account id={}", user.getId());
        }, () -> log.debug("Password reset requested for an address with no account"));
    }

    /**
     * Completes a password reset.
     *
     * <p>All existing sessions are revoked on success: if the reset was prompted
     * by a compromise, leaving the attacker's refresh token alive would defeat
     * the entire exercise.
     */
    @Transactional
    public void confirmPasswordReset(String rawToken, String newPassword) {
        AccountToken token = accountTokenRepository
                .findUsable(jwtService.hashToken(rawToken), AccountTokenType.PASSWORD_RESET)
                .orElseThrow(() -> new AuthenticationFailedException(
                        "This reset link is invalid or has expired"));

        if (!token.isUsable()) {
            throw new AuthenticationFailedException("This reset link is invalid or has expired");
        }

        User user = token.getUser();
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        token.consume();
        accountTokenRepository.save(token);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());

        log.info("Password reset completed for account id={}; all sessions revoked", user.getId());
    }

    /** Issues an email-verification token for a signed-in user. */
    @Transactional
    public void requestEmailVerification(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));
        if (user.isEmailVerified()) {
            throw new BadRequestException("This email address is already verified");
        }

        accountTokenRepository.consumeOutstanding(
                user.getId(), AccountTokenType.EMAIL_VERIFICATION, Instant.now());

        String rawToken = jwtService.generateRefreshToken();
        accountTokenRepository.save(new AccountToken(user, jwtService.hashToken(rawToken),
                AccountTokenType.EMAIL_VERIFICATION, Instant.now().plus(EMAIL_VERIFICATION_TTL)));

        notificationSender.sendEmailVerification(user.getEmail(), rawToken);
        log.info("Issued email verification token for account id={}", user.getId());
    }

    @Transactional
    public void confirmEmailVerification(String rawToken) {
        AccountToken token = accountTokenRepository
                .findUsable(jwtService.hashToken(rawToken), AccountTokenType.EMAIL_VERIFICATION)
                .orElseThrow(() -> new AuthenticationFailedException(
                        "This verification link is invalid or has expired"));

        if (!token.isUsable()) {
            throw new AuthenticationFailedException(
                    "This verification link is invalid or has expired");
        }

        User user = token.getUser();
        user.setEmailVerified(true);
        userRepository.save(user);

        token.consume();
        accountTokenRepository.save(token);
        log.info("Verified email for account id={}", user.getId());
    }

    /**
     * Changes the password for a signed-in user.
     *
     * <p>Requires the current password even though the caller is authenticated,
     * so that a hijacked access token alone cannot lock the owner out.
     */
    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthenticationFailedException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new BadRequestException("New password must differ from the current password");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        refreshTokenRepository.revokeAllForUser(user.getId(), Instant.now());
        log.info("Password changed for account id={}; all sessions revoked", user.getId());
    }
}
