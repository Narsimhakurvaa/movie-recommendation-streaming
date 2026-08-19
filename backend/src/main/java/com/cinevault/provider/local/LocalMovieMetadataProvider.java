package com.cinevault.provider.local;

import com.cinevault.catalogue.domain.CreditType;
import com.cinevault.catalogue.domain.Genre;
import com.cinevault.catalogue.domain.Keyword;
import com.cinevault.catalogue.domain.Movie;
import com.cinevault.catalogue.domain.MovieCredit;
import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.provider.MovieMetadataProvider;
import com.cinevault.provider.ProviderMovie;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Metadata provider backed by the seeded local catalogue.
 *
 * <p>This is the default, and it is a genuine implementation rather than a
 * stub: it answers every query from real data in the database, so the product
 * is fully functional with no API key and no network access. That matters for
 * offline development, for deterministic tests, and for anyone cloning the
 * repository who should not have to register for a third-party account before
 * seeing the application work.
 *
 * <p>It reports {@link #isAvailable()} as {@code true} unconditionally, because
 * the database is a hard dependency of the application anyway.
 */
@Component
public class LocalMovieMetadataProvider implements MovieMetadataProvider {

    private final MovieRepository movieRepository;

    public LocalMovieMetadataProvider(MovieRepository movieRepository) {
        this.movieRepository = movieRepository;
    }

    @Override
    public String name() {
        return "local";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderMovie> search(String query, int page) {
        // Pages are one-based upstream; Spring Data is zero-based.
        var pageable = PageRequest.of(Math.max(0, page - 1), 20);
        return movieRepository.suggest(query, pageable).stream()
                .map(suggestion -> movieRepository.findDetailById(suggestion.id()))
                .flatMap(Optional::stream)
                .map(this::toProviderMovie)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProviderMovie> findById(String providerId) {
        try {
            return movieRepository.findDetailById(Long.valueOf(providerId))
                    .map(this::toProviderMovie);
        } catch (NumberFormatException notALocalId) {
            return Optional.empty();
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderMovie> fetchPopular(int page) {
        return movieRepository.findPopular(PageRequest.of(Math.max(0, page - 1), 20))
                .map(this::toProviderMovie)
                .getContent();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ProviderMovie> fetchTrending(int page) {
        return movieRepository.findTrending(LocalDate.now().minusMonths(18),
                        PageRequest.of(Math.max(0, page - 1), 20))
                .map(this::toProviderMovie)
                .getContent();
    }

    private ProviderMovie toProviderMovie(Movie movie) {
        return new ProviderMovie(
                String.valueOf(movie.getId()),
                movie.getTitle(),
                movie.getOriginalTitle(),
                movie.getOverview(),
                movie.getReleaseDate(),
                movie.getRuntimeMinutes(),
                movie.getOriginalLanguage(),
                movie.getOriginCountry(),
                movie.getPosterUrl(),
                movie.getBackdropUrl(),
                movie.getTrailerUrl(),
                movie.getExternalRating() == null ? 0d : movie.getExternalRating().doubleValue(),
                movie.getExternalVoteCount(),
                movie.getPopularity() == null ? 0d : movie.getPopularity().doubleValue(),
                movie.isAdult(),
                movie.getGenres().stream().map(Genre::getName).toList(),
                movie.getKeywords().stream().map(Keyword::getName).toList(),
                creditNames(movie, CreditType.CAST),
                creditNames(movie, CreditType.DIRECTOR),
                creditNames(movie, CreditType.WRITER),
                splitCompanies(movie.getProductionCompanies()));
    }

    private List<String> creditNames(Movie movie, CreditType type) {
        return movie.getCredits().stream()
                .filter(credit -> credit.getCreditType() == type)
                .sorted(java.util.Comparator.comparingInt(MovieCredit::getDisplayOrder))
                .map(credit -> credit.getPerson().getName())
                .toList();
    }

    private static List<String> splitCompanies(String companies) {
        if (companies == null || companies.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(companies.split(","))
                .map(String::trim)
                .filter(company -> !company.isEmpty())
                .toList();
    }
}
