package com.cinevault.recommendation.service;

import com.cinevault.catalogue.domain.CreditType;
import com.cinevault.interaction.domain.InteractionType;
import com.cinevault.interaction.repository.RatingRepository;
import com.cinevault.interaction.repository.RatingRepository.AffinityRow;
import com.cinevault.interaction.repository.UserFavouriteGenreRepository;
import com.cinevault.interaction.repository.UserPreferencesRepository;
import com.cinevault.interaction.repository.WatchHistoryRepository;
import com.cinevault.interaction.repository.WatchlistRepository;
import com.cinevault.recommendation.model.UserTasteProfile;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Builds a {@link UserTasteProfile} from persisted signals.
 *
 * <p>This is the seam between the database and the framework-free engine. It
 * runs a <em>fixed</em> number of aggregate queries - eleven, regardless of how
 * much history the user has - and hands the engine plain in-memory maps. The
 * alternative, letting strategies pull data as they score, would produce an
 * N+1 per candidate film.
 *
 * <h2>Blending explicit and implicit signals</h2>
 * <p>Genre affinity combines two sources: ratings (explicit, high confidence)
 * and browsing history (implicit, high volume). Ratings are weighted more
 * heavily because a 5-star score states a preference, whereas opening a detail
 * page merely suggests curiosity. Users who watch a lot but rate little would
 * otherwise look like cold-start users forever.
 */
@Component
public class TasteProfileAssembler {

    /** Weight applied to affinity derived from explicit ratings. */
    static final double RATING_SIGNAL_WEIGHT = 1.0;

    /** Weight applied to affinity derived from browsing behaviour. */
    static final double HISTORY_SIGNAL_WEIGHT = 0.35;

    /** Interactions meaningful enough to imply genuine interest. */
    private static final List<InteractionType> MEANINGFUL_INTERACTIONS = List.of(
            InteractionType.WATCHED_TRAILER,
            InteractionType.STARTED_WATCHING,
            InteractionType.COMPLETED,
            InteractionType.ADDED_TO_WATCHLIST);

    /**
     * Cap on how many neighbours the collaborative query may return, bounding
     * both query cost and the memory held while scoring.
     */
    static final int NEIGHBOUR_LIMIT = 400;

    private final RatingRepository ratingRepository;
    private final WatchHistoryRepository watchHistoryRepository;
    private final WatchlistRepository watchlistRepository;
    private final UserFavouriteGenreRepository favouriteGenreRepository;
    private final UserPreferencesRepository preferencesRepository;

    public TasteProfileAssembler(RatingRepository ratingRepository,
                                 WatchHistoryRepository watchHistoryRepository,
                                 WatchlistRepository watchlistRepository,
                                 UserFavouriteGenreRepository favouriteGenreRepository,
                                 UserPreferencesRepository preferencesRepository) {
        this.ratingRepository = ratingRepository;
        this.watchHistoryRepository = watchHistoryRepository;
        this.watchlistRepository = watchlistRepository;
        this.favouriteGenreRepository = favouriteGenreRepository;
        this.preferencesRepository = preferencesRepository;
    }

