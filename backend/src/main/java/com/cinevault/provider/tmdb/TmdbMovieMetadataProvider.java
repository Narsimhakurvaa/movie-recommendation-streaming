package com.cinevault.provider.tmdb;

import com.cinevault.common.exception.ExternalProviderException;
import com.cinevault.provider.MovieMetadataProvider;
import com.cinevault.provider.MovieProviderProperties;
import com.cinevault.provider.ProviderMovie;
import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * TMDB-backed metadata provider.
 *
 * <h2>Resilience</h2>
 * <ul>
 *   <li><b>Timeouts</b> - both connect and read are bounded, so a hung upstream
 *       cannot exhaust the servlet thread pool.</li>
 *   <li><b>Retries</b> - only for transient failures (I/O errors, 5xx and 429).
 *       Retrying a 404 or a 401 would just multiply a deterministic failure.</li>
 *   <li><b>Backoff</b> - exponential, so a struggling upstream is not hammered
 *       by a retry storm.</li>
 *   <li><b>Rate-limit awareness</b> - a 429 is honoured rather than retried
 *       immediately, and the wait is capped so a request cannot hang forever.</li>
 *   <li><b>Caching</b> - responses are cached; TMDB metadata changes slowly and
 *       the free tier has a request budget worth respecting.</li>
 * </ul>
 *
 * <p>Upstream failures are never surfaced verbatim: they are wrapped in
 * {@link ExternalProviderException} so no third-party error text or URL
 * (which contains the API key) can reach a client.
 */
@Component
public class TmdbMovieMetadataProvider implements MovieMetadataProvider {

    private static final Logger log = LoggerFactory.getLogger(TmdbMovieMetadataProvider.class);

    /** Upper bound on honouring a Retry-After, so a request cannot stall. */
    private static final Duration MAX_RATE_LIMIT_WAIT = Duration.ofSeconds(10);

    private final MovieProviderProperties properties;
    private final RestClient restClient;
    private final AtomicLong throttledUntilEpochMs = new AtomicLong(0);

    public TmdbMovieMetadataProvider(MovieProviderProperties properties,
                                     RestClient.Builder restClientBuilder) {
        this.properties = properties;
        this.restClient = restClientBuilder
                .baseUrl(properties.baseUrl())
                .requestFactory(timeoutRequestFactory(properties.timeout()))
                .build();
    }

