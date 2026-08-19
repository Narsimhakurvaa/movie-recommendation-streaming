package tools;

import com.cinevault.recommendation.explain.ExplanationBuilder;
import com.cinevault.recommendation.model.MovieFeatures;
import com.cinevault.recommendation.model.RecommendationType;
import com.cinevault.recommendation.model.ScoredMovie;
import com.cinevault.recommendation.model.UserTasteProfile;
import com.cinevault.recommendation.scoring.SimilarityFunctions;
import com.cinevault.recommendation.strategy.ColdStartRecommendationStrategy;
import com.cinevault.recommendation.strategy.CollaborativeRecommendationStrategy;
import com.cinevault.recommendation.strategy.ContentBasedRecommendationStrategy;
import com.cinevault.recommendation.strategy.HybridRecommendationStrategy;
import com.cinevault.recommendation.strategy.PopularityRecommendationStrategy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.LongFunction;

/**
 * Executable verification of the recommendation engine, mirroring the JUnit
 * suite in {@code backend/src/test/java}. Runs the real strategy classes with a
 * fixed clock and a deterministic in-memory catalogue.
 *
 * <p>Covers the eight scenarios the engine must handle: new user, single
 * rating, many ratings, declared genre preferences, watch history, no matching
 * content, similar-movie lookup, and popularity fallback.
 */
public final class RecommendationEngineVerification {

    // Fixed clock so recency scoring is reproducible.
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-08-18T00:00:00Z"), ZoneOffset.UTC);

    // --- Genre ids ---------------------------------------------------------
    private static final long SCIFI = 1, THRILLER = 2, DRAMA = 3, ANIMATION = 4,
            COMEDY = 5, CRIME = 6, ROMANCE = 7, HORROR = 8;
    // --- People ids --------------------------------------------------------
    private static final long NOLAN = 100, VILLENEUVE = 101, MIYAZAKI = 102, FINCHER = 103;
    private static final long MCCONAUGHEY = 200, GOSLING = 201, BALE = 202, PITT = 203;
    // --- Keyword ids -------------------------------------------------------
    private static final long SPACE = 300, DREAM = 301, DYSTOPIA = 302, HEIST = 303, FAMILY = 304;

    private static final Map<Long, String> GENRE_NAMES = Map.of(
            SCIFI, "Science Fiction", THRILLER, "Thriller", DRAMA, "Drama",
            ANIMATION, "Animation", COMEDY, "Comedy", CRIME, "Crime",
            ROMANCE, "Romance", HORROR, "Horror");
    private static final Map<Long, String> PERSON_NAMES = Map.of(
            NOLAN, "Christopher Nolan", VILLENEUVE, "Denis Villeneuve",
            MIYAZAKI, "Hayao Miyazaki", FINCHER, "David Fincher",
            MCCONAUGHEY, "Matthew McConaughey", GOSLING, "Ryan Gosling",
            BALE, "Christian Bale", PITT, "Brad Pitt");
    private static final Map<Long, String> KEYWORD_NAMES = Map.of(
            SPACE, "space travel", DREAM, "dreams", DYSTOPIA, "dystopia",
            HEIST, "heists", FAMILY, "family");

    private static final LongFunction<String> GENRE_LOOKUP = GENRE_NAMES::get;
    private static final LongFunction<String> PERSON_LOOKUP = PERSON_NAMES::get;
    private static final LongFunction<String> KEYWORD_LOOKUP = KEYWORD_NAMES::get;

    private static final Map<Long, MovieFeatures> CATALOGUE = new LinkedHashMap<>();

    private static MovieFeatures film(long id, String title, Set<Long> genres, Set<Long> keywords,
                                      Set<Long> cast, Set<Long> directors, String lang,
                                      String release, double rating, int votes, double popularity) {
        MovieFeatures m = new MovieFeatures(id, title, genres, keywords, cast, directors,
                Set.of(), lang, LocalDate.parse(release), rating, votes, 0d, 0, popularity, false);
        CATALOGUE.put(id, m);
        return m;
    }

