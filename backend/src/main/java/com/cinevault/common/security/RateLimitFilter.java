package com.cinevault.common.security;

import com.cinevault.common.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token-bucket rate limiting on authentication endpoints.
 *
 * <h2>Why only auth endpoints</h2>
 * <p>Login and registration are the endpoints where abuse is cheap and
 * damaging: credential stuffing, password spraying and account enumeration all
 * depend on issuing many attempts quickly. Browsing endpoints are naturally
 * bounded by pagination and are far more sensitive to false positives, so they
 * are left unthrottled here and would be better handled at the edge.
 *
 * <h2>Storage</h2>
 * <p>Buckets live in an in-memory map, which is correct for a single instance.
 * Behind a load balancer this becomes per-instance limiting; the honest fix is
 * a distributed bucket backend, and the map is deliberately isolated behind
 * {@link #resolveBucket} so that swap is a one-method change.
 */
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    /** Endpoints worth protecting, matched by prefix. */
    private static final String[] PROTECTED_PREFIXES = {
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/refresh",
            "/api/auth/password-reset"
    };

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final int capacity;
    private final Duration refillPeriod;
    private final boolean enabled;

    public RateLimitFilter(ObjectMapper objectMapper,
                           @Value("${cinevault.rate-limit.enabled:true}") boolean enabled,
                           @Value("${cinevault.rate-limit.capacity:20}") int capacity,
                           @Value("${cinevault.rate-limit.refill-minutes:1}") int refillMinutes) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.capacity = capacity;
        this.refillPeriod = Duration.ofMinutes(refillMinutes);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        Bucket bucket = resolveBucket(clientKey(request));
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            response.addHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));
            filterChain.doFilter(request, response);
            return;
        }

        long retryAfterSeconds = Math.max(1, probe.getNanosToWaitForRefill() / 1_000_000_000);
        // Logged without the credential body, and at WARN because sustained
        // throttling on auth endpoints is worth noticing.
        log.warn("Rate limit exceeded for {} on {}", clientKey(request), request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.addHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        ApiError body = ApiError.of(429, "Too Many Requests", "RATE_LIMIT_EXCEEDED",
                "Too many requests. Please retry in %d seconds.".formatted(retryAfterSeconds),
                request.getRequestURI(), UUID.randomUUID().toString().substring(0, 8));
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, ignored -> Bucket.builder()
                .addLimit(Bandwidth.builder()
                        .capacity(capacity)
                        .refillGreedy(capacity, refillPeriod)
                        .build())
                .build());
    }

    /**
     * Identifies the caller.
     *
     * <p>Honours {@code X-Forwarded-For} because the application runs behind a
     * reverse proxy, where the socket address would otherwise be the proxy's
     * and every client would share one bucket. Only the first hop is used, as
     * later entries are attacker-controlled.
     */
    private String clientKey(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            int comma = forwarded.indexOf(',');
            return (comma > 0 ? forwarded.substring(0, comma) : forwarded).trim();
        }
        return request.getRemoteAddr();
    }

    /** Only the authentication endpoints are throttled. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!enabled) {
            return true;
        }
        String path = request.getRequestURI();
        for (String prefix : PROTECTED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return false;
            }
        }
        return true;
    }
}
