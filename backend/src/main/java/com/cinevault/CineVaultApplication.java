package com.cinevault;

import com.cinevault.common.security.JwtProperties;
import com.cinevault.provider.MovieProviderProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Application entry point.
 *
 * <p>CineVault is a modular monolith: the {@code user}, {@code catalogue},
 * {@code interaction}, {@code recommendation}, {@code provider} and
 * {@code admin} packages each own their domain, and communicate through
 * services rather than reaching into one another's repositories. The boundaries
 * are drawn where a service split would go, so that extraction later is
 * mechanical - but they are not split now, because a single deployable is the
 * right shape for this system's actual scale.
 */
@SpringBootApplication
@EnableConfigurationProperties({JwtProperties.class, MovieProviderProperties.class})
@EnableJpaAuditing
@EnableScheduling
public class CineVaultApplication {

    public static void main(String[] args) {
        SpringApplication.run(CineVaultApplication.class, args);
    }
}