    /**
     * Assembles the profile for one user.
     *
     * @param userId the user to profile
     * @return a fully populated, immutable profile
     */
    @Transactional(readOnly = true)
    public UserTasteProfile assemble(Long userId) {
        Map<Long, Integer> ratings = new HashMap<>();
        ratingRepository.findScoresByUserId(userId)
                .forEach(row -> ratings.put(row.getMovieId(), row.getScore().intValue()));

        // Explicit genre affinity from ratings, blended with implicit affinity
        // from browsing, then normalised so the strongest signal reaches 1.0.
        Map<Long, Double> genreRaw = weighted(
                ratingRepository.findGenreAffinity(userId), RATING_SIGNAL_WEIGHT);
        mergeWeighted(genreRaw,
                watchHistoryRepository.findGenreAffinityFromHistory(userId, MEANINGFUL_INTERACTIONS),
                HISTORY_SIGNAL_WEIGHT);

        Set<Long> declaredGenres = new HashSet<>(favouriteGenreRepository.findGenreIdsByUserId(userId));
        // Onboarding picks seed the affinity map so they influence content
        // scoring, not just the cold-start path.
        declaredGenres.forEach(genreId -> genreRaw.merge(genreId, 1.0d, Double::sum));

        Map<Long, Double> keywordRaw = weighted(
                ratingRepository.findKeywordAffinity(userId), RATING_SIGNAL_WEIGHT);
        Map<Long, Double> castRaw = weighted(
                ratingRepository.findPersonAffinity(userId, CreditType.CAST), RATING_SIGNAL_WEIGHT);
        Map<Long, Double> directorRaw = weighted(
                ratingRepository.findPersonAffinity(userId, CreditType.DIRECTOR), RATING_SIGNAL_WEIGHT);
        Map<Long, Double> writerRaw = weighted(
                ratingRepository.findPersonAffinity(userId, CreditType.WRITER), RATING_SIGNAL_WEIGHT);

        Map<String, Double> languageRaw = new HashMap<>();
        ratingRepository.findLanguageAffinity(userId).forEach(row -> {
            if (row.getCode() != null && row.getWeight() != null) {
                languageRaw.merge(row.getCode(), row.getWeight(), Double::sum);
            }
        });

        Set<Long> interacted = new HashSet<>(watchHistoryRepository.findAllInteractedMovieIds(userId));
        interacted.addAll(ratings.keySet());

        Set<Long> watchlist = new HashSet<>(watchlistRepository.findMovieIdsByUserId(userId));
        Set<Long> liked = new HashSet<>(ratingRepository.findLikedMovieIds(
                userId, UserTasteProfile.LIKED_THRESHOLD));

        var preferences = preferencesRepository.findByUserId(userId);
        Set<String> preferredLanguages = preferences
                .map(p -> Set.copyOf(p.preferredLanguageCodes()))
                .orElseGet(Set::of);
        double minimumRating = preferences
                .map(p -> p.getMinimumRating() == null
                        ? 0d : p.getMinimumRating().doubleValue())
                .orElse(0d);
        boolean includeAdult = preferences.map(p -> p.isIncludeAdult()).orElse(false);

        return new UserTasteProfile(userId, ratings,
                UserTasteProfile.normalised(genreRaw),
                UserTasteProfile.normalised(keywordRaw),
                UserTasteProfile.normalised(castRaw),
                UserTasteProfile.normalised(directorRaw),
                UserTasteProfile.normalised(writerRaw),
                UserTasteProfile.normalised(languageRaw),
                declaredGenres, interacted, watchlist, liked,
                preferredLanguages, minimumRating, includeAdult);
    }

    /** Rating vectors of overlapping users, for the collaborative strategy. */
    @Transactional(readOnly = true)
    public Map<Long, Map<Long, Integer>> loadNeighbourRatings(Long userId) {
        Map<Long, Map<Long, Integer>> byUser = new HashMap<>();
        ratingRepository.findNeighbourRatings(userId, NEIGHBOUR_LIMIT).forEach(row ->
                byUser.computeIfAbsent(row.getUserId(), id -> new HashMap<>())
                        .put(row.getMovieId(), row.getScore().intValue()));
        return byUser;
    }

    private static Map<Long, Double> weighted(List<AffinityRow> rows, double factor) {
        Map<Long, Double> result = new HashMap<>();
        for (AffinityRow row : rows) {
            if (row.getId() != null && row.getWeight() != null) {
                result.merge(row.getId(), row.getWeight() * factor, Double::sum);
            }
        }
        return result;
    }

    private static void mergeWeighted(Map<Long, Double> target, List<AffinityRow> rows, double factor) {
        for (AffinityRow row : rows) {
            if (row.getId() != null && row.getWeight() != null) {
                target.merge(row.getId(), row.getWeight() * factor, Double::sum);
            }
        }
    }
}