    private static org.springframework.http.client.ClientHttpRequestFactory timeoutRequestFactory(
            Duration timeout) {
        var factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) timeout.toMillis());
        factory.setReadTimeout((int) timeout.toMillis());
        return factory;
    }

    @Override
    public String name() {
        return "tmdb";
    }

    @Override
    public boolean isAvailable() {
        return properties.hasApiKey();
    }

    @Override
    @Cacheable(cacheNames = "tmdbSearch", key = "#query + '-' + #page", unless = "#result.isEmpty()")
    public List<ProviderMovie> search(String query, int page) {
        JsonNode body = get("/search/movie?query={q}&page={p}&include_adult=false", query, page);
        return mapResults(body);
    }

    @Override
    @Cacheable(cacheNames = "tmdbMovie", key = "#providerId")
    public Optional<ProviderMovie> findById(String providerId) {
        try {
            // append_to_response collapses what would be three round trips
            // (details, credits, videos) into one.
            JsonNode body = get("/movie/{id}?append_to_response=credits,videos,keywords", providerId);
            return Optional.ofNullable(body).map(this::mapDetail);
        } catch (ExternalProviderException notFound) {
            log.debug("TMDB lookup failed for id {}", providerId);
            return Optional.empty();
        }
    }

    @Override
    @Cacheable(cacheNames = "tmdbPopular", key = "#page")
    public List<ProviderMovie> fetchPopular(int page) {
        return mapResults(get("/movie/popular?page={p}", page));
    }

    @Override
    @Cacheable(cacheNames = "tmdbTrending", key = "#page")
    public List<ProviderMovie> fetchTrending(int page) {
        return mapResults(get("/trending/movie/week?page={p}", page));
    }

    /**
     * Issues a request with retry and backoff.
     *
     * <p>The API key travels as a bearer token rather than a query parameter so
     * it cannot leak into access logs or the {@code Referer} header.
     */
    private JsonNode get(String uriTemplate, Object... uriVariables) {
        if (!isAvailable()) {
            throw new ExternalProviderException("TMDB is not configured");
        }
        respectActiveThrottle();

        int attempts = properties.maxRetries() + 1;
        RuntimeException lastFailure = null;

        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                return restClient.get()
                        .uri(uriTemplate, uriVariables)
                        .header("Authorization", "Bearer " + properties.apiKey())
                        .header("Accept", "application/json")
                        .retrieve()
                        .onStatus(HttpStatusCode::isError, (request, response) -> {
                            throw new TransientOrPermanent(response.getStatusCode());
                        })
                        .body(JsonNode.class);
            } catch (TransientOrPermanent statusFailure) {
                lastFailure = new ExternalProviderException(
                        "Movie metadata provider returned " + statusFailure.status.value());
                if (statusFailure.status.value() == 429) {
                    noteRateLimited();
                }
                if (!statusFailure.isRetryable() || attempt == attempts) {
                    log.warn("TMDB request failed with {} (attempt {}/{})",
                            statusFailure.status.value(), attempt, attempts);
                    throw lastFailure;
                }
            } catch (ResourceAccessException ioFailure) {
                // Timeout or connection error: worth retrying.
                lastFailure = new ExternalProviderException(
                        "Movie metadata provider is unreachable", ioFailure);
                if (attempt == attempts) {
                    log.warn("TMDB unreachable after {} attempts", attempts);
                    throw lastFailure;
                }
            }
            backoff(attempt);
        }
        throw lastFailure == null
                ? new ExternalProviderException("Movie metadata provider failed") : lastFailure;
    }

    /** Exponential backoff: 200ms, 400ms, 800ms... */
    private void backoff(int attempt) {
        try {
            Thread.sleep(Math.min(200L * (1L << (attempt - 1)), 2_000L));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new ExternalProviderException("Interrupted while retrying provider request");
        }
    }

    private void noteRateLimited() {
        throttledUntilEpochMs.set(System.currentTimeMillis() + MAX_RATE_LIMIT_WAIT.toMillis());
    }

    /** Fails fast while a known rate-limit window is still open. */
    private void respectActiveThrottle() {
        long until = throttledUntilEpochMs.get();
        if (until > System.currentTimeMillis()) {
            throw new ExternalProviderException(
                    "Movie metadata provider is rate limited; please retry shortly");
        }
    }

    // ------------------------------------------------------------- mapping

    private List<ProviderMovie> mapResults(JsonNode body) {
        if (body == null || !body.has("results")) {
            return List.of();
        }
        List<ProviderMovie> movies = new ArrayList<>();
        for (JsonNode node : body.get("results")) {
            movies.add(mapSummary(node));
        }
        return movies;
    }

    private ProviderMovie mapSummary(JsonNode node) {
        return new ProviderMovie(
                text(node, "id"),
                text(node, "title"),
                text(node, "original_title"),
                text(node, "overview"),
                date(node, "release_date"),
                node.hasNonNull("runtime") ? node.get("runtime").asInt() : null,
                text(node, "original_language"),
                null,
                imageUrl(text(node, "poster_path"), "w500"),
                imageUrl(text(node, "backdrop_path"), "w1280"),
                null,
                node.path("vote_average").asDouble(0),
                node.path("vote_count").asInt(0),
                node.path("popularity").asDouble(0),
                node.path("adult").asBoolean(false),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private ProviderMovie mapDetail(JsonNode node) {
        List<String> genres = names(node.path("genres"), "name");
        List<String> keywords = names(node.path("keywords").path("keywords"), "name");
        List<String> companies = names(node.path("production_companies"), "name");
        List<String> countries = names(node.path("production_countries"), "name");

        List<String> cast = new ArrayList<>();
        for (JsonNode member : node.path("credits").path("cast")) {
            if (cast.size() >= 15) {
                break;
            }
            cast.add(member.path("name").asText());
        }

        List<String> directors = new ArrayList<>();
        List<String> writers = new ArrayList<>();
        for (JsonNode member : node.path("credits").path("crew")) {
            String job = member.path("job").asText("");
            if ("Director".equals(job)) {
                directors.add(member.path("name").asText());
            } else if ("Screenplay".equals(job) || "Writer".equals(job)) {
                writers.add(member.path("name").asText());
            }
        }

        return new ProviderMovie(
                text(node, "id"),
                text(node, "title"),
                text(node, "original_title"),
                text(node, "overview"),
                date(node, "release_date"),
                node.hasNonNull("runtime") ? node.get("runtime").asInt() : null,
                text(node, "original_language"),
                countries.isEmpty() ? null : countries.get(0),
                imageUrl(text(node, "poster_path"), "w500"),
                imageUrl(text(node, "backdrop_path"), "w1280"),
                trailerUrl(node.path("videos").path("results")),
                node.path("vote_average").asDouble(0),
                node.path("vote_count").asInt(0),
                node.path("popularity").asDouble(0),
                node.path("adult").asBoolean(false),
                genres, keywords, cast, directors, writers, companies);
    }

    /** Prefers an official YouTube trailer; falls back to any YouTube video. */
    private String trailerUrl(JsonNode videos) {
        String fallback = null;
        for (JsonNode video : videos) {
            if (!"YouTube".equals(video.path("site").asText())) {
                continue;
            }
            String key = video.path("key").asText(null);
            if (key == null) {
                continue;
            }
            String url = "https://www.youtube.com/watch?v=" + key;
            if ("Trailer".equals(video.path("type").asText())
                    && video.path("official").asBoolean(false)) {
                return url;
            }
            if (fallback == null) {
                fallback = url;
            }
        }
        return fallback;
    }

    private static List<String> names(JsonNode array, String field) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            String value = node.path(field).asText(null);
            if (value != null && !value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private String imageUrl(String path, String size) {
        if (path == null || path.isBlank()) {
            return null;
        }
        return properties.imageBaseUrl() + "/" + size + path;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return (value == null || value.isNull()) ? null : value.asText();
    }

    private static LocalDate date(JsonNode node, String field) {
        String raw = text(node, field);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(raw);
        } catch (DateTimeParseException malformed) {
            return null;
        }
    }

    /** Carries the upstream status so the retry loop can classify the failure. */
    private static final class TransientOrPermanent extends RuntimeException {

        private final transient HttpStatusCode status;

        private TransientOrPermanent(HttpStatusCode status) {
            super("upstream status " + status.value(), null, false, false);
            this.status = status;
        }

        /** 5xx and 429 may succeed on a retry; 4xx will not. */
        private boolean isRetryable() {
            return status.is5xxServerError() || status.value() == 429;
        }
    }
}