    static {
        film(1, "Interstellar", Set.of(SCIFI, DRAMA), Set.of(SPACE), Set.of(MCCONAUGHEY),
                Set.of(NOLAN), "en", "2014-11-07", 8.4, 34000, 92.5);
        film(2, "Inception", Set.of(SCIFI, THRILLER), Set.of(DREAM, HEIST), Set.of(BALE),
                Set.of(NOLAN), "en", "2010-07-16", 8.4, 36000, 95.1);
        film(3, "The Dark Knight", Set.of(THRILLER, CRIME, DRAMA), Set.of(), Set.of(BALE),
                Set.of(NOLAN), "en", "2008-07-18", 8.5, 32000, 97.3);
        film(4, "Blade Runner 2049", Set.of(SCIFI, DRAMA), Set.of(DYSTOPIA), Set.of(GOSLING),
                Set.of(VILLENEUVE), "en", "2017-10-06", 7.6, 14000, 68.2);
        film(5, "Arrival", Set.of(SCIFI, DRAMA), Set.of(SPACE), Set.of(),
                Set.of(VILLENEUVE), "en", "2016-11-11", 7.6, 19000, 71.4);
        film(6, "Dune", Set.of(SCIFI, DRAMA), Set.of(SPACE, DYSTOPIA), Set.of(),
                Set.of(VILLENEUVE), "en", "2021-09-15", 7.8, 12000, 88.9);
        film(7, "Spirited Away", Set.of(ANIMATION, FAMILY_G()), Set.of(FAMILY), Set.of(),
                Set.of(MIYAZAKI), "ja", "2001-07-20", 8.5, 16000, 79.6);
        film(8, "Se7en", Set.of(CRIME, THRILLER), Set.of(), Set.of(PITT),
                Set.of(FINCHER), "en", "1995-09-22", 8.4, 19000, 63.7);
        film(9, "Fight Club", Set.of(DRAMA, THRILLER), Set.of(), Set.of(PITT),
                Set.of(FINCHER), "en", "1999-10-15", 8.4, 28000, 72.4);
        film(10, "A Quiet Romance", Set.of(ROMANCE, COMEDY), Set.of(), Set.of(),
                Set.of(), "fr", "2013-02-14", 6.2, 900, 12.4);
        film(11, "Obscure Horror", Set.of(HORROR), Set.of(), Set.of(),
                Set.of(), "es", "1988-10-31", 5.1, 40, 3.2);
        film(12, "Brand New Blockbuster", Set.of(SCIFI, THRILLER), Set.of(SPACE), Set.of(),
                Set.of(), "en", "2026-06-01", 7.9, 2500, 96.0);
        film(13, "Ancient Classic", Set.of(DRAMA), Set.of(), Set.of(),
                Set.of(), "en", "1954-03-01", 8.6, 8000, 21.0);
        film(14, "The Prestige", Set.of(DRAMA, THRILLER, SCIFI), Set.of(), Set.of(BALE),
                Set.of(NOLAN), "en", "2006-10-20", 8.2, 14000, 59.6);
    }

    // ANIMATION-adjacent family genre reuses the animation id space.
    private static long FAMILY_G() {
        return 9L;
    }

    private static List<MovieFeatures> catalogue() {
        return new ArrayList<>(CATALOGUE.values());
    }

    private static MovieFeatures byId(long id) {
        return CATALOGUE.get(id);
    }

    // --- Strategy assembly -------------------------------------------------
    private static ContentBasedRecommendationStrategy content() {
        return new ContentBasedRecommendationStrategy(GENRE_LOOKUP, PERSON_LOOKUP, KEYWORD_LOOKUP);
    }

    private static PopularityRecommendationStrategy popularity() {
        return new PopularityRecommendationStrategy(CLOCK);
    }

    private static CollaborativeRecommendationStrategy collaborative(
            Map<Long, Map<Long, Integer>> neighbours) {
        return new CollaborativeRecommendationStrategy(userId -> neighbours);
    }

    private static HybridRecommendationStrategy hybrid(Map<Long, Map<Long, Integer>> neighbours) {
        return new HybridRecommendationStrategy(content(), collaborative(neighbours), popularity());
    }

    /** Builds a profile, deriving affinities the way the service does. */
    private static UserTasteProfile profileOf(long userId, Map<Long, Integer> ratings,
                                              Set<Long> declaredGenres, Set<Long> watchlist) {
        Map<Long, Double> genreRaw = new HashMap<>();
        Map<Long, Double> keywordRaw = new HashMap<>();
        Map<Long, Double> castRaw = new HashMap<>();
        Map<Long, Double> directorRaw = new HashMap<>();
        Map<String, Double> langRaw = new HashMap<>();
        Set<Long> liked = new java.util.HashSet<>();

        ratings.forEach((movieId, score) -> {
            MovieFeatures m = byId(movieId);
            if (m == null) {
                return;
            }
            // Ratings below the midpoint contribute negatively.
            double weight = (score - 3) / 2.0d;
            if (score >= UserTasteProfile.LIKED_THRESHOLD) {
                liked.add(movieId);
            }
            if (weight <= 0) {
                return;
            }
            m.genreIds().forEach(g -> genreRaw.merge(g, weight, Double::sum));
            m.keywordIds().forEach(k -> keywordRaw.merge(k, weight, Double::sum));
            m.castIds().forEach(c -> castRaw.merge(c, weight, Double::sum));
            m.directorIds().forEach(d -> directorRaw.merge(d, weight, Double::sum));
            if (m.language() != null) {
                langRaw.merge(m.language(), weight, Double::sum);
            }
        });
        declaredGenres.forEach(g -> genreRaw.merge(g, 1.0d, Double::sum));

        return new UserTasteProfile(userId, ratings,
                UserTasteProfile.normalised(genreRaw),
                UserTasteProfile.normalised(keywordRaw),
                UserTasteProfile.normalised(castRaw),
                UserTasteProfile.normalised(directorRaw),
                Map.of(),
                UserTasteProfile.normalised(langRaw),
                declaredGenres, ratings.keySet(), watchlist, liked, Set.of(), 0d, false);
    }

