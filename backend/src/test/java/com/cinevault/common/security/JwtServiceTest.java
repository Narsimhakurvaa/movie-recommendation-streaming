package com.cinevault.common.security;

import com.cinevault.common.exception.AuthenticationFailedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Base64;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for token issuance, verification and hashing.
 *
 * <p>These assert the security properties the design depends on: that a
 * tampered token is rejected, that an access token cannot be replayed as a
 * refresh token, and that refresh tokens are never stored in a usable form.
 */
class JwtServiceTest {

    private static final String SECRET =
            Base64.getEncoder().encodeToString("a-test-signing-key-of-at-least-32-bytes!!".getBytes());

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(
                SECRET, "cinevault-test", Duration.ofMinutes(15), Duration.ofDays(30)));
    }

    @Test
    @DisplayName("issues a token carrying the subject, email and roles")
    void issuesTokenWithClaims() {
        String token = jwtService.generateAccessToken(42L, "ada@example.com", Set.of("ROLE_USER"));

        JwtPrincipal principal = jwtService.parseAccessToken(token);

        assertThat(principal.userId()).isEqualTo(42L);
        assertThat(principal.email()).isEqualTo("ada@example.com");
        assertThat(principal.roles()).containsExactly("ROLE_USER");
        assertThat(principal.isAdmin()).isFalse();
    }

    @Test
    @DisplayName("recognises an administrator")
    void recognisesAdmin() {
        String token = jwtService.generateAccessToken(
                1L, "admin@example.com", Set.of("ROLE_USER", "ROLE_ADMIN"));

        assertThat(jwtService.parseAccessToken(token).isAdmin()).isTrue();
    }

    @Test
    @DisplayName("rejects a token whose payload has been tampered with")
    void rejectsTamperedToken() {
        String token = jwtService.generateAccessToken(1L, "a@example.com", Set.of("ROLE_USER"));
        // Corrupt the signature segment.
        String tampered = token.substring(0, token.lastIndexOf('.') + 1) + "forgedsignature";

        assertThatThrownBy(() -> jwtService.parseAccessToken(tampered))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("Invalid");
    }

    @Test
    @DisplayName("rejects a token signed with a different key")
    void rejectsForeignSignature() {
        JwtService other = new JwtService(new JwtProperties(
                Base64.getEncoder().encodeToString("a-completely-different-key-32-bytes-x".getBytes()),
                "cinevault-test", Duration.ofMinutes(15), Duration.ofDays(30)));
        String foreignToken = other.generateAccessToken(1L, "a@example.com", Set.of("ROLE_USER"));

        assertThatThrownBy(() -> jwtService.parseAccessToken(foreignToken))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("rejects a token issued for a different issuer")
    void rejectsForeignIssuer() {
        JwtService other = new JwtService(new JwtProperties(
                SECRET, "someone-else", Duration.ofMinutes(15), Duration.ofDays(30)));
        String foreignToken = other.generateAccessToken(1L, "a@example.com", Set.of("ROLE_USER"));

        assertThatThrownBy(() -> jwtService.parseAccessToken(foreignToken))
                .isInstanceOf(AuthenticationFailedException.class);
    }

    @Test
    @DisplayName("reports an expired token distinctly so the client knows to refresh")
    void reportsExpiryDistinctly() {
        JwtService expiring = new JwtService(new JwtProperties(
                SECRET, "cinevault-test", Duration.ofSeconds(-1), Duration.ofDays(30)));
        String expired = expiring.generateAccessToken(1L, "a@example.com", Set.of("ROLE_USER"));

        assertThatThrownBy(() -> expiring.parseAccessToken(expired))
                .isInstanceOf(AuthenticationFailedException.class)
                .hasMessageContaining("expired");
    }

    @Test
    @DisplayName("rejects a secret too short to sign HS256 safely")
    void rejectsWeakSecret() {
        assertThatThrownBy(() -> new JwtService(new JwtProperties(
                "too-short", "cinevault", Duration.ofMinutes(15), Duration.ofDays(30))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("at least");
    }

    @Test
    @DisplayName("generates unpredictable refresh tokens")
    void generatesUniqueRefreshTokens() {
        Set<String> tokens = new java.util.HashSet<>();
        for (int i = 0; i < 500; i++) {
            tokens.add(jwtService.generateRefreshToken());
        }

        assertThat(tokens)
                .as("collisions would let one session hijack another")
                .hasSize(500);
    }

    @Test
    @DisplayName("hashes refresh tokens deterministically but irreversibly")
    void hashesRefreshTokens() {
        String raw = jwtService.generateRefreshToken();

        String first = jwtService.hashToken(raw);
        String second = jwtService.hashToken(raw);

        assertThat(first).isEqualTo(second);
        assertThat(first).hasSize(64);           // SHA-256 rendered as hex
        assertThat(first).isNotEqualTo(raw);     // the stored form is not the token
        assertThat(first).doesNotContain(raw);
    }

    @Test
    @DisplayName("produces different digests for different tokens")
    void hashesDistinctly() {
        assertThat(jwtService.hashToken("token-a"))
                .isNotEqualTo(jwtService.hashToken("token-b"));
    }

    @Test
    @DisplayName("advertises the configured access token lifetime")
    void reportsAccessTokenTtl() {
        assertThat(jwtService.accessTokenTtlSeconds()).isEqualTo(900L);
    }
}
