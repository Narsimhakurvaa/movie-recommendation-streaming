package com.cinevault.user.service;

import com.cinevault.catalogue.repository.GenreRepository;
import com.cinevault.common.exception.AuthenticationFailedException;
import com.cinevault.common.exception.DuplicateResourceException;
import com.cinevault.common.security.JwtService;
import com.cinevault.interaction.repository.UserFavouriteGenreRepository;
import com.cinevault.interaction.repository.UserPreferencesRepository;
import com.cinevault.user.domain.RefreshToken;
import com.cinevault.user.domain.Role;
import com.cinevault.user.domain.User;
import com.cinevault.user.dto.AuthDtos.LoginRequest;
import com.cinevault.user.dto.AuthDtos.RegisterRequest;
import com.cinevault.user.repository.RefreshTokenRepository;
import com.cinevault.user.repository.RoleRepository;
import com.cinevault.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for registration, login and refresh-token rotation.
 *
 * <p>The rotation tests are the important ones: they pin down the theft
 * response that makes stolen refresh tokens survivable.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthenticationServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private RoleRepository roleRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private UserPreferencesRepository preferencesRepository;
    @Mock private UserFavouriteGenreRepository favouriteGenreRepository;
    @Mock private GenreRepository genreRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtService jwtService;

    @InjectMocks private AuthenticationService authenticationService;

    private User existingUser;

    @BeforeEach
    void setUp() throws Exception {
        existingUser = new User("ada@example.com", "$2a$12$storedhash", "Ada");
        setId(existingUser, 1L);
        existingUser.addRole(new Role("ROLE_USER", "Standard viewer"));

        when(jwtService.generateAccessToken(anyLong(), anyString(), any())).thenReturn("access-token");
        when(jwtService.generateRefreshToken()).thenReturn("raw-refresh-token");
        when(jwtService.hashToken(anyString())).thenAnswer(i -> "hashed-" + i.getArgument(0));
        when(jwtService.refreshTokenExpiry()).thenReturn(Instant.now().plusSeconds(3600));
        when(jwtService.accessTokenTtlSeconds()).thenReturn(900L);
    }

    @Test
    @DisplayName("refuses to register an email that is already taken")
    void rejectsDuplicateEmail() {
        when(userRepository.existsByEmailIgnoreCase("ada@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.register(
                new RegisterRequest("ada@example.com", "Str0ngPassphrase!", "Ada", null), null, null))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("stores only a hashed password, never the plaintext")
    void hashesPasswordOnRegistration() {
        when(userRepository.existsByEmailIgnoreCase(anyString())).thenReturn(false);
        when(roleRepository.findByName("ROLE_USER"))
                .thenReturn(Optional.of(new Role("ROLE_USER", "Standard viewer")));
        when(passwordEncoder.encode("Str0ngPassphrase!")).thenReturn("$2a$12$bcrypthash");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User saved = invocation.getArgument(0);
            setId(saved, 2L);
            return saved;
        });

        authenticationService.register(
                new RegisterRequest("new@example.com", "Str0ngPassphrase!", "New", null), null, null);

        verify(passwordEncoder).encode("Str0ngPassphrase!");
        verify(userRepository).save(org.mockito.ArgumentMatchers.argThat(
                user -> user.getPasswordHash().equals("$2a$12$bcrypthash")));
    }

    @Test
    @DisplayName("gives the same answer for a wrong password and an unknown account")
    void doesNotRevealWhetherAccountExists() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

        String unknownAccountMessage = catchMessage(() -> authenticationService.login(
                new LoginRequest("ghost@example.com", "whatever"), null, null));
        String wrongPasswordMessage = catchMessage(() -> authenticationService.login(
                new LoginRequest("ada@example.com", "wrong"), null, null));

        assertThat(unknownAccountMessage)
                .as("differing messages would let an attacker enumerate accounts")
                .isEqualTo(wrongPasswordMessage)
                .isEqualTo("Invalid email or password");
    }

    @Test
    @DisplayName("still performs a hash for an unknown account, so timing does not leak")
    void performsConstantWorkForUnknownAccount() {
        when(userRepository.findByEmailIgnoreCase("ghost@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("ghost@example.com", "whatever"), null, null))
                .isInstanceOf(AuthenticationFailedException.class);

        // A dummy comparison keeps the response time comparable to a real one.
        verify(passwordEncoder).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("refuses to sign in a disabled account")
    void rejectsDisabledAccount() {
        existingUser.setEnabled(false);
        when(userRepository.findByEmailIgnoreCase("ada@example.com")).thenReturn(Optional.of(existingUser));
        when(passwordEncoder.matches(anyString(), anyString())).thenReturn(true);

        assertThatThrownBy(() -> authenticationService.login(
                new LoginRequest("ada@example.com", "correct"), null, null))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("disabled");
    }

    @Test
    @DisplayName("rotates the refresh token, revoking the one presented")
    void rotatesRefreshToken() {
        RefreshToken stored = new RefreshToken(existingUser, "hashed-old", Instant.now().plusSeconds(3600));
        when(refreshTokenRepository.findByTokenHash("hashed-old-token")).thenReturn(Optional.of(stored));

        authenticationService.refresh("old-token", null, null);

        assertThat(stored.isRevoked())
                .as("the presented token must not remain usable")
                .isTrue();
        assertThat(stored.getReplacedBy()).isNotNull();
    }

    @Test
    @DisplayName("revokes every session when a used refresh token is presented again")
    void detectsTokenReuse() {
        RefreshToken alreadyUsed =
                new RefreshToken(existingUser, "hashed-old", Instant.now().plusSeconds(3600));
        alreadyUsed.revoke();
        when(refreshTokenRepository.findByTokenHash("hashed-stolen")).thenReturn(Optional.of(alreadyUsed));

        assertThatThrownBy(() -> authenticationService.refresh("stolen", null, null))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("already been used");

        // Reuse implies theft: the whole family is invalidated.
        verify(refreshTokenRepository).revokeAllForUser(org.mockito.ArgumentMatchers.eq(1L),
                any(Instant.class));
    }

    @Test
    @DisplayName("rejects an expired refresh token")
    void rejectsExpiredRefreshToken() {
        RefreshToken expired =
                new RefreshToken(existingUser, "hashed-old", Instant.now().minusSeconds(60));
        when(refreshTokenRepository.findByTokenHash("hashed-expired")).thenReturn(Optional.of(expired));

        assertThatThrownBy(() -> authenticationService.refresh("expired", null, null))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("rejects a refresh token that was never issued")
    void rejectsUnknownRefreshToken() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authenticationService.refresh("made-up", null, null))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("treats logout as idempotent for an unknown token")
    void logoutIsIdempotent() {
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.empty());

        authenticationService.logout("already-gone");

        verify(refreshTokenRepository, never()).save(any());
    }

    @Test
    @DisplayName("ignores a blank token on logout without touching the database")
    void logoutIgnoresBlankToken() {
        authenticationService.logout("  ");

        verify(refreshTokenRepository, never()).findByTokenHash(anyString());
    }

    private static String catchMessage(Runnable action) {
        try {
            action.run();
            throw new AssertionError("expected an authentication failure");
        } catch (AuthenticationFailedException expected) {
            return expected.getMessage();
        }
    }

    private static void setId(Object entity, Long id) {
        Class<?> type = entity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("id");
                field.setAccessible(true);
                field.set(entity, id);
                return;
            } catch (NoSuchFieldException continueUp) {
                type = type.getSuperclass();
            } catch (IllegalAccessException impossible) {
                throw new IllegalStateException(impossible);
            }
        }
        throw new IllegalStateException("no id field on " + entity.getClass());
    }
}