    private static List<MovieFeatures> unseen(UserTasteProfile profile) {
        return catalogue().stream()
                .filter(m -> !profile.ratings().containsKey(m.movieId()))
                .toList();
    }

    private static String titleAt(List<ScoredMovie> ranked, int index) {
        return ranked.get(index).movie().title();
    }

    private static boolean containsTitle(List<ScoredMovie> ranked, String title, int withinTop) {
        return ranked.stream().limit(withinTop)
                .anyMatch(s -> s.movie().title().equals(title));
    }

    public static void main(String[] args) {
        similarityFunctions();
        scenarioNewUserNoSignals();
        scenarioColdStartWithGenres();
        scenarioSingleRating();
        scenarioMultipleRatings();
        scenarioWatchHistoryAndWatchlist();
        scenarioNoMatchingContent();
        scenarioCollaborative();
        scenarioSimilarMovies();
        scenarioPopularityFallback();
        scenarioExplanations();
        scenarioWeightRenormalisation();
        System.exit(MiniTest.summarise());
    }

    // ---------------------------------------------------------------- maths
    private static void similarityFunctions() {
        MiniTest.suite("Similarity primitives");

        MiniTest.assertClose("jaccard of identical sets is 1",
                1.0, SimilarityFunctions.jaccard(Set.of(1L, 2L), Set.of(1L, 2L)), 1e-9);
        MiniTest.assertClose("jaccard of disjoint sets is 0",
                0.0, SimilarityFunctions.jaccard(Set.of(1L), Set.of(2L)), 1e-9);
        MiniTest.assertClose("jaccard of half overlap",
                1.0 / 3.0, SimilarityFunctions.jaccard(Set.of(1L, 2L), Set.of(2L, 3L)), 1e-9);
        MiniTest.assertClose("jaccard with empty set is 0",
                0.0, SimilarityFunctions.jaccard(Set.of(), Set.of(1L)), 1e-9);
        MiniTest.assertTrue("jaccard penalises breadth",
                SimilarityFunctions.jaccard(Set.of(1L, 2L), Set.of(1L, 2L))
                        > SimilarityFunctions.jaccard(Set.of(1L, 2L), Set.of(1L, 2L, 3L, 4L, 5L)));

        MiniTest.assertClose("cosine of identical vectors is 1", 1.0,
                SimilarityFunctions.cosine(Map.of(1L, 1.0, 2L, 2.0), Map.of(1L, 1.0, 2L, 2.0)), 1e-9);
        MiniTest.assertClose("cosine of orthogonal vectors is 0", 0.0,
                SimilarityFunctions.cosine(Map.of(1L, 1.0), Map.of(2L, 1.0)), 1e-9);
        MiniTest.assertTrue("cosine detects opposing opinions",
                SimilarityFunctions.cosine(Map.of(1L, 1.0), Map.of(1L, -1.0)) < 0);

        MiniTest.assertClose("bayesian average with no votes returns prior",
                6.5, SimilarityFunctions.bayesianAverage(10.0, 0, 6.5, 1000), 1e-9);
        MiniTest.assertTrue("bayesian average shrinks a single perfect vote",
                SimilarityFunctions.bayesianAverage(10.0, 1, 6.5, 1000) < 6.6);
        MiniTest.assertTrue("bayesian average trusts a large sample",
                SimilarityFunctions.bayesianAverage(8.5, 30000, 6.5, 1000) > 8.4);
        MiniTest.assertTrue("many votes beat one perfect vote",
                SimilarityFunctions.bayesianAverage(8.5, 30000, 6.5, 1000)
                        > SimilarityFunctions.bayesianAverage(10.0, 1, 6.5, 1000));

        MiniTest.assertClose("decay at age zero is 1",
                1.0, SimilarityFunctions.exponentialDecay(0, 730), 1e-9);
        MiniTest.assertClose("decay at one half-life is 0.5",
                0.5, SimilarityFunctions.exponentialDecay(730, 730), 1e-9);
        MiniTest.assertTrue("decay is monotonic",
                SimilarityFunctions.exponentialDecay(100, 730)
                        > SimilarityFunctions.exponentialDecay(2000, 730));
        MiniTest.assertClose("unreleased films are not penalised",
                1.0, SimilarityFunctions.exponentialDecay(-50, 730), 1e-9);
        MiniTest.assertThrows("non-positive half-life is rejected",
                IllegalArgumentException.class,
                () -> SimilarityFunctions.exponentialDecay(1, 0));

        MiniTest.assertClose("clamp01 floors negatives", 0.0, SimilarityFunctions.clamp01(-3), 1e-9);
        MiniTest.assertClose("clamp01 caps above one", 1.0, SimilarityFunctions.clamp01(4), 1e-9);
        MiniTest.assertClose("clamp01 maps NaN to zero", 0.0, SimilarityFunctions.clamp01(Double.NaN), 1e-9);
        MiniTest.assertInRange("logNormalise stays in range",
                SimilarityFunctions.logNormalise(9999, 50), 0, 1);

        Map<Long, Double> affinity = Map.of(1L, 1.0, 2L, 0.2);
        MiniTest.assertTrue("peak affinity exceeds mean for uneven input",
                SimilarityFunctions.peakAffinity(List.of(1L, 2L), affinity)
                        > SimilarityFunctions.meanAffinity(List.of(1L, 2L), affinity));
    }

