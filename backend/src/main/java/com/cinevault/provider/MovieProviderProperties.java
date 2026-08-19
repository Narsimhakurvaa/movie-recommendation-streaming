package com.cinevault.provider;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Configuration for external metadata providers.
 *
 * <p>{@code apiKey} is intentionally allowed to be blank: the application must
 * start and function without one, falling back to the seeded local catalogue.
 *
 * @param type        provider to use: {@code local} or {@code tmdb}
 * @param apiKey      credential, supplied via environment variable
 * @param baseUrl     API root
 * @param imageBaseUrl CDN root for poster and backdrop paths
 * @param timeout     per-request timeout
 * @param maxRetries  attempts for transient upstream failures
 */
@ConfigurationProperties(prefix = "cinevault.provider")
public record MovieProviderProperties(
        String type,
        String apiKey,
        String baseUrl,
        String imageBaseUrl,
        Duration timeout,
        Integer maxRetries) {

    public MovieProviderProperties {
        type = (type == null || type.isBlank()) ? "local" : type.trim().toLowerCase();
        baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://api.themoviedb.org/3" : baseUrl;
        imageBaseUrl = (imageBaseUrl == null || imageBaseUrl.isBlank())
                ? "https://image.tmdb.org/t/p" : imageBaseUrl;
        timeout = timeout == null ? Duration.ofSeconds(5) : timeout;
        maxRetries = maxRetries == null ? 2 : maxRetries;
    }

    public boolean hasApiKey() {
        return apiKey != null && !apiKey.isBlank();
    }
}
