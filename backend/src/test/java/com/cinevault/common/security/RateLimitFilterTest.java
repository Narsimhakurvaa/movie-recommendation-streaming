package com.cinevault.common.security;

import com.cinevault.common.dto.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests for the Bucket4j-backed rate limiter.
 *
 * <p>These exist for two reasons. First, throttling the authentication
 * endpoints is a real security control - it is what makes credential stuffing
 * and password spraying expensive - so its behaviour deserves to be pinned
 * down rather than assumed.
 *
 * <p>Second, they exercise the Bucket4j API surface the filter depends on
 * ({@code Bandwidth.builder().capacity().refillGreedy()},
 * {@code tryConsumeAndReturnRemaining} and the {@code ConsumptionProbe}
 * accessors). If a future Bucket4j upgrade changes or removes any of it, these
 * tests fail at compile or run time instead of the limiter silently degrading.
 */
class RateLimitFilterTest {

    private static final int CAPACITY = 5;

    private ObjectMapper objectMapper;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        // ApiError carries an Instant, so JSR-310 support is required to read
        // the body back. Spring Boot registers this automatically at runtime.
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        chain = mock(FilterChain.class);
    }

    private RateLimitFilter filter(boolean enabled, int capacity) {
        return new RateLimitFilter(objectMapper, enabled, capacity, 1);
    }

    private static MockHttpServletRequest request(String uri, String ip) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setRequestURI(uri);
        request.setRemoteAddr(ip);
        return request;
    }

    @Nested
    @DisplayName("Which endpoints are throttled")
    class Scope {

        @ParameterizedTest(name = "throttles {0}")
        @ValueSource(strings = {
                "/api/auth/login",
                "/api/auth/register",
                "/api/auth/refresh",
                "/api/auth/password-reset/request",
        })
        @DisplayName("protects the endpoints where abuse is cheap and damaging")
        void protectsAuthEndpoints(String uri) throws Exception {
            assertThat(filter(true, CAPACITY).shouldNotFilter(request(uri, "10.0.0.1")))
                    .isFalse();
        }

        @ParameterizedTest(name = "does not throttle {0}")
        @ValueSource(strings = {
                "/api/movies",
                "/api/movies/42",
                "/api/recommendations/trending",
                "/api/genres",
                "/actuator/health",
        })
        @DisplayName("leaves browsing alone, where throttling would mostly cause false positives")
        void ignoresBrowsingEndpoints(String uri) throws Exception {
            assertThat(filter(true, CAPACITY).shouldNotFilter(request(uri, "10.0.0.1")))
                    .isTrue();
        }

        @Test
        @DisplayName("is fully disabled when the feature flag is off")
        void honoursDisableFlag() throws Exception {
            // The `test` profile switches this off so suites are not throttled.
            assertThat(filter(false, CAPACITY).shouldNotFilter(request("/api/auth/login", "10.0.0.1")))
                    .isTrue();
        }
    }

    @Nested
    @DisplayName("Consumption behaviour")
    class Consumption {

        @Test
        @DisplayName("allows requests up to the configured capacity")
        void allowsUpToCapacity() throws Exception {
            RateLimitFilter rateLimitFilter = filter(true, CAPACITY);

            for (int i = 0; i < CAPACITY; i++) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                rateLimitFilter.doFilterInternal(
                        request("/api/auth/login", "10.0.0.1"), response, chain);
                assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
            }

            verify(chain, times(CAPACITY)).doFilter(any(), any());
        }

        @Test
        @DisplayName("rejects the request that exceeds the capacity")
        void rejectsBeyondCapacity() throws Exception {
            RateLimitFilter rateLimitFilter = filter(true, CAPACITY);

            for (int i = 0; i < CAPACITY; i++) {
                rateLimitFilter.doFilterInternal(request("/api/auth/login", "10.0.0.1"),
                        new MockHttpServletResponse(), chain);
            }

            MockHttpServletResponse blocked = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(
                    request("/api/auth/login", "10.0.0.1"), blocked, chain);

            assertThat(blocked.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
            // The chain must not run: a throttled request has to be stopped
            // before it can attempt authentication.
            verify(chain, times(CAPACITY)).doFilter(any(), any());
        }

        @Test
        @DisplayName("advertises the remaining allowance on every accepted request")
        void reportsRemainingTokens() throws Exception {
            RateLimitFilter rateLimitFilter = filter(true, CAPACITY);
            MockHttpServletResponse response = new MockHttpServletResponse();

            rateLimitFilter.doFilterInternal(
                    request("/api/auth/login", "10.0.0.1"), response, chain);

            assertThat(response.getHeader("X-RateLimit-Remaining"))
                    .isEqualTo(String.valueOf(CAPACITY - 1));
        }

        @Test
        @DisplayName("counts down the allowance across successive requests")
        void remainingDecreasesMonotonically() throws Exception {
            RateLimitFilter rateLimitFilter = filter(true, CAPACITY);

            for (int i = 0; i < CAPACITY; i++) {
                MockHttpServletResponse response = new MockHttpServletResponse();
                rateLimitFilter.doFilterInternal(
                        request("/api/auth/login", "10.0.0.1"), response, chain);
                assertThat(response.getHeader("X-RateLimit-Remaining"))
                        .isEqualTo(String.valueOf(CAPACITY - 1 - i));
            }
        }
    }

    @Nested
    @DisplayName("The 429 response")
    class ThrottledResponse {

        private MockHttpServletResponse exhaust() throws Exception {
            RateLimitFilter rateLimitFilter = filter(true, 1);
            rateLimitFilter.doFilterInternal(request("/api/auth/login", "10.0.0.9"),
                    new MockHttpServletResponse(), chain);

            MockHttpServletResponse blocked = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(
                    request("/api/auth/login", "10.0.0.9"), blocked, chain);
            return blocked;
        }

        @Test
        @DisplayName("includes a Retry-After header the client can act on")
        void includesRetryAfter() throws Exception {
            String retryAfter = exhaust().getHeader(HttpHeaders.RETRY_AFTER);

            assertThat(retryAfter).isNotNull();
            // Always at least 1: a Retry-After of 0 invites an immediate retry.
            assertThat(Long.parseLong(retryAfter)).isGreaterThanOrEqualTo(1L);
        }

        @Test
        @DisplayName("uses the standard ApiError shape, not an ad-hoc body")
        void usesStandardErrorShape() throws Exception {
            ApiError body = objectMapper.readValue(
                    exhaust().getContentAsString(), ApiError.class);

            assertThat(body.status()).isEqualTo(429);
            assertThat(body.code()).isEqualTo("RATE_LIMIT_EXCEEDED");
            assertThat(body.path()).isEqualTo("/api/auth/login");
            assertThat(body.correlationId()).isNotBlank();
        }

        @Test
        @DisplayName("does not leak credentials or internals into the body")
        void doesNotLeakInternals() throws Exception {
            String payload = exhaust().getContentAsString();

            assertThat(payload)
                    .doesNotContain("password")
                    .doesNotContain("Exception")
                    .doesNotContain("bucket4j");
        }
    }

    @Nested
    @DisplayName("Client isolation")
    class ClientIsolation {

        @Test
        @DisplayName("one abusive client cannot exhaust another client's allowance")
        void bucketsArePerClient() throws Exception {
            RateLimitFilter rateLimitFilter = filter(true, 1);

            rateLimitFilter.doFilterInternal(request("/api/auth/login", "10.0.0.1"),
                    new MockHttpServletResponse(), chain);
            MockHttpServletResponse firstBlocked = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(
                    request("/api/auth/login", "10.0.0.1"), firstBlocked, chain);

            // A different address must start with a full bucket, otherwise one
            // attacker could deny service to everyone else.
            MockHttpServletResponse other = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(
                    request("/api/auth/login", "10.0.0.2"), other, chain);

            assertThat(firstBlocked.getStatus()).isEqualTo(429);
            assertThat(other.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("identifies the caller by the first X-Forwarded-For hop")
        void usesFirstForwardedHop() throws Exception {
            RateLimitFilter rateLimitFilter = filter(true, 1);

            // Behind a proxy every request shares one socket address, so the
            // limiter must read the forwarded client address instead.
            MockHttpServletRequest first = request("/api/auth/login", "172.16.0.1");
            first.addHeader("X-Forwarded-For", "203.0.113.5, 172.16.0.1");
            rateLimitFilter.doFilterInternal(first, new MockHttpServletResponse(), chain);

            MockHttpServletRequest sameClient = request("/api/auth/login", "172.16.0.1");
            sameClient.addHeader("X-Forwarded-For", "203.0.113.5, 172.16.0.1");
            MockHttpServletResponse blocked = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(sameClient, blocked, chain);

            MockHttpServletRequest differentClient = request("/api/auth/login", "172.16.0.1");
            differentClient.addHeader("X-Forwarded-For", "203.0.113.99, 172.16.0.1");
            MockHttpServletResponse allowed = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(differentClient, allowed, chain);

            assertThat(blocked.getStatus()).isEqualTo(429);
            assertThat(allowed.getStatus()).isEqualTo(HttpStatus.OK.value());
        }

        @Test
        @DisplayName("ignores later X-Forwarded-For hops, which a client can forge")
        void ignoresForgedTrailingHops() throws Exception {
            RateLimitFilter rateLimitFilter = filter(true, 1);

            MockHttpServletRequest first = request("/api/auth/login", "172.16.0.1");
            first.addHeader("X-Forwarded-For", "203.0.113.5, 172.16.0.1");
            rateLimitFilter.doFilterInternal(first, new MockHttpServletResponse(), chain);

            // Same real client, but appending a different trailing hop must not
            // reset the bucket - otherwise the limit is trivially bypassed.
            MockHttpServletRequest spoofed = request("/api/auth/login", "172.16.0.1");
            spoofed.addHeader("X-Forwarded-For", "203.0.113.5, 198.51.100.7");
            MockHttpServletResponse response = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(spoofed, response, chain);

            assertThat(response.getStatus()).isEqualTo(429);
            verify(chain, times(1)).doFilter(any(), any());
        }

        @Test
        @DisplayName("falls back to the socket address when no proxy header is present")
        void fallsBackToRemoteAddr() throws Exception {
            RateLimitFilter rateLimitFilter = filter(true, 1);

            rateLimitFilter.doFilterInternal(request("/api/auth/login", "198.51.100.1"),
                    new MockHttpServletResponse(), chain);
            MockHttpServletResponse blocked = new MockHttpServletResponse();
            rateLimitFilter.doFilterInternal(
                    request("/api/auth/login", "198.51.100.1"), blocked, chain);

            assertThat(blocked.getStatus()).isEqualTo(429);
            // Exactly one request reached the chain: the first. The throttled
            // one was stopped before it could attempt authentication.
            verify(chain, times(1)).doFilter(any(), any());
        }
    }
}
