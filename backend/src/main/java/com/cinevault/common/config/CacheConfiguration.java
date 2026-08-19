package com.cinevault.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

/**
 * Caching policy.
 *
 * <h2>Redis is optional</h2>
 * <p>Redis genuinely helps for trending/popular rails and upstream metadata,
 * which are read constantly and change slowly. It helps nothing for a personal
 * watchlist. So it is used selectively, and when it is absent the application
 * falls back to an in-memory cache rather than refusing to start. Requiring a
 * Redis instance to run the project locally would be a cost with no benefit.
 *
 * <h2>Per-cache TTLs</h2>
 * <p>Each cache expires on its own schedule, chosen from how quickly the
 * underlying data actually changes. A single global TTL would either serve
 * stale recommendations or pointlessly evict a genre list that changes monthly.
 */
@Configuration
@EnableCaching
public class CacheConfiguration {

    private static final Logger log = LoggerFactory.getLogger(CacheConfiguration.class);

    /** Cache names paired with how long their entries stay fresh. */
    private static final Map<String, Duration> CACHE_TTLS = Map.of(
            // Rebuilt often enough to feel live, cached enough to spare the DB.
            "trendingMovies", Duration.ofMinutes(15),
            "popularMovies", Duration.ofMinutes(30),
            // Changes only when the catalogue is re-imported.
            "genres", Duration.ofHours(6),
            // Type-ahead: high volume, tolerant of slight staleness.
            "movieSuggestions", Duration.ofMinutes(10),
            // Personalised results: short, because new ratings should show up.
            "userRecommendations", Duration.ofMinutes(5),
            // Upstream metadata is effectively immutable once published.
            "tmdbMovie", Duration.ofHours(24),
            "tmdbSearch", Duration.ofHours(1),
            "tmdbPopular", Duration.ofHours(3),
            "tmdbTrending", Duration.ofHours(1));

    private static final Duration DEFAULT_TTL = Duration.ofMinutes(10);

    /**
     * Redis-backed caching, active when {@code cinevault.cache.type=redis}.
     */
    @Bean
    @ConditionalOnProperty(name = "cinevault.cache.type", havingValue = "redis")
    public CacheManager redisCacheManager(RedisConnectionFactory connectionFactory,
                                          ObjectMapper objectMapper) {
        log.info("Cache backend: Redis");
        var serializer = new GenericJackson2JsonRedisSerializer(objectMapper);
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(DEFAULT_TTL)
                // Null results are not cached: caching a miss would pin a
                // "not found" in place until the TTL expired.
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer));

        var perCache = new java.util.HashMap<String, RedisCacheConfiguration>();
        CACHE_TTLS.forEach((name, ttl) -> perCache.put(name, base.entryTtl(ttl)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }

    /**
     * In-memory fallback, used whenever Redis is not configured.
     *
     * <p>Correct for a single instance. It does not expire entries on a timer,
     * which is an accepted trade-off for local development; any deployment that
     * needs shared or expiring cache state should enable Redis.
     */
    @Bean
    @ConditionalOnProperty(name = "cinevault.cache.type", havingValue = "memory",
            matchIfMissing = true)
    public CacheManager inMemoryCacheManager() {
        log.info("Cache backend: in-memory (set cinevault.cache.type=redis for a shared cache)");
        return new ConcurrentMapCacheManager(CACHE_TTLS.keySet().toArray(String[]::new));
    }
}
