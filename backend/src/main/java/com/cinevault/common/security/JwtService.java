package com.cinevault.common.security;

import com.cinevault.common.exception.AuthenticationFailedException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * Issues and validates JSON Web Tokens, and hashes refresh tokens.
 *
 * <h2>Token design</h2>
 * <p>Access tokens are short-lived (15 minutes by default) and carry the
 * subject, roles and a token-type claim. They are self-contained, so verifying
 * one costs no database round trip.
 *
 * <p>Refresh tokens are <em>opaque random strings</em>, not JWTs. A JWT refresh
 * token cannot be revoked before it expires without a server-side blacklist,
 * which defeats the point of statelessness. An opaque token is looked up by
 * digest, so revocation is immediate and a stolen token can be invalidated.
 *
 * <p>The {@code typ} claim prevents an access token from being replayed at the
 * refresh endpoint, and vice versa.
 */
@Service
public class JwtService {

    private static final Logger log = LoggerFactory.getLogger(JwtService.class);

    private static final String CLAIM_ROLES = "roles";
    private static final String CLAIM_TOKEN_TYPE = "typ";
    private static final String TYPE_ACCESS = "access";

    /** 32 bytes of entropy: infeasible to guess, comfortable in a JSON body. */
    private static final int REFRESH_TOKEN_BYTES = 32;

    private final JwtProperties properties;
    private final SecretKey signingKey;
    private final SecureRandom secureRandom = new SecureRandom();

    public JwtService(JwtProperties properties) {
        this.properties = properties;
        this.signingKey = buildSigningKey(properties.secret());
    }

    /**
     * Decodes the configured secret and rejects anything too weak to sign with.
     *
     * <p>Accepts Base64 but falls back to raw UTF-8 so a developer who exports
     * a plain passphrase gets a clear error about length rather than a confusing
     * Base64 decoding failure.
     */
    private static SecretKey buildSigningKey(String configuredSecret) {
        byte[] keyBytes;
        try {
            keyBytes = Base64.getDecoder().decode(configuredSecret);
        } catch (IllegalArgumentException notBase64) {
            keyBytes = configuredSecret.getBytes(StandardCharsets.UTF_8);
        }
        if (keyBytes.length < JwtProperties.MINIMUM_SECRET_BYTES) {
            throw new IllegalStateException(
                    "JWT secret must be at least %d bytes (%d bits) for HS256; got %d bytes. "
                            .formatted(JwtProperties.MINIMUM_SECRET_BYTES,
                                    JwtProperties.MINIMUM_SECRET_BYTES * 8, keyBytes.length)
                            + "Generate one with: openssl rand -base64 48");
        }
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Mints a signed access token.
     *
     * @param userId the subject
     * @param email  convenience claim so the UI need not decode a second source
     * @param roles  granted authorities, e.g. {@code ROLE_USER}
     */
    public String generateAccessToken(Long userId, String email, Set<String> roles) {
        Instant now = Instant.now();
        Instant expiry = now.plus(properties.accessTokenTtl());
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("email", email)
                .claim(CLAIM_ROLES, List.copyOf(roles))
                .claim(CLAIM_TOKEN_TYPE, TYPE_ACCESS)
                .issuer(properties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and verifies an access token.
     *
     * @throws AuthenticationFailedException if the token is expired, tampered
     *                                       with, or not an access token
     */
    public JwtPrincipal parseAccessToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            if (!TYPE_ACCESS.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                throw new AuthenticationFailedException("Token is not an access token");
            }

            @SuppressWarnings("unchecked")
            List<String> roles = claims.get(CLAIM_ROLES, List.class);
            return new JwtPrincipal(
                    Long.valueOf(claims.getSubject()),
                    claims.get("email", String.class),
                    roles == null ? Set.of() : Set.copyOf(roles));
        } catch (ExpiredJwtException expired) {
            // Distinguished so the client knows to refresh rather than re-login.
            throw new AuthenticationFailedException("Access token has expired");
        } catch (JwtException | IllegalArgumentException invalid) {
            // Never log the token itself: it is a bearer credential.
            log.debug("Rejected malformed or unverifiable JWT: {}", invalid.getMessage());
            throw new AuthenticationFailedException("Invalid access token");
        }
    }

    /**
     * Generates an opaque refresh token.
     *
     * @return the raw value; the caller must persist only {@link #hashToken}
     */
    public String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * SHA-256 digest used as the stored form of refresh and account tokens.
     *
     * <p>A plain hash (not BCrypt) is correct here: the input is 256 bits of
     * cryptographic randomness, so it is not brute-forceable and a slow KDF
     * would only add latency to every refresh.
     */
    public String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashed);
        } catch (NoSuchAlgorithmException impossible) {
            // SHA-256 is mandated by the JRE specification.
            throw new IllegalStateException("SHA-256 is unavailable", impossible);
        }
    }

    public Instant refreshTokenExpiry() {
        return Instant.now().plus(properties.refreshTokenTtl());
    }

    /** Seconds until an access token expires, advertised to the client. */
    public long accessTokenTtlSeconds() {
        return properties.accessTokenTtl().toSeconds();
    }

    public java.time.Duration refreshTokenTtl() {
        return properties.refreshTokenTtl();
    }
}
