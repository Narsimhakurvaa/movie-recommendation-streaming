package com.cinevault.user.service;

import com.cinevault.catalogue.repository.GenreRepository;
import com.cinevault.common.exception.AuthenticationFailedException;
import com.cinevault.common.exception.DuplicateResourceException;
import com.cinevault.common.security.JwtService;
import com.cinevault.interaction.domain.UserFavouriteGenre;
import com.cinevault.interaction.domain.UserPreferences;
import com.cinevault.interaction.repository.UserFavouriteGenreRepository;
import com.cinevault.interaction.repository.UserPreferencesRepository;
import com.cinevault.user.domain.RefreshToken;
import com.cinevault.user.domain.Role;
import com.cinevault.user.domain.RoleName;
import com.cinevault.user.domain.User;
import com.cinevault.user.dto.AuthDtos.AuthResponse;
import com.cinevault.user.dto.AuthDtos.AuthenticatedUser;
import com.cinevault.user.dto.AuthDtos.LoginRequest;
import com.cinevault.user.dto.AuthDtos.RegisterRequest;
import com.cinevault.user.repository.RefreshTokenRepository;
import com.cinevault.user.repository.RoleRepository;
import com.cinevault.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registration, login, token refresh and logout.
 *
 * <h2>Refresh-token rotation</h2>
 * <p>Every refresh consumes the presented token and issues a new one. The old
 * row is revoked and records which token replaced it, forming a chain.
 *
 * <p>If a token that has <em>already been used</em> is presented again, that is
 * strong evidence it was stolen: the legitimate client would have moved on to
 * its successor. The service responds by revoking the entire token family for
 * that user, forcing both the attacker and the victim to re-authenticate. This
 * is the standard defence recommended by OAuth 2.0 Security Best Current
 * Practice, and it is why rotation is worth the extra write.
 */
@Service
public class AuthenticationService {

