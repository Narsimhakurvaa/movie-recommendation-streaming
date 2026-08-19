package com.cinevault.common.security;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/**
 * JWT settings, bound from configuration and validated at startup.
 *
 * <p>There is deliberately no default for {@code secret}: the application must
 * fail to start rather than silently run on a hardcoded key. In development the
 * value comes from {@code .env}; in production from the environment or a secret
 * manager.
 *
 * @param secret          Base64-encoded signing key, at least 256 bits for HS256
 * @param issuer          value placed in the {@code iss} claim
 * @param accessTokenTtl  lifetime of an access token; short by design
 * @param refreshTokenTtl lifetime of a refresh token
 */
@Validated
@ConfigurationProperties(prefix = "cinevault.jwt")
public record JwtProperties(
        @NotBlank(message = "JWT_SECRET must be provided") String secret,
        @NotBlank String issuer,
        @NotNull Duration accessTokenTtl,
        @NotNull Duration refreshTokenTtl) {

    /**
     * Minimum key length for HS256. RFC 7518 requires a key at least as long as
     * the hash output; anything shorter weakens the signature.
     */
    public static final int MINIMUM_SECRET_BYTES = 32;
}