    // ------------------------------------------------- scenario 1: new user
    private static void scenarioNewUserNoSignals() {
        MiniTest.suite("Scenario 1: brand-new user with no signals at all");

        UserTasteProfile profile = UserTasteProfile.empty(1L);
        MiniTest.assertTrue("profile reports cold start", profile.isColdStart());
        MiniTest.assertTrue("profile reports no signals", profile.hasNoSignals());

        var coldStart = new ColdStartRecommendationStrategy(popularity(), GENRE_LOOKUP);
        List<ScoredMovie> ranked = coldStart.score(profile, catalogue());

        MiniTest.assertTrue("cold start never returns an empty page", !ranked.isEmpty());
        MiniTest.assertEquals("every catalogue entry is scored", catalogue().size(), ranked.size());
        MiniTest.assertTrue("scores stay within [0,1]",
                ranked.stream().allMatch(s -> s.score() >= 0 && s.score() <= 1));
        MiniTest.assertTrue("obscure low-quality title is not first",
                !titleAt(ranked, 0).equals("Obscure Horror"));

        // Diversity: the top of the list must not be monopolised by one genre.
        long distinctGenresInTop6 = ranked.stream().limit(6)
                .flatMap(s -> s.movie().genreIds().stream()).distinct().count();
        MiniTest.assertTrue("cold-start results are diversified across genres",
                distinctGenresInTop6 >= 4);

        // Every recommendation must still carry a usable explanation.
        MiniTest.assertTrue("cold-start entries all have explanations",
                ranked.stream().allMatch(s ->
                        !ExplanationBuilder.describe(s, RecommendationType.COLD_START).isBlank()));
    }

    // --------------------------------------- scenario 2: onboarding genres
    private static void scenarioColdStartWithGenres() {
        MiniTest.suite("Scenario 2: new user who declared favourite genres");

        UserTasteProfile animationFan = new UserTasteProfile(2L, Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Set.of(ANIMATION), Set.of(), Set.of(),
                Set.of(), Set.of(), 0d, false);
        MiniTest.assertTrue("still classified as cold start", animationFan.isColdStart());
        MiniTest.assertTrue("but does have a signal", !animationFan.hasNoSignals());

        var coldStart = new ColdStartRecommendationStrategy(popularity(), GENRE_LOOKUP);
        List<ScoredMovie> ranked = coldStart.score(animationFan, catalogue());

        MiniTest.assertEquals("declared genre wins the top slot", "Spirited Away", titleAt(ranked, 0));
        MiniTest.assertTrue("explanation cites the onboarding choice",
                ExplanationBuilder.describe(ranked.get(0), RecommendationType.COLD_START)
                        .contains("Animation"));

        // A declared genre must beat a merely popular title from another genre.
        UserTasteProfile romanceFan = new UserTasteProfile(3L, Map.of(), Map.of(), Map.of(),
                Map.of(), Map.of(), Map.of(), Map.of(), Set.of(ROMANCE), Set.of(), Set.of(),
                Set.of(), Set.of(), 0d, false);
        List<ScoredMovie> romanceRanked = coldStart.score(romanceFan, catalogue());
        MiniTest.assertEquals("romance preference outranks blockbusters",
                "A Quiet Romance", titleAt(romanceRanked, 0));
        MiniTest.assertTrue("a niche pick still beats an unrelated hit",
                romanceRanked.get(0).score()
                        > romanceRanked.stream()
                        .filter(s -> s.movie().title().equals("Brand New Blockbuster"))
                        .findFirst().orElseThrow().score());
    }

