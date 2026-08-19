package com.cinevault.recommendation.support;

import com.cinevault.recommendation.model.MovieFeatures;
import com.cinevault.recommendation.model.UserTasteProfile;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Deterministic catalogue and profile builders for the recommendation tests.
 *
 * <p>Centralised so every test reasons about the same films, which makes the
 * expected orderings in the assertions verifiable by hand.
 */
public final class MovieFixtures {

    // Genre identifiers
    public static final long SCIFI = 1;
    public static final long THRILLER = 2;
    public static final long DRAMA = 3;
    public static final long ANIMATION = 4;
    public static final long COMEDY = 5;
    public static final long CRIME = 6;
    public static final long ROMANCE = 7;
    public static final long HORROR = 8;
    public static final long FAMILY_GENRE = 9;

    // People identifiers
    public static final long NOLAN = 100;
    public static final long VILLENEUVE = 101;
    public static final long MIYAZAKI = 102;
    public static final long FINCHER = 103;
    public static final long MCCONAUGHEY = 200;
    public static final long GOSLING = 201;
    public static final long BALE = 202;
    public static final long PITT = 203;

    // Keyword identifiers
    public static final long SPACE = 300;
    public static final long DREAM = 301;
    public static final long DYSTOPIA = 302;
    public static final long HEIST = 303;
    public static final long FAMILY_KEYWORD = 304;

    public static final Map<Long, String> GENRE_NAMES = Map.of(
            SCIFI, "Science Fiction", THRILLER, "Thriller", DRAMA, "Drama",
            ANIMATION, "Animation", COMEDY, "Comedy", CRIME, "Crime",
            ROMANCE, "Romance", HORROR, "Horror", FAMILY_GENRE, "Family");

    public static final Map<Long, String> PERSON_NAMES = Map.of(
            NOLAN, "Christopher Nolan", VILLENEUVE, "Denis Villeneuve",
            MIYAZAKI, "Hayao Miyazaki", FINCHER, "David Fincher",
            MCCONAUGHEY, "Matthew McConaughey", GOSLING, "Ryan Gosling",
            BALE, "Christian Bale", PITT, "Brad Pitt");

    public static final Map<Long, String> KEYWORD_NAMES = Map.of(
            SPACE, "space travel", DREAM, "dreams", DYSTOPIA, "dystopia",
            HEIST, "heists", FAMILY_KEYWORD, "family");

    private static final Map<Long, MovieFeatures> CATALOGUE = new LinkedHashMap<>();

    private MovieFixtures() {
    }

    private static void film(long id, String title, Set<Long> genres, Set<Long> keywords,
                             Set<Long> cast, Set<Long> directors, String language,
                             String release, double rating, int votes, double popularity) {
        CATALOGUE.put(id, new MovieFeatures(id, title, genres, keywords, cast, directors,
                Set.of(), language, LocalDate.parse(release), rating, votes, 0d, 0,
                popularity, false));
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
        film(7, "Spirited Away", Set.of(ANIMATION, FAMILY_GENRE), Set.of(FAMILY_KEYWORD), Set.of(),
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

    public static List<MovieFeatures> catalogue() {
        return new ArrayList<>(CATALOGUE.values());
    }

    public static MovieFeatures byId(long id) {
        return CATALOGUE.get(id);
    }

    /**
     * Builds a taste profile the way {@code TasteProfileAssembler} does, so unit
     * tests exercise realistic affinity values rather than invented ones.
     */
    public static UserTasteProfile profileOf(long userId, Map<Long, Integer> ratings,
                                             Set<Long> declaredGenres, Set<Long> watchlist) {
        Map<Long, Double> genreRaw = new HashMap<>();
        Map<Long, Double> keywordRaw = new HashMap<>();
        Map<Long, Double> castRaw = new HashMap<>();
        Map<Long, Double> directorRaw = new HashMap<>();
        Map<String, Double> languageRaw = new HashMap<>();
        Set<Long> liked = new HashSet<>();

        ratings.forEach((movieId, score) -> {
            MovieFeatures movie = byId(movieId);
            if (movie == null) {
                return;
            }
            if (score >= UserTasteProfile.LIKED_THRESHOLD) {
                liked.add(movieId);
            }
            // Only positive ratings contribute affinity, mirroring the SQL.
            double weight = (score - 3) / 2.0d;
            if (weight <= 0) {
                return;
            }
            movie.genreIds().forEach(id -> genreRaw.merge(id, weight, Double::sum));
            movie.keywordIds().forEach(id -> keywordRaw.merge(id, weight, Double::sum));
            movie.castIds().forEach(id -> castRaw.merge(id, weight, Double::sum));
            movie.directorIds().forEach(id -> directorRaw.merge(id, weight, Double::sum));
            if (movie.language() != null) {
                languageRaw.merge(movie.language(), weight, Double::sum);
            }
        });
        declaredGenres.forEach(id -> genreRaw.merge(id, 1.0d, Double::sum));

        return new UserTasteProfile(userId, ratings,
                UserTasteProfile.normalised(genreRaw),
                UserTasteProfile.normalised(keywordRaw),
                UserTasteProfile.normalised(castRaw),
                UserTasteProfile.normalised(directorRaw),
                Map.of(),
                UserTasteProfile.normalised(languageRaw),
                declaredGenres, ratings.keySet(), watchlist, liked, Set.of(), 0d, false);
    }

    /** Candidates excluding anything the user has already rated. */
    public static List<MovieFeatures> unseenBy(UserTasteProfile profile) {
        return catalogue().stream()
                .filter(movie -> !profile.ratings().containsKey(movie.movieId()))
                .toList();
    }
}
