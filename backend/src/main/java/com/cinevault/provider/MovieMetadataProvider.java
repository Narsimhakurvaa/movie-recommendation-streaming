package com.cinevault.provider;

import java.util.List;
import java.util.Optional;

/**
 * Source of film metadata.
 *
 * <p>The application depends on this interface, never on TMDB directly. That
 * keeps the catalogue, recommendation and admin modules free of any knowledge
 * of the upstream API, and means a different provider - or none at all - is a
 * configuration change rather than a refactor.
 *
 * <p>Two implementations ship: a TMDB client, and a local provider backed by
 * the seeded catalogue so the product is fully usable with no API key.
 */
public interface MovieMetadataProvider {

    /** Identifies this provider in logs, health output and admin responses. */
    String name();

    /**
     * Whether the provider is usable right now.
     *
     * <p>A provider missing its credentials reports {@code false} rather than
     * failing at call time, which lets the application choose a fallback during
     * start-up instead of erroring on the first user request.
     */
    boolean isAvailable();

    /**
     * Finds films matching a free-text query.
     *
     * @param query search term
     * @param page  one-based page number, following the upstream convention
     * @throws com.cinevault.common.exception.ExternalProviderException on failure
     */
    List<ProviderMovie> search(String query, int page);

    /** Fetches one film by its provider-native identifier. */
    Optional<ProviderMovie> findById(String providerId);

    /** Films currently popular upstream, used to seed and refresh the catalogue. */
    List<ProviderMovie> fetchPopular(int page);

    /** Films trending upstream over the last week. */
    List<ProviderMovie> fetchTrending(int page);
}