    // ------------------------------------------- scenario 3: single rating
    private static void scenarioSingleRating() {
        MiniTest.suite("Scenario 3: user with exactly one rating");

        UserTasteProfile profile = profileOf(4L, Map.of(1L, 5), Set.of(), Set.of());
        MiniTest.assertTrue("one rating is still cold start", profile.isColdStart());
        MiniTest.assertTrue("content strategy can already contribute",
                content().supports(profile));
        MiniTest.assertTrue("collaborative strategy cannot yet contribute",
                !collaborative(Map.of()).supports(profile));

        List<ScoredMovie> ranked = content().score(profile, unseen(profile));
        MiniTest.assertTrue("content strategy produces results from one rating", !ranked.isEmpty());
        ranked.sort(ScoredMovie.byScoreDescending());

        // Interstellar is sci-fi/drama, directed by Nolan, keyword "space".
        MiniTest.assertTrue("another Nolan film surfaces in the top 3",
                containsTitle(ranked, "The Prestige", 3)
                        || containsTitle(ranked, "Inception", 3));
        MiniTest.assertTrue("a same-genre same-keyword film ranks highly",
                containsTitle(ranked, "Arrival", 5) || containsTitle(ranked, "Dune", 5));
        MiniTest.assertTrue("unrelated romance does not reach the top 3",
                !containsTitle(ranked, "A Quiet Romance", 3));
        MiniTest.assertTrue("the rated film itself is excluded",
                ranked.stream().noneMatch(s -> s.movieId() == 1L));
    }

    // --------------------------------------- scenario 4: multiple ratings
    private static void scenarioMultipleRatings() {
        MiniTest.suite("Scenario 4: user with a rich rating history");

        // A committed Nolan / hard sci-fi viewer.
        Map<Long, Integer> ratings = new LinkedHashMap<>();
        ratings.put(1L, 5);   // Interstellar
        ratings.put(2L, 5);   // Inception
        ratings.put(3L, 4);   // The Dark Knight
        ratings.put(14L, 5);  // The Prestige
        ratings.put(10L, 1);  // A Quiet Romance - actively disliked
        UserTasteProfile profile = profileOf(5L, ratings, Set.of(), Set.of());

        MiniTest.assertTrue("no longer cold start", !profile.isColdStart());
        MiniTest.assertTrue("director affinity was learned",
                profile.directorAffinity().getOrDefault(NOLAN, 0d) > 0.9);
        MiniTest.assertTrue("disliked genres earn no affinity",
                profile.genreAffinity().getOrDefault(ROMANCE, 0d) == 0d);

        List<ScoredMovie> ranked = content().score(profile, unseen(profile));
        ranked.sort(ScoredMovie.byScoreDescending());

        MiniTest.assertTrue("sci-fi dominates the top of the list",
                ranked.stream().limit(3).allMatch(s -> s.movie().genreIds().contains(SCIFI)));
        MiniTest.assertTrue("the disliked romance is ranked last or absent",
                ranked.stream().noneMatch(s -> s.movie().title().equals("A Quiet Romance"))
                        || titleAt(ranked, ranked.size() - 1).equals("A Quiet Romance"));

        double topScore = ranked.get(0).score();
        MiniTest.assertInRange("top score is a valid normalised value", topScore, 0, 1);
        MiniTest.assertTrue("a strong match scores meaningfully above zero", topScore > 0.2);
    }

    // ------------------------------- scenario 5: history + watchlist boost
    private static void scenarioWatchHistoryAndWatchlist() {
        MiniTest.suite("Scenario 5: watch history and watchlist influence ranking");

        Map<Long, Integer> ratings = Map.of(1L, 5, 2L, 5, 5L, 4);
        UserTasteProfile withoutWatchlist = profileOf(6L, ratings, Set.of(), Set.of());
        UserTasteProfile withWatchlist = profileOf(6L, ratings, Set.of(), Set.of(6L)); // Dune saved

        var engine = hybrid(Map.of());
        List<ScoredMovie> plain = engine.score(withoutWatchlist, unseen(withoutWatchlist));
        List<ScoredMovie> boosted = engine.score(withWatchlist, unseen(withWatchlist));

        double plainDune = plain.stream().filter(s -> s.movieId() == 6L)
                .findFirst().orElseThrow().score();
        double boostedDune = boosted.stream().filter(s -> s.movieId() == 6L)
                .findFirst().orElseThrow().score();
        MiniTest.assertTrue("a watchlisted film scores higher than when unsaved",
                boostedDune > plainDune);

        MiniTest.assertTrue("already-rated films are never recommended back",
                boosted.stream().noneMatch(s -> ratings.containsKey(s.movieId())));
        MiniTest.assertTrue("all hybrid scores remain normalised",
                boosted.stream().allMatch(s -> s.score() >= 0 && s.score() <= 1));
    }

