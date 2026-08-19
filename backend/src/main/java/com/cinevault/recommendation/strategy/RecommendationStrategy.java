package com.cinevault.recommendation.strategy;

import com.cinevault.recommendation.model.MovieFeatures;
import com.cinevault.recommendation.model.RecommendationType;
import com.cinevault.recommendation.model.ScoredMovie;
import com.cinevault.recommendation.model.UserTasteProfile;

import java.util.Collection;
import java.util.List;

/**
 * A single scoring approach.
 *
 * <p>Implementations are pure functions of their inputs: given the same profile
 * and candidate set they always return the same scores. They perform no I/O,
 * which is what allows the whole engine to be unit-tested without a database
 * and lets the hybrid strategy compose them freely.
 *
 * <p>Every implementation returns scores normalised to {@code [0, 1]} so the
 * hybrid blend can combine them with meaningful weights.
 */
public interface RecommendationStrategy {

    /**
     * Scores the supplied candidates for the given user.
     *
     * @param profile    the user's aggregated taste, never {@code null}
     * @param candidates films eligible for recommendation
     * @return scored candidates, unsorted; entries scoring zero may be omitted
     */
    List<ScoredMovie> score(UserTasteProfile profile, Collection<MovieFeatures> candidates);

    /** Identifies this strategy in API responses and logs. */
    RecommendationType type();

    /**
     * Whether this strategy can contribute for the given user. The hybrid
     * strategy re-normalises its weights over only the applicable strategies,
     * so a user with no ratings is not silently penalised by a collaborative
     * component that cannot produce a signal.
     */
    default boolean supports(UserTasteProfile profile) {
        return true;
    }
}