    private static final Logger log = LoggerFactory.getLogger(AuthenticationService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final UserPreferencesRepository preferencesRepository;
    private final UserFavouriteGenreRepository favouriteGenreRepository;
    private final GenreRepository genreRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthenticationService(UserRepository userRepository,
                                 RoleRepository roleRepository,
                                 RefreshTokenRepository refreshTokenRepository,
                                 UserPreferencesRepository preferencesRepository,
                                 UserFavouriteGenreRepository favouriteGenreRepository,
                                 GenreRepository genreRepository,
                                 PasswordEncoder passwordEncoder,
                                 JwtService jwtService) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.preferencesRepository = preferencesRepository;
        this.favouriteGenreRepository = favouriteGenreRepository;
        this.genreRepository = genreRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Creates an account and signs the user straight in.
     *
     * <p>Onboarding genres are captured at this point because they are the only
     * signal available to the cold-start strategy before any viewing happens.
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, String userAgent, String clientIp) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            // Registration necessarily reveals whether an email is taken; that
            // is unavoidable. Login and password reset do not.
            throw new DuplicateResourceException("An account with this email already exists");
        }

        Role userRole = roleRepository.findByName(RoleName.ROLE_USER.name())
                .orElseThrow(() -> new IllegalStateException(
                        "ROLE_USER is missing; database migrations have not been applied"));

        User user = new User(request.email(),
                passwordEncoder.encode(request.password()),
                request.displayName().trim());
        user.addRole(userRole);
        user.setOnboardingCompleted(
                request.favouriteGenreSlugs() != null && !request.favouriteGenreSlugs().isEmpty());
        User saved = userRepository.save(user);

        preferencesRepository.save(new UserPreferences(saved));
        persistFavouriteGenres(saved, request.favouriteGenreSlugs());

        log.info("Registered new account id={}", saved.getId());
        return issueTokens(saved, userAgent, clientIp);
    }

    /**
     * Verifies credentials and issues a token pair.
     *
     * <p>Every failure path returns the same message and takes a similar amount
     * of time, so the endpoint cannot be used to discover which emails exist.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, String userAgent, String clientIp) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElse(null);

        if (user == null) {
            // Hash anyway so a missing account is not detectably faster than a
            // wrong password. Without this the response time leaks account
            // existence regardless of the identical message.
            passwordEncoder.matches(request.password(),
                    "$2a$12$0000000000000000000000000000000000000000000000000000u");
            throw new AuthenticationFailedException("Invalid email or password");
        }

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            log.info("Failed login attempt for account id={}", user.getId());
            throw new AuthenticationFailedException("Invalid email or password");
        }

        if (!user.isEnabled()) {
            throw new AuthenticationFailedException("This account has been disabled");
        }

        userRepository.recordLogin(user.getId(), Instant.now());
        log.info("Successful login for account id={}", user.getId());
        return issueTokens(user, userAgent, clientIp);
    }

    /**
     * Exchanges a refresh token for a fresh pair, rotating the old one.
     *
     * @throws AuthenticationFailedException if the token is unknown, expired or
     *                                       already used
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken, String userAgent, String clientIp) {
        String hash = jwtService.hashToken(rawRefreshToken);
        RefreshToken stored = refreshTokenRepository.findByTokenHash(hash)
                .orElseThrow(() -> new AuthenticationFailedException("Invalid refresh token"));

        if (stored.isRevoked()) {
            // Reuse of a rotated token: assume compromise and kill the family.
            log.warn("Refresh token reuse detected for account id={}; revoking all sessions",
                    stored.getUser().getId());
            refreshTokenRepository.revokeAllForUser(stored.getUser().getId(), Instant.now());
            throw new AuthenticationFailedException(
                    "Refresh token has already been used. Please sign in again.");
        }

        if (stored.isExpired()) {
            throw new AuthenticationFailedException("Refresh token has expired");
        }

        User user = stored.getUser();
        if (!user.isEnabled()) {
            throw new AuthenticationFailedException("This account has been disabled");
        }

        AuthResponse response = issueTokens(user, userAgent, clientIp);
        stored.revoke();
        stored.setReplacedBy(jwtService.hashToken(response.refreshToken()));
        refreshTokenRepository.save(stored);
        return response;
    }

    /**
     * Revokes a single session.
     *
     * <p>Silently succeeds for an unknown token: logout must be idempotent, and
     * reporting "no such token" would leak which tokens are valid.
     */
    @Transactional
    public void logout(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        refreshTokenRepository.findByTokenHash(jwtService.hashToken(rawRefreshToken))
                .ifPresent(token -> {
                    token.revoke();
                    refreshTokenRepository.save(token);
                    log.info("Revoked session for account id={}", token.getUser().getId());
                });
    }

    /** Signs the user out of every device. */
    @Transactional
    public void logoutEverywhere(Long userId) {
        int revoked = refreshTokenRepository.revokeAllForUser(userId, Instant.now());
        log.info("Revoked {} session(s) for account id={}", revoked, userId);
    }

    private AuthResponse issueTokens(User user, String userAgent, String clientIp) {
        Set<String> roles = user.getRoles().stream()
                .map(Role::getName)
                .collect(Collectors.toUnmodifiableSet());

        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail(), roles);
        String rawRefreshToken = jwtService.generateRefreshToken();

        RefreshToken refreshToken = new RefreshToken(user,
                jwtService.hashToken(rawRefreshToken), jwtService.refreshTokenExpiry());
        refreshToken.setUserAgent(truncate(userAgent, 255));
        refreshToken.setClientIp(truncate(clientIp, 64));
        refreshTokenRepository.save(refreshToken);

        return AuthResponse.of(accessToken, rawRefreshToken,
                jwtService.accessTokenTtlSeconds(), toAuthenticatedUser(user, roles));
    }

    private void persistFavouriteGenres(User user, List<String> slugs) {
        if (slugs == null || slugs.isEmpty()) {
            return;
        }
        // Earlier picks weigh more; floor at 0.4 so later choices still count.
        int index = 0;
        for (String slug : slugs.stream().distinct().limit(8).toList()) {
            var genre = genreRepository.findBySlug(slug);
            if (genre.isEmpty()) {
                log.debug("Ignoring unknown genre slug during onboarding: {}", slug);
                continue;
            }
            BigDecimal weight = BigDecimal.valueOf(Math.max(0.4, 1.0 - (index * 0.1)))
                    .setScale(3, java.math.RoundingMode.HALF_UP);
            favouriteGenreRepository.save(new UserFavouriteGenre(user, genre.get(), weight));
            index++;
        }
    }

    static AuthenticatedUser toAuthenticatedUser(User user, Set<String> roles) {
        return new AuthenticatedUser(user.getId(), user.getEmail(), user.getDisplayName(),
                user.getAvatarUrl(), roles, user.isEmailVerified(), user.isOnboardingCompleted());
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
