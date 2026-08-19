package com.cinevault.provider;

import com.cinevault.provider.local.LocalMovieMetadataProvider;
import com.cinevault.provider.tmdb.TmdbMovieMetadataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Chooses which {@link MovieMetadataProvider} the application uses.
 *
 * <p>Selection is by configuration, with a safety net: if TMDB is requested but
 * no API key is present, the application logs a clear warning and falls back to
 * the local provider rather than starting in a state where every catalogue
 * request would fail. A missing optional credential should degrade the feature,
 * not break the deployment.
 */
@Configuration
public class MovieProviderConfiguration {

    private static final Logger log = LoggerFactory.getLogger(MovieProviderConfiguration.class);

    @Bean
    @Primary
    public MovieMetadataProvider movieMetadataProvider(MovieProviderProperties properties,
                                                       TmdbMovieMetadataProvider tmdbProvider,
                                                       LocalMovieMetadataProvider localProvider) {
        if (!"tmdb".equals(properties.type())) {
            log.info("Movie metadata provider: local seeded catalogue");
            return localProvider;
        }
        if (!tmdbProvider.isAvailable()) {
            log.warn("""
                    TMDB was selected but TMDB_API_KEY is not set. \
                    Falling back to the local seeded catalogue. \
                    Set TMDB_API_KEY to enable live metadata.""");
            return localProvider;
        }
        log.info("Movie metadata provider: TMDB");
        return tmdbProvider;
    }
}
