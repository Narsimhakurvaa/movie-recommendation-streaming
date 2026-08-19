package com.cinevault.recommendation.strategy;

import com.cinevault.recommendation.model.MovieFeatures;
import com.cinevault.recommendation.model.RecommendationType;
import com.cinevault.recommendation.model.ScoredMovie;
import com.cinevault.recommendation.model.SignalContribution;
import com.cinevault.recommendation.model.SignalContribution.SignalKind;
import com.cinevault.recommendation.model.UserTasteProfile;
import com.cinevault.recommendation.scoring.SimilarityFunctions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.LongFunction;

/**
 * Scores films by how closely their metadata matches the attributes the user
 * has demonstrably enjoyed.
 *
 * <p>Rather than the naive "same genre" match, similarity is computed across
 * six independent facets, each contributing a documented share of the score:
 *
 * <table>
 *   <caption>Facet weights</caption>
 *   <tr><th>Facet</th><th>Weight</th><th>Why</th></tr>
 *   <tr><td>Genre</td><td>0.30</td>
 *       <td>The broadest and most reliable taste indicator.</td></tr>
 *   <tr><td>Keyword</td><td>0.22</td>
 *       <td>Captures theme and tone, which genre alone misses; this is what
 *           separates a cerebral sci-fi from a space opera.</td></tr>
 *   <tr><td>Director</td><td>0.20</td>
 *       <td>The strongest single-person signal - viewers follow directors far
 *           more consistently than they follow actors.</td></tr>
 *   <tr><td>Cast</td><td>0.15</td>
 *       <td>Meaningful but noisier; prolific actors appear everywhere.</td></tr>
 *   <tr><td>Writer</td><td>0.08</td>
 *       <td>Real but weak, and frequently overlaps with the director.</td></tr>
 *   <tr><td>Language</td><td>0.05</td>
 *       <td>A mild preference nudge, deliberately small so it never traps a
 *           user inside a single language.</td></tr>
 * </table>
 *
 * <p>Weights sum to 1.0, so the returned score is already normalised.
 */
public class ContentBasedRecommendationStrategy implements RecommendationStrategy {

    static final double GENRE_WEIGHT = 0.30;
    static final double KEYWORD_WEIGHT = 0.22;
    static final double DIRECTOR_WEIGHT = 0.20;
    static final double CAST_WEIGHT = 0.15;
    static final double WRITER_WEIGHT = 0.08;
    static final double LANGUAGE_WEIGHT = 0.05;

    /** Contributions below this add noise to explanations without adding value. */
    private static final double EVIDENCE_THRESHOLD = 0.05;

    private final LongFunction<String> genreNameLookup;
    private final LongFunction<String> personNameLookup;
    private final LongFunction<String> keywordNameLookup;

    /**
     * @param genreNameLookup   resolves a genre id to its display name
     * @param personNameLookup  resolves a person id to their name
     * @param keywordNameLookup resolves a keyword id to its label
     */
    public ContentBasedRecommendationStrategy(LongFunction<String> genreNameLookup,
                                              LongFunction<String> personNameLookup,
                                              LongFunction<String> keywordNameLookup) {
        this.genreNameLookup = genreNameLookup;
        this.personNameLookup = personNameLookup;
        this.keywordNameLookup = keywordNameLookup;
    }

    @Override
    public List<ScoredMovie> score(UserTasteProfile profile, Collection<MovieFeatures> candidates) {
        List<ScoredMovie> results = new ArrayList<>(candidates.size());
        for (MovieFeatures movie : candidates) {
            ScoredMovie scored = scoreOne(profile, movie);
            if (scored.score() > 0) {
                results.add(scored);
            }
        }
        return results;
    }