    // ----------------------------------- scenario 6: no matching content
    private static void scenarioNoMatchingContent() {
        MiniTest.suite("Scenario 6: user whose taste matches nothing in the catalogue");

        // Affinity for genres and people that no catalogue film possesses.
        UserTasteProfile alien = new UserTasteProfile(7L, Map.of(999L, 5),
                Map.of(4242L, 1.0), Map.of(4243L, 1.0), Map.of(4244L, 1.0),
                Map.of(4245L, 1.0), Map.of(), Map.of("xx", 1.0),
                Set.of(), Set.of(999L), Set.of(), Set.of(999L), Set.of(), 0d, false);

        List<ScoredMovie> contentRanked = content().score(alien, catalogue());
        MiniTest.assertTrue("content strategy yields nothing for an unmatched profile",
                contentRanked.isEmpty());

        // The hybrid must still return a full, useful page via the popularity floor.
        List<ScoredMovie> hybridRanked = hybrid(Map.of()).score(alien, catalogue());
        MiniTest.assertTrue("hybrid still returns results", !hybridRanked.isEmpty());
        MiniTest.assertEquals("hybrid scores the whole catalogue",
                catalogue().size(), hybridRanked.size());
        MiniTest.assertTrue("results are ordered by descending score",
                isDescending(hybridRanked));
        MiniTest.assertTrue("fallback results still carry explanations",
                hybridRanked.stream().limit(5).allMatch(s ->
                        !ExplanationBuilder.describe(s, RecommendationType.HYBRID).isBlank()));
    }

    // ------------------------------------- scenario 7: collaborative signal
    private static void scenarioCollaborative() {
        MiniTest.suite("Scenario 7: collaborative filtering from similar users");

        // Target loves 1 and 2, is lukewarm on 4, and has never seen Dune (6).
        Map<Long, Integer> mine = Map.of(1L, 5, 2L, 5, 4L, 3);
        UserTasteProfile profile = profileOf(8L, mine, Set.of(), Set.of());

        // Three neighbours who genuinely correlate with that shape (cosine
        // 0.94 / 0.83 / 0.94 on the mean-centred vectors) and all rated Dune
        // highly, plus one with opposing taste that must be excluded.
        // Each must show rating VARIANCE, otherwise they mean-centre to the
        // zero vector and are (correctly) skipped as expressing no preference.
        Map<Long, Map<Long, Integer>> neighbours = new LinkedHashMap<>();
        neighbours.put(20L, Map.of(1L, 5, 2L, 5, 4L, 3, 6L, 5));
        neighbours.put(21L, Map.of(1L, 5, 2L, 4, 4L, 2, 6L, 5));
        neighbours.put(23L, Map.of(1L, 4, 2L, 5, 4L, 2, 6L, 4));
        neighbours.put(22L, Map.of(1L, 2, 2L, 1, 10L, 5));  // opposite taste

        var strategy = collaborative(neighbours);
        MiniTest.assertTrue("collaborative applies with enough ratings", strategy.supports(profile));

        List<ScoredMovie> ranked = strategy.score(profile, unseen(profile));
        ranked.sort(ScoredMovie.byScoreDescending());

        MiniTest.assertTrue("collaborative produces results", !ranked.isEmpty());
        MiniTest.assertEquals("neighbours' favourite surfaces first", "Dune", titleAt(ranked, 0));
        MiniTest.assertTrue("already-rated titles are excluded",
                ranked.stream().noneMatch(s -> mine.containsKey(s.movieId())));
        MiniTest.assertTrue("the opposite-taste user's pick ranks below",
                ranked.stream().filter(s -> s.movie().title().equals("A Quiet Romance"))
                        .findFirst().map(s -> s.score() < ranked.get(0).score()).orElse(true));
        MiniTest.assertTrue("explanation references similar viewers",
                ExplanationBuilder.describe(ranked.get(0), RecommendationType.COLLABORATIVE)
                        .toLowerCase().contains("similar taste"));

        // Confidence damping: the SAME neighbour alone must yield a weaker
        // score than the full set, isolating neighbour COUNT as the variable.
        List<ScoredMovie> single = collaborative(Map.of(20L, Map.of(1L, 5, 2L, 5, 4L, 3, 6L, 5)))
                .score(profile, unseen(profile));
        double oneNeighbour = single.stream().filter(s -> s.movieId() == 6L)
                .findFirst().orElseThrow().score();
        MiniTest.assertTrue("more neighbours means higher confidence",
                ranked.get(0).score() > oneNeighbour);

        // A neighbour who rates everything identically expresses no preference
        // and must be ignored rather than crashing the cosine computation.
        List<ScoredMovie> flatOnly = collaborative(Map.of(30L, Map.of(1L, 3, 2L, 3, 6L, 3)))
                .score(profile, unseen(profile));
        MiniTest.assertTrue("a zero-variance neighbour is ignored", flatOnly.isEmpty());

        // Likewise for the target user themselves rating everything the same.
        UserTasteProfile flatUser = profileOf(15L, Map.of(1L, 4, 2L, 4, 4L, 4), Set.of(), Set.of());
        MiniTest.assertTrue("a zero-variance target user yields no collaborative results",
                collaborative(neighbours).score(flatUser, unseen(flatUser)).isEmpty());

        // Too few ratings must disable the strategy entirely.
        UserTasteProfile sparse = profileOf(9L, Map.of(1L, 5), Set.of(), Set.of());
        MiniTest.assertTrue("sparse profile disables collaborative",
                !strategy.supports(sparse));
        MiniTest.assertTrue("sparse profile yields no collaborative results",
                strategy.score(sparse, catalogue()).isEmpty());
    }

