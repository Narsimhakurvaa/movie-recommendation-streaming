package com.cinevault.recommendation;

import com.cinevault.recommendation.explain.ExplanationBuilder;
import com.cinevault.recommendation.model.MovieFeatures;
import com.cinevault.recommendation.model.RecommendationType;
import com.cinevault.recommendation.model.ScoredMovie;
import com.cinevault.recommendation.model.UserTasteProfile;
import com.cinevault.recommendation.strategy.ColdStartRecommendationStrategy;
import com.cinevault.recommendation.strategy.CollaborativeRecommendationStrategy;
import com.cinevault.recommendation.strategy.ContentBasedRecommendationStrategy;
import com.cinevault.recommendation.strategy.HybridRecommendationStrategy;
import com.cinevault.recommendation.strategy.PopularityRecommendationStrategy;
import com.cinevault.recommendation.support.MovieFixtures;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.cinevault.recommendation.support.MovieFixtures.ANIMATION;
import static com.cinevault.recommendation.support.MovieFixtures.ROMANCE;
import static com.cinevault.recommendation.support.MovieFixtures.SCIFI;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Behavioural tests for the recommendation engine.
 *
 * <p>Covers the eight scenarios the engine must handle correctly: a brand-new
 * user, onboarding preferences only, a single rating, a rich history, watch
 * history and watchlist influence, a profile matching nothing, collaborative
 * filtering, similar-movie lookup, and the popularity fallback.
 *
 * <p>A fixed clock is injected throughout so recency scoring is reproducible.
 */
class RecommendationEngineTest {

