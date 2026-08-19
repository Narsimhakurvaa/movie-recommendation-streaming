package com.cinevault.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.web.client.RestClient;

import java.time.Clock;

/**
 * Cross-cutting infrastructure beans.
 */
@Configuration
@EnableSpringDataWebSupport(
        // Pin the DTO-style page serialisation so Spring's warning about
        // unstable PageImpl JSON does not apply. Our controllers return
        // PageResponse anyway; this covers anything that slips through.
        pageSerializationMode = EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO)
public class ApplicationConfiguration {

    /**
     * A single injectable clock.
     *
     * <p>Time-dependent logic (recency decay, token expiry) takes this rather
     * than calling {@code Instant.now()} directly, which is what allows those
     * behaviours to be tested deterministically with a fixed clock.
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /** Shared builder so every outbound client inherits common configuration. */
    @Bean
    public RestClient.Builder restClientBuilder() {
        return RestClient.builder();
    }
}