    // ------------------------------------------ scenario 8: similar movies
    private static void scenarioSimilarMovies() {
        MiniTest.suite("Scenario 8: similar-movie lookup");

        var engine = hybrid(Map.of());
        MovieFeatures seed = byId(1L); // Interstellar
        List<ScoredMovie> similar = engine.findSimilar(
                seed, catalogue(), GENRE_LOOKUP, PERSON_LOOKUP);

        MiniTest.assertTrue("similar returns results", !similar.isEmpty());
        MiniTest.assertTrue("the seed film excludes itself",
                similar.stream().noneMatch(s -> s.movieId() == seed.movieId()));
        MiniTest.assertTrue("results are ordered by descending score", isDescending(similar));
        MiniTest.assertTrue("a same-genre same-keyword film leads",
                containsTitle(similar, "Arrival", 3) || containsTitle(similar, "Dune", 3));
        MiniTest.assertTrue("unrelated horror does not reach the top 3",
                !containsTitle(similar, "Obscure Horror", 3));

        // Shared director must be surfaced as the explanation where applicable.
        ScoredMovie prestige = similar.stream()
                .filter(s -> s.movie().title().equals("The Prestige")).findFirst().orElseThrow();
        MiniTest.assertTrue("shared-director explanation names the director",
                ExplanationBuilder.describe(prestige, RecommendationType.SIMILAR)
                        .contains("Christopher Nolan"));

        // The endpoint consults no user profile, so it works for anonymous
        // visitors. Seeded with a film that shares genres with others.
        MiniTest.assertTrue("similar works for anonymous visitors (no profile used)",
                !engine.findSimilar(byId(8L), catalogue(), GENRE_LOOKUP, PERSON_LOOKUP).isEmpty());

        // A film sharing no genre, keyword, cast, director or language with any
        // other entry legitimately has no similar titles; the API must return an
        // empty list rather than fabricating weak matches.
        MiniTest.assertTrue("a metadata-isolated film yields no forced matches",
                engine.findSimilar(byId(7L), catalogue(), GENRE_LOOKUP, PERSON_LOOKUP).isEmpty());
    }

    // ------------------------------------- scenario 9: popularity fallback
    private static void scenarioPopularityFallback() {
        MiniTest.suite("Scenario 9: popularity fallback and quality weighting");

        var strategy = popularity();
        List<ScoredMovie> ranked = strategy.score(UserTasteProfile.empty(10L), catalogue());
        ranked.sort(ScoredMovie.byScoreDescending());

        MiniTest.assertTrue("popularity always applies",
                strategy.supports(UserTasteProfile.empty(10L)));
        MiniTest.assertEquals("scores the entire catalogue", catalogue().size(), ranked.size());
        MiniTest.assertTrue("all scores normalised",
                ranked.stream().allMatch(s -> s.score() >= 0 && s.score() <= 1));

        double obscure = ranked.stream().filter(s -> s.movie().title().equals("Obscure Horror"))
                .findFirst().orElseThrow().score();
        double blockbuster = ranked.stream()
                .filter(s -> s.movie().title().equals("Brand New Blockbuster"))
                .findFirst().orElseThrow().score();
        MiniTest.assertTrue("a poorly-rated obscure film ranks below a strong recent hit",
                obscure < blockbuster);

        double ancient = ranked.stream().filter(s -> s.movie().title().equals("Ancient Classic"))
                .findFirst().orElseThrow().score();
        MiniTest.assertTrue("recency separates a 2026 hit from a 1954 classic",
                blockbuster > ancient);
        MiniTest.assertTrue("but an acclaimed classic still scores respectably", ancient > 0.3);
    }