    private static final Clock FIXED_CLOCK =
            Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);

    private static ContentBasedRecommendationStrategy contentStrategy() {
        return new ContentBasedRecommendationStrategy(
                MovieFixtures.GENRE_NAMES::get,
                MovieFixtures.PERSON_NAMES::get,
                MovieFixtures.KEYWORD_NAMES::get);
    }

    private static PopularityRecommendationStrategy popularityStrategy() {
        return new PopularityRecommendationStrategy(FIXED_CLOCK);
    }

    private static CollaborativeRecommendationStrategy collaborativeStrategy(
            Map<Long, Map<Long, Integer>> neighbours) {
        return new CollaborativeRecommendationStrategy(userId -> neighbours);
    }

    private static HybridRecommendationStrategy hybridStrategy(
            Map<Long, Map<Long, Integer>> neighbours) {
        return new HybridRecommendationStrategy(
                contentStrategy(), collaborativeStrategy(neighbours), popularityStrategy());
    }

    private static ColdStartRecommendationStrategy coldStartStrategy() {
        return new ColdStartRecommendationStrategy(
                popularityStrategy(), MovieFixtures.GENRE_NAMES::get);
    }

    private static List<String> titlesOf(List<ScoredMovie> ranked) {
        return ranked.stream().map(scored -> scored.movie().title()).toList();
    }

    @Nested
    @DisplayName("Scenario 1: a brand-new user with no signals")
    class NewUser {

        @Test
        @DisplayName("is classified as cold start")
        void isColdStart() {
            UserTasteProfile profile = UserTasteProfile.empty(1L);

            assertThat(profile.isColdStart()).isTrue();
            assertThat(profile.hasNoSignals()).isTrue();
        }

        @Test
        @DisplayName("never receives an empty recommendation page")
        void neverReturnsEmpty() {
            List<ScoredMovie> ranked =
                    coldStartStrategy().score(UserTasteProfile.empty(1L), MovieFixtures.catalogue());

            assertThat(ranked)
                    .as("a new user must always be given something to watch")
                    .isNotEmpty()
                    .hasSize(MovieFixtures.catalogue().size());
        }

        @Test
        @DisplayName("receives normalised scores and a diversified list")
        void diversifiesResults() {
            List<ScoredMovie> ranked =
                    coldStartStrategy().score(UserTasteProfile.empty(1L), MovieFixtures.catalogue());

            assertThat(ranked).allSatisfy(scored ->
                    assertThat(scored.score()).isBetween(0d, 1d));

            long distinctGenres = ranked.stream().limit(6)
                    .flatMap(scored -> scored.movie().genreIds().stream())
                    .distinct().count();
            assertThat(distinctGenres)
                    .as("the top of a cold-start page should not be one genre repeated")
                    .isGreaterThanOrEqualTo(4);
        }

        @Test
        @DisplayName("does not lead with an obscure, poorly rated title")
        void avoidsObscureTitles() {
            List<ScoredMovie> ranked =
                    coldStartStrategy().score(UserTasteProfile.empty(1L), MovieFixtures.catalogue());

            assertThat(ranked.get(0).movie().title()).isNotEqualTo("Obscure Horror");
        }
    }

    @Nested
    @DisplayName("Scenario 2: a new user who declared favourite genres")
    class OnboardingPreferences {

        @Test
        @DisplayName("ranks the declared genre first")
        void prioritisesDeclaredGenre() {
            UserTasteProfile animationFan = MovieFixtures.profileOf(
                    2L, Map.of(), Set.of(ANIMATION), Set.of());

            List<ScoredMovie> ranked =
                    coldStartStrategy().score(animationFan, MovieFixtures.catalogue());

            assertThat(ranked.get(0).movie().title()).isEqualTo("Spirited Away");
        }

        @Test
        @DisplayName("explains the choice using the onboarding selection")
        void explainsUsingPreference() {
            UserTasteProfile animationFan = MovieFixtures.profileOf(
                    2L, Map.of(), Set.of(ANIMATION), Set.of());

            List<ScoredMovie> ranked =
                    coldStartStrategy().score(animationFan, MovieFixtures.catalogue());

            assertThat(ExplanationBuilder.describe(ranked.get(0), RecommendationType.COLD_START))
                    .contains("Animation");
        }

        @Test
        @DisplayName("lets a niche preference outrank an unrelated blockbuster")
        void preferenceBeatsPopularity() {
            UserTasteProfile romanceFan = MovieFixtures.profileOf(
                    3L, Map.of(), Set.of(ROMANCE), Set.of());

            List<ScoredMovie> ranked =
                    coldStartStrategy().score(romanceFan, MovieFixtures.catalogue());

            assertThat(ranked.get(0).movie().title()).isEqualTo("A Quiet Romance");
        }
    }

    @Nested
    @DisplayName("Scenario 3: a user with exactly one rating")
    class SingleRating {

        private final UserTasteProfile profile =
                MovieFixtures.profileOf(4L, Map.of(1L, 5), Set.of(), Set.of());

        @Test
        @DisplayName("is still cold start, but content scoring already applies")
        void contentAppliesButCollaborativeDoesNot() {
            assertThat(profile.isColdStart()).isTrue();
            assertThat(contentStrategy().supports(profile)).isTrue();
            assertThat(collaborativeStrategy(Map.of()).supports(profile))
                    .as("one rating is not enough to find meaningful neighbours")
                    .isFalse();
        }

        @Test
        @DisplayName("surfaces thematically related films from a single data point")
        void surfacesRelatedFilms() {
            List<ScoredMovie> ranked =
                    contentStrategy().score(profile, MovieFixtures.unseenBy(profile));
            ranked.sort(ScoredMovie.byScoreDescending());

            // Interstellar is sci-fi/drama with a space theme, directed by Nolan.
            // Shared genre plus keyword outweighs the director signal alone, so
            // Arrival and Dune lead and The Prestige follows.
            assertThat(titlesOf(ranked).subList(0, 3))
                    .containsAnyOf("Arrival", "Dune")
                    .containsAnyOf("The Prestige", "Inception");
        }

        @Test
        @DisplayName("never recommends the film the user already rated")
        void excludesRatedFilm() {
            List<ScoredMovie> ranked =
                    contentStrategy().score(profile, MovieFixtures.unseenBy(profile));

            assertThat(ranked).noneMatch(scored -> scored.movieId() == 1L);
        }

        @Test
        @DisplayName("does not put an unrelated genre at the top")
        void excludesUnrelatedGenres() {
            List<ScoredMovie> ranked =
                    contentStrategy().score(profile, MovieFixtures.unseenBy(profile));
            ranked.sort(ScoredMovie.byScoreDescending());

            assertThat(titlesOf(ranked).subList(0, 3)).doesNotContain("A Quiet Romance");
        }
    }

    @Nested
    @DisplayName("Scenario 4: a user with a rich rating history")
    class RichHistory {

        private UserTasteProfile nolanFan() {
            Map<Long, Integer> ratings = new LinkedHashMap<>();
            ratings.put(1L, 5);   // Interstellar
            ratings.put(2L, 5);   // Inception
            ratings.put(3L, 4);   // The Dark Knight
            ratings.put(14L, 5);  // The Prestige
            ratings.put(10L, 1);  // A Quiet Romance, actively disliked
            return MovieFixtures.profileOf(5L, ratings, Set.of(), Set.of());
        }

        @Test
        @DisplayName("learns a strong director affinity")
        void learnsDirectorAffinity() {
            assertThat(nolanFan().directorAffinity())
                    .hasEntrySatisfying(MovieFixtures.NOLAN,
                            weight -> assertThat(weight).isGreaterThan(0.9));
        }

        @Test
        @DisplayName("gains no affinity from films rated below the midpoint")
        void ignoresDislikedGenres() {
            assertThat(nolanFan().genreAffinity())
                    .as("a 1-star rating must not create positive affinity")
                    .doesNotContainKey(ROMANCE);
        }

        @Test
        @DisplayName("fills the top of the list with the preferred genre")
        void prioritisesPreferredGenre() {
            UserTasteProfile profile = nolanFan();
            List<ScoredMovie> ranked =
                    contentStrategy().score(profile, MovieFixtures.unseenBy(profile));
            ranked.sort(ScoredMovie.byScoreDescending());

            assertThat(ranked.subList(0, 3))
                    .allSatisfy(scored ->
                            assertThat(scored.movie().genreIds()).contains(SCIFI));
        }

        @Test
        @DisplayName("is no longer treated as cold start")
        void notColdStart() {
            assertThat(nolanFan().isColdStart()).isFalse();
        }
    }

    @Nested
    @DisplayName("Scenario 5: watch history and watchlist influence ranking")
    class WatchlistInfluence {

        @Test
        @DisplayName("ranks a watchlisted film above its unsaved equivalent")
        void watchlistBoostsScore() {
            Map<Long, Integer> ratings = Map.of(1L, 5, 2L, 5, 5L, 4);
            UserTasteProfile without = MovieFixtures.profileOf(6L, ratings, Set.of(), Set.of());
            UserTasteProfile with = MovieFixtures.profileOf(6L, ratings, Set.of(), Set.of(6L));

            var engine = hybridStrategy(Map.of());
            double plain = scoreOf(engine.score(without, MovieFixtures.unseenBy(without)), 6L);
            double boosted = scoreOf(engine.score(with, MovieFixtures.unseenBy(with)), 6L);

            assertThat(boosted)
                    .as("saving a film signals intent and should lift it")
                    .isGreaterThan(plain);
        }

        @Test
        @DisplayName("never recommends a film back to the user who rated it")
        void excludesRatedFilms() {
            Map<Long, Integer> ratings = Map.of(1L, 5, 2L, 5, 5L, 4);
            UserTasteProfile profile = MovieFixtures.profileOf(6L, ratings, Set.of(), Set.of());

            List<ScoredMovie> ranked =
                    hybridStrategy(Map.of()).score(profile, MovieFixtures.unseenBy(profile));

            assertThat(ranked).noneMatch(scored -> ratings.containsKey(scored.movieId()));
        }

        private double scoreOf(List<ScoredMovie> ranked, long movieId) {
            return ranked.stream()
                    .filter(scored -> scored.movieId() == movieId)
                    .findFirst().orElseThrow().score();
        }
    }

    @Nested
    @DisplayName("Scenario 6: a profile matching nothing in the catalogue")
    class NoMatchingContent {

        private final UserTasteProfile alienTaste = new UserTasteProfile(7L, Map.of(999L, 5),
                Map.of(4242L, 1.0), Map.of(4243L, 1.0), Map.of(4244L, 1.0),
                Map.of(4245L, 1.0), Map.of(), Map.of("xx", 1.0),
                Set.of(), Set.of(999L), Set.of(), Set.of(999L), Set.of(), 0d, false);

        @Test
        @DisplayName("produces no content-based matches")
        void contentYieldsNothing() {
            assertThat(contentStrategy().score(alienTaste, MovieFixtures.catalogue())).isEmpty();
        }

        @Test
        @DisplayName("still returns a full page via the popularity floor")
        void hybridStillReturnsResults() {
            List<ScoredMovie> ranked =
                    hybridStrategy(Map.of()).score(alienTaste, MovieFixtures.catalogue());

            assertThat(ranked)
                    .as("the popularity component guarantees the page is never empty")
                    .hasSize(MovieFixtures.catalogue().size());
            assertThat(ranked).isSortedAccordingTo(
                    (a, b) -> Double.compare(b.score(), a.score()));
        }

        @Test
        @DisplayName("still attaches an honest explanation to every result")
        void stillExplainsResults() {
            List<ScoredMovie> ranked =
                    hybridStrategy(Map.of()).score(alienTaste, MovieFixtures.catalogue());

            assertThat(ranked).allSatisfy(scored ->
                    assertThat(ExplanationBuilder.describe(scored, RecommendationType.HYBRID))
                            .isNotBlank());
        }
    }

    @Nested
    @DisplayName("Scenario 7: collaborative filtering")
    class Collaborative {

        // Target loves 1 and 2, is lukewarm on 4, and has not seen Dune (6).
        private final Map<Long, Integer> myRatings = Map.of(1L, 5, 2L, 5, 4L, 3);
        private final UserTasteProfile profile =
                MovieFixtures.profileOf(8L, myRatings, Set.of(), Set.of());

        /**
         * Neighbours must show rating variance: a user who scores everything
         * identically mean-centres to the zero vector and expresses no
         * preference at all.
         */
        private Map<Long, Map<Long, Integer>> neighbours() {
            Map<Long, Map<Long, Integer>> neighbours = new LinkedHashMap<>();
            neighbours.put(20L, Map.of(1L, 5, 2L, 5, 4L, 3, 6L, 5));
            neighbours.put(21L, Map.of(1L, 5, 2L, 4, 4L, 2, 6L, 5));
            neighbours.put(23L, Map.of(1L, 4, 2L, 5, 4L, 2, 6L, 4));
            neighbours.put(22L, Map.of(1L, 2, 2L, 1, 10L, 5)); // opposing taste
            return neighbours;
        }

        @Test
        @DisplayName("recommends what similar viewers rated highly")
        void surfacesNeighbourFavourite() {
            List<ScoredMovie> ranked = collaborativeStrategy(neighbours())
                    .score(profile, MovieFixtures.unseenBy(profile));
            ranked.sort(ScoredMovie.byScoreDescending());

            assertThat(ranked.get(0).movie().title()).isEqualTo("Dune");
        }

        @Test
        @DisplayName("explains the recommendation as taste similarity")
        void explainsAsSimilarViewers() {
            List<ScoredMovie> ranked = collaborativeStrategy(neighbours())
                    .score(profile, MovieFixtures.unseenBy(profile));
            ranked.sort(ScoredMovie.byScoreDescending());

            assertThat(ExplanationBuilder.describe(ranked.get(0), RecommendationType.COLLABORATIVE))
                    .containsIgnoringCase("similar taste");
        }

        @Test
        @DisplayName("trusts a prediction backed by more neighbours")
        void confidenceGrowsWithNeighbourCount() {
            var many = collaborativeStrategy(neighbours());
            var one = collaborativeStrategy(Map.of(20L, Map.of(1L, 5, 2L, 5, 4L, 3, 6L, 5)));

            double withMany = scoreOfDune(many.score(profile, MovieFixtures.unseenBy(profile)));
            double withOne = scoreOfDune(one.score(profile, MovieFixtures.unseenBy(profile)));

            assertThat(withMany)
                    .as("a single enthusiastic stranger should not dominate")
                    .isGreaterThan(withOne);
        }

        @Test
        @DisplayName("ignores a neighbour who rated everything identically")
        void ignoresZeroVarianceNeighbour() {
            // Mean-centres to the zero vector, for which cosine is undefined.
            var flat = collaborativeStrategy(Map.of(30L, Map.of(1L, 3, 2L, 3, 6L, 3)));

            assertThat(flat.score(profile, MovieFixtures.unseenBy(profile))).isEmpty();
        }

        @Test
        @DisplayName("produces nothing when the target user has no rating variance")
        void ignoresZeroVarianceTargetUser() {
            UserTasteProfile flatUser =
                    MovieFixtures.profileOf(15L, Map.of(1L, 4, 2L, 4, 4L, 4), Set.of(), Set.of());

            assertThat(collaborativeStrategy(neighbours())
                    .score(flatUser, MovieFixtures.unseenBy(flatUser))).isEmpty();
        }

        @Test
        @DisplayName("is disabled for a user with too few ratings")
        void requiresEnoughRatings() {
            UserTasteProfile sparse =
                    MovieFixtures.profileOf(9L, Map.of(1L, 5), Set.of(), Set.of());

            assertThat(collaborativeStrategy(neighbours()).supports(sparse)).isFalse();
            assertThat(collaborativeStrategy(neighbours())
                    .score(sparse, MovieFixtures.catalogue())).isEmpty();
        }

        private double scoreOfDune(List<ScoredMovie> ranked) {
            return ranked.stream()
                    .filter(scored -> scored.movieId() == 6L)
                    .findFirst().orElseThrow().score();
        }
    }

    @Nested
    @DisplayName("Scenario 8: similar-movie lookup")
    class SimilarMovies {

        @Test
        @DisplayName("excludes the seed film from its own results")
        void excludesSeed() {
            MovieFeatures seed = MovieFixtures.byId(1L);
            List<ScoredMovie> similar = hybridStrategy(Map.of()).findSimilar(
                    seed, MovieFixtures.catalogue(),
                    MovieFixtures.GENRE_NAMES::get, MovieFixtures.PERSON_NAMES::get);

            assertThat(similar).noneMatch(scored -> scored.movieId() == seed.movieId());
        }

        @Test
        @DisplayName("ranks films sharing genre and theme highest")
        void ranksSharedMetadataHighest() {
            List<ScoredMovie> similar = hybridStrategy(Map.of()).findSimilar(
                    MovieFixtures.byId(1L), MovieFixtures.catalogue(),
                    MovieFixtures.GENRE_NAMES::get, MovieFixtures.PERSON_NAMES::get);

            assertThat(titlesOf(similar).subList(0, 3)).containsAnyOf("Arrival", "Dune");
            assertThat(titlesOf(similar).subList(0, 3)).doesNotContain("Obscure Horror");
        }

        @Test
        @DisplayName("names the shared director in the explanation")
        void namesSharedDirector() {
            List<ScoredMovie> similar = hybridStrategy(Map.of()).findSimilar(
                    MovieFixtures.byId(1L), MovieFixtures.catalogue(),
                    MovieFixtures.GENRE_NAMES::get, MovieFixtures.PERSON_NAMES::get);

            ScoredMovie prestige = similar.stream()
                    .filter(scored -> scored.movie().title().equals("The Prestige"))
                    .findFirst().orElseThrow();

            assertThat(ExplanationBuilder.describe(prestige, RecommendationType.SIMILAR))
                    .contains("Christopher Nolan");
        }

        @Test
        @DisplayName("returns nothing rather than forcing weak matches")
        void returnsEmptyForIsolatedFilm() {
            // Spirited Away shares no genre, keyword, cast, crew or language.
            List<ScoredMovie> similar = hybridStrategy(Map.of()).findSimilar(
                    MovieFixtures.byId(7L), MovieFixtures.catalogue(),
                    MovieFixtures.GENRE_NAMES::get, MovieFixtures.PERSON_NAMES::get);

            assertThat(similar).isEmpty();
        }
    }

    @Nested
    @DisplayName("Scenario 9: the popularity fallback")
    class PopularityFallback {

        @Test
        @DisplayName("always applies, whatever the user's state")
        void alwaysApplies() {
            assertThat(popularityStrategy().supports(UserTasteProfile.empty(10L))).isTrue();
        }

        @Test
        @DisplayName("ranks a strong recent release above an obscure weak one")
        void ranksQualityAndRecency() {
            List<ScoredMovie> ranked =
                    popularityStrategy().score(UserTasteProfile.empty(10L), MovieFixtures.catalogue());

            assertThat(scoreOf(ranked, "Brand New Blockbuster"))
                    .isGreaterThan(scoreOf(ranked, "Obscure Horror"));
        }

        @Test
        @DisplayName("applies recency decay without burying an acclaimed classic")
        void appliesRecencyDecay() {
            List<ScoredMovie> ranked =
                    popularityStrategy().score(UserTasteProfile.empty(10L), MovieFixtures.catalogue());

            assertThat(scoreOf(ranked, "Brand New Blockbuster"))
                    .isGreaterThan(scoreOf(ranked, "Ancient Classic"));
            assertThat(scoreOf(ranked, "Ancient Classic"))
                    .as("a 1954 classic should still be a respectable suggestion")
                    .isGreaterThan(0.3);
        }

        private double scoreOf(List<ScoredMovie> ranked, String title) {
            return ranked.stream()
                    .filter(scored -> scored.movie().title().equals(title))
                    .findFirst().orElseThrow().score();
        }
    }

    @Nested
    @DisplayName("Hybrid weighting adapts to the signals available")
    class AdaptiveWeighting {

        private final HybridRecommendationStrategy engine =
                hybridStrategy(Map.of(20L, Map.of(1L, 5, 2L, 4, 6L, 5)));

        @Test
        @DisplayName("uses every component for a user with rich history")
        void richProfileUsesAllComponents() {
            UserTasteProfile rich = MovieFixtures.profileOf(
                    12L, Map.of(1L, 5, 2L, 5, 4L, 4), Set.of(SCIFI), Set.of());

            Map<RecommendationType, Double> weights = engine.resolveWeights(rich);

            assertThat(weights).containsKeys(
                    RecommendationType.CONTENT_BASED,
                    RecommendationType.COLLABORATIVE,
                    RecommendationType.POPULARITY);
            assertThat(weights.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1e-9));
        }

        @Test
        @DisplayName("falls back entirely to popularity for a blank profile")
        void blankProfileFallsBackToPopularity() {
            Map<RecommendationType, Double> weights =
                    engine.resolveWeights(UserTasteProfile.empty(13L));

            assertThat(weights).containsOnlyKeys(RecommendationType.POPULARITY);
            assertThat(weights.get(RecommendationType.POPULARITY)).isEqualTo(1.0d);
        }

        @Test
        @DisplayName("scales the content weight up when collaborative cannot contribute")
        void renormalisesWhenComponentsAreUnavailable() {
            UserTasteProfile rich = MovieFixtures.profileOf(
                    12L, Map.of(1L, 5, 2L, 5, 4L, 4), Set.of(SCIFI), Set.of());
            UserTasteProfile oneRating =
                    MovieFixtures.profileOf(14L, Map.of(1L, 5), Set.of(), Set.of());

            double baselineContent =
                    engine.resolveWeights(rich).get(RecommendationType.CONTENT_BASED);
            Map<RecommendationType, Double> weights = engine.resolveWeights(oneRating);

            assertThat(weights).containsKey(RecommendationType.CONTENT_BASED);
            assertThat(weights).doesNotContainKey(RecommendationType.COLLABORATIVE);
            assertThat(weights.get(RecommendationType.CONTENT_BASED))
                    .as("losing a component must not flatten the ranking towards popularity")
                    .isGreaterThan(baselineContent);
            assertThat(weights.values().stream().mapToDouble(Double::doubleValue).sum())
                    .isCloseTo(1.0d, org.assertj.core.data.Offset.offset(1e-9));
        }
    }

    @Nested
    @DisplayName("Explanations are derived from real scoring signals")
    class Explanations {

        @Test
        @DisplayName("names the actual matching entity rather than a generic phrase")
        void namesTheMatchingEntity() {
            UserTasteProfile profile = MovieFixtures.profileOf(
                    11L, Map.of(1L, 5, 2L, 5, 14L, 5), Set.of(), Set.of());

            List<ScoredMovie> ranked =
                    contentStrategy().score(profile, MovieFixtures.unseenBy(profile));
            ScoredMovie darkKnight = ranked.stream()
                    .filter(scored -> scored.movie().title().equals("The Dark Knight"))
                    .findFirst().orElseThrow();

            assertThat(ExplanationBuilder.describe(darkKnight, RecommendationType.HYBRID))
                    .contains("Christopher Nolan");
        }

        @Test
        @DisplayName("falls back honestly when there is no personal evidence")
        void fallsBackHonestly() {
            ScoredMovie bare = new ScoredMovie(MovieFixtures.byId(11L), 0.1);

            assertThat(ExplanationBuilder.describe(bare, RecommendationType.COLD_START))
                    .isEqualTo("A well-loved title to get you started");
            assertThat(ExplanationBuilder.describe(bare, RecommendationType.POPULARITY))
                    .isEqualTo("Popular with viewers right now");
        }

        @Test
        @DisplayName("provides wording for every recommendation type")
        void coversEveryType() {
            ScoredMovie bare = new ScoredMovie(MovieFixtures.byId(11L), 0.1);

            for (RecommendationType type : RecommendationType.values()) {
                assertThat(ExplanationBuilder.describe(bare, type))
                        .as("fallback for %s", type)
                        .isNotBlank();
            }
        }
    }
}
