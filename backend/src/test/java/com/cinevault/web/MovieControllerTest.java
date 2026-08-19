package com.cinevault.web;

import com.cinevault.catalogue.dto.MovieDtos.MovieSuggestionResponse;
import com.cinevault.catalogue.dto.MovieDtos.MovieSummary;
import com.cinevault.catalogue.service.MovieService;
import com.cinevault.catalogue.web.MovieController;
import com.cinevault.common.dto.PageResponse;
import com.cinevault.common.exception.GlobalExceptionHandler;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.common.security.JwtPrincipal;
import com.cinevault.interaction.domain.InteractionType;
import com.cinevault.interaction.service.WatchHistoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web-layer tests for the public catalogue endpoints.
 *
 * <p>Built with standalone MockMvc so the controller, its parameter binding,
 * its validation annotations and the global exception handler are exercised
 * together without booting a full application context.
 *
 * <p>Two pieces of infrastructure must be registered explicitly, because
 * standalone mode does not autoconfigure them: a {@code Validator} (otherwise
 * the {@code @Min}/{@code @Max} parameter constraints are silently ignored and
 * the negative-page tests would pass for the wrong reason) and the
 * {@code @AuthenticationPrincipal} resolver behind {@code @CurrentUser}
 * (otherwise Spring tries to data-bind {@code JwtPrincipal} from query
 * parameters instead of leaving it null for anonymous callers).
 */
class MovieControllerTest {

    private MovieService movieService;
    private WatchHistoryService watchHistoryService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        movieService = mock(MovieService.class);
        watchHistoryService = mock(WatchHistoryService.class);

        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new MovieController(movieService, watchHistoryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setValidator(validator)
                .build();
    }

    private static MovieSummary sampleMovie() {
        return new MovieSummary(1L, "Interstellar", "interstellar-2014", 2014,
                "https://example.com/poster.jpg", new BigDecimal("8.4"),
                new BigDecimal("4.6"), 128, 169,
                List.of("Science Fiction", "Drama"), null, null);
    }

    @Test
    @DisplayName("serves a page of movies to an anonymous caller")
    void browseReturnsPagedEnvelope() throws Exception {
        when(movieService.discover(any(), any(), isNull()))
                .thenReturn(new PageResponse<>(List.of(sampleMovie()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].title").value("Interstellar"))
                .andExpect(jsonPath("$.content[0].genres[0]").value("Science Fiction"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));
    }

    @Test
    @DisplayName("never exposes a raw Spring Page envelope")
    void doesNotLeakSpringPageShape() throws Exception {
        when(movieService.discover(any(), any(), isNull()))
                .thenReturn(new PageResponse<>(List.of(sampleMovie()), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                // These keys would appear if a Page were serialised directly;
                // the contract promises a stable PageResponse instead.
                .andExpect(jsonPath("$.pageable").doesNotExist())
                .andExpect(jsonPath("$.numberOfElements").doesNotExist())
                .andExpect(jsonPath("$.sort").doesNotExist());
    }

    @Test
    @DisplayName("rejects a negative page index")
    void rejectsNegativePage() throws Exception {
        mockMvc.perform(get("/api/movies").param("page", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        verifyNoInteractions(movieService);
    }

    @Test
    @DisplayName("rejects a page size beyond the permitted maximum")
    void rejectsOversizedPage() throws Exception {
        // Without a cap, a single request could ask for the whole catalogue.
        mockMvc.perform(get("/api/movies").param("size", "5000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.validationErrors[0].field").value("size"));

        verifyNoInteractions(movieService);
    }

    @Test
    @DisplayName("rejects a non-numeric page parameter")
    void rejectsNonNumericPage() throws Exception {
        mockMvc.perform(get("/api/movies").param("page", "abc"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("returns a structured 404 with a correlation id and no internals")
    void returnsStructuredNotFound() throws Exception {
        when(movieService.findById(eq(999L), isNull()))
                .thenThrow(new ResourceNotFoundException("Movie", 999));

        mockMvc.perform(get("/api/movies/999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value("/api/movies/999"))
                .andExpect(jsonPath("$.correlationId").exists())
                // An error body must never carry implementation detail.
                .andExpect(jsonPath("$.stackTrace").doesNotExist())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist());
    }

    @Test
    @DisplayName("does not record a view signal for an anonymous visitor")
    void doesNotRecordAnonymousView() throws Exception {
        when(movieService.findById(eq(1L), isNull())).thenReturn(null);

        mockMvc.perform(get("/api/movies/1")).andExpect(status().isOk());

        // There is no user to attribute the signal to.
        verify(watchHistoryService, never())
                .record(anyLong(), anyLong(), any(InteractionType.class), any());
    }

    @Test
    @DisplayName("returns suggestions as a bare array")
    void suggestReturnsArray() throws Exception {
        when(movieService.suggest(eq("inter"), anyInt())).thenReturn(List.of(
                new MovieSuggestionResponse(1L, "Interstellar", "interstellar-2014", null, 2014)));

        mockMvc.perform(get("/api/movies/suggest").param("query", "inter"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].title").value("Interstellar"));
    }

    @Test
    @DisplayName("requires the query parameter on search")
    void searchRequiresQuery() throws Exception {
        mockMvc.perform(get("/api/movies/search"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(movieService);
    }

    @Test
    @DisplayName("passes the caller's id through so cards carry personal state")
    void propagatesAuthenticatedUser() throws Exception {
        when(movieService.discover(any(), any(), eq(7L)))
                .thenReturn(new PageResponse<>(List.of(
                        sampleMovie().withUserState(true, 5)), 0, 20, 1L, 1, true, true));

        mockMvc.perform(get("/api/movies").with(authenticated(7L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].inWatchlist").value(true))
                .andExpect(jsonPath("$.content[0].userRating").value(5));

        verify(movieService).discover(any(), any(), eq(7L));
    }

    /** Installs a {@link JwtPrincipal} the way the JWT filter would. */
    private static org.springframework.test.web.servlet.request.RequestPostProcessor authenticated(
            long userId) {
        return request -> {
            var principal = new JwtPrincipal(userId, "viewer@example.com", java.util.Set.of("ROLE_USER"));
            var authentication = new org.springframework.security.authentication
                    .UsernamePasswordAuthenticationToken(principal, null, List.of());
            var context = org.springframework.security.core.context.SecurityContextHolder
                    .createEmptyContext();
            context.setAuthentication(authentication);
            org.springframework.security.core.context.SecurityContextHolder.setContext(context);
            return request;
        };
    }
}