    // ------------------------------------------------------- explanations
    private static void scenarioExplanations() {
        MiniTest.suite("Explanations are derived from real signals");

        Map<Long, Integer> ratings = Map.of(1L, 5, 2L, 5, 14L, 5);
        UserTasteProfile profile = profileOf(11L, ratings, Set.of(), Set.of());
        List<ScoredMovie> ranked = content().score(profile, unseen(profile));
        ranked.sort(ScoredMovie.byScoreDescending());

        for (ScoredMovie scored : ranked) {
            String reason = ExplanationBuilder.describe(scored, RecommendationType.HYBRID);
            MiniTest.assertTrue("explanation for " + scored.movie().title() + " is non-empty",
                    reason != null && !reason.isBlank());
        }

        // The engine must name the actual matching entity, not a generic phrase.
        ScoredMovie darkKnight = ranked.stream()
                .filter(s -> s.movie().title().equals("The Dark Knight")).findFirst().orElseThrow();
        String reason = ExplanationBuilder.describe(darkKnight, RecommendationType.HYBRID);
        MiniTest.assertTrue("Nolan film explanation names Nolan",
                reason.contains("Christopher Nolan"));

        // An empty-evidence candidate must still get an honest fallback.
        ScoredMovie bare = new ScoredMovie(byId(11L), 0.1);
        MiniTest.assertTrue("cold-start fallback wording is used",
                ExplanationBuilder.describe(bare, RecommendationType.COLD_START)
                        .equals("A well-loved title to get you started"));
        MiniTest.assertTrue("popularity fallback wording is used",
                ExplanationBuilder.describe(bare, RecommendationType.POPULARITY)
                        .equals("Popular with viewers right now"));

        // Every recommendation type must have a fallback sentence.
        for (RecommendationType type : RecommendationType.values()) {
            MiniTest.assertTrue("fallback exists for " + type,
                    !ExplanationBuilder.describe(bare, type).isBlank());
        }
    }

    // ------------------------------------------- adaptive weight behaviour
    private static void scenarioWeightRenormalisation() {
        MiniTest.suite("Hybrid weights adapt to available signals");

        var engine = hybrid(Map.of(20L, Map.of(1L, 5, 2L, 5, 6L, 5)));

        UserTasteProfile rich = profileOf(12L, Map.of(1L, 5, 2L, 5, 4L, 4), Set.of(SCIFI), Set.of());
        Map<RecommendationType, Double> richWeights = engine.resolveWeights(rich);
        MiniTest.assertTrue("rich profile uses the content component",
                richWeights.containsKey(RecommendationType.CONTENT_BASED));
        MiniTest.assertTrue("rich profile uses the collaborative component",
                richWeights.containsKey(RecommendationType.COLLABORATIVE));
        MiniTest.assertClose("weights sum to one", 1.0,
                richWeights.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);

        UserTasteProfile blank = UserTasteProfile.empty(13L);
        Map<RecommendationType, Double> blankWeights = engine.resolveWeights(blank);
        MiniTest.assertTrue("blank profile drops the collaborative component",
                !blankWeights.containsKey(RecommendationType.COLLABORATIVE));
        MiniTest.assertTrue("blank profile drops the content component",
                !blankWeights.containsKey(RecommendationType.CONTENT_BASED));
        MiniTest.assertClose("blank profile falls back entirely to popularity", 1.0,
                blankWeights.get(RecommendationType.POPULARITY), 1e-9);
        MiniTest.assertClose("weights still sum to one", 1.0,
                blankWeights.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);

        UserTasteProfile oneRating = profileOf(14L, Map.of(1L, 5), Set.of(), Set.of());
        Map<RecommendationType, Double> sparseWeights = engine.resolveWeights(oneRating);
        MiniTest.assertTrue("one rating keeps content but drops collaborative",
                sparseWeights.containsKey(RecommendationType.CONTENT_BASED)
                        && !sparseWeights.containsKey(RecommendationType.COLLABORATIVE));
        MiniTest.assertTrue("content weight is scaled up to compensate",
                sparseWeights.get(RecommendationType.CONTENT_BASED) > 0.40);
        MiniTest.assertClose("weights sum to one after renormalisation", 1.0,
                sparseWeights.values().stream().mapToDouble(Double::doubleValue).sum(), 1e-9);
    }

    private static boolean isDescending(List<ScoredMovie> ranked) {
        for (int i = 1; i < ranked.size(); i++) {
            if (ranked.get(i - 1).score() < ranked.get(i).score()) {
                return false;
            }
        }
        return true;
    }
}