    private ScoredMovie scoreOne(UserTasteProfile profile, MovieFeatures movie) {
        ScoredMovie scored = new ScoredMovie(movie);
        double total = 0d;

        double genre = SimilarityFunctions.meanAffinity(movie.genreIds(), profile.genreAffinity());
        total += genre * GENRE_WEIGHT;
        recordTop(scored, movie.genreIds(), profile.genreAffinity(),
                SignalKind.SHARED_GENRE, genreNameLookup);

        double keyword = SimilarityFunctions.meanAffinity(movie.keywordIds(), profile.keywordAffinity());
        total += keyword * KEYWORD_WEIGHT;
        recordTop(scored, movie.keywordIds(), profile.keywordAffinity(),
                SignalKind.SHARED_KEYWORD, keywordNameLookup);

        // Peak, not mean: one beloved director is a strong signal on its own.
        double director = SimilarityFunctions.peakAffinity(movie.directorIds(), profile.directorAffinity());
        total += director * DIRECTOR_WEIGHT;
        recordTop(scored, movie.directorIds(), profile.directorAffinity(),
                SignalKind.SHARED_DIRECTOR, personNameLookup);

        double cast = SimilarityFunctions.meanAffinity(movie.castIds(), profile.castAffinity());
        total += cast * CAST_WEIGHT;
        recordTop(scored, movie.castIds(), profile.castAffinity(),
                SignalKind.SHARED_CAST, personNameLookup);

        double writer = SimilarityFunctions.peakAffinity(movie.writerIds(), profile.writerAffinity());
        total += writer * WRITER_WEIGHT;
        recordTop(scored, movie.writerIds(), profile.writerAffinity(),
                SignalKind.SHARED_WRITER, personNameLookup);

        double language = movie.language() == null ? 0d
                : profile.languageAffinity().getOrDefault(movie.language(), 0d);
        total += language * LANGUAGE_WEIGHT;
        if (language > EVIDENCE_THRESHOLD && movie.language() != null) {
            scored.addContribution(SignalContribution.of(
                    SignalKind.LANGUAGE_MATCH, language, displayLanguage(movie.language())));
        }

        scored.setScore(SimilarityFunctions.clamp01(total));
        return scored;
    }

    /**
     * Records the single strongest matching attribute as evidence, so the
     * explanation names a concrete genre/person rather than a vague category.
     */
    private void recordTop(ScoredMovie scored,
                           Collection<Long> attributeIds,
                           Map<Long, Double> affinity,
                           SignalKind kind,
                           LongFunction<String> nameLookup) {
        if (attributeIds.isEmpty() || affinity.isEmpty()) {
            return;
        }
        Long bestId = null;
        double best = 0d;
        for (Long id : attributeIds) {
            double value = affinity.getOrDefault(id, 0d);
            if (value > best) {
                best = value;
                bestId = id;
            }
        }
        if (bestId != null && best > EVIDENCE_THRESHOLD) {
            String name = nameLookup == null ? null : nameLookup.apply(bestId);
            if (name != null && !name.isBlank()) {
                scored.addContribution(SignalContribution.of(kind, best, name, bestId));
            }
        }
    }

    private static String displayLanguage(String code) {
        return switch (code) {
            case "en" -> "English";
            case "ja" -> "Japanese";
            case "ko" -> "Korean";
            case "fr" -> "French";
            case "es" -> "Spanish";
            case "de" -> "German";
            case "pt" -> "Portuguese";
            case "it" -> "Italian";
            case "hi" -> "Hindi";
            case "te" -> "Telugu";
            case "ta" -> "Tamil";
            case "zh" -> "Chinese";
            default -> code.toUpperCase();
        };
    }

    @Override
    public RecommendationType type() {
        return RecommendationType.CONTENT_BASED;
    }

    /** Needs at least one attribute affinity to say anything. */
    @Override
    public boolean supports(UserTasteProfile profile) {
        return !profile.genreAffinity().isEmpty()
                || !profile.keywordAffinity().isEmpty()
                || !profile.directorAffinity().isEmpty()
                || !profile.castAffinity().isEmpty();
    }
}
