package com.cinevault.recommendation.scoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

/**
 * Unit tests for the scoring primitives.
 *
 * <p>These are the mathematical core of the recommendation engine, so each
 * metric is verified against hand-computable cases and at its boundaries.
 */
class SimilarityFunctionsTest {

    @Nested
    @DisplayName("Jaccard index")
    class Jaccard {

        @Test
        @DisplayName("is 1 for identical sets")
        void identicalSets() {
            assertThat(SimilarityFunctions.jaccard(Set.of(1L, 2L), Set.of(1L, 2L)))
                    .isEqualTo(1.0d);
        }

        @Test
        @DisplayName("is 0 for disjoint sets")
        void disjointSets() {
            assertThat(SimilarityFunctions.jaccard(Set.of(1L), Set.of(2L))).isZero();
        }

        @Test
        @DisplayName("is intersection over union for partial overlap")
        void partialOverlap() {
            // {1,2} n {2,3} = {2}; union = {1,2,3}; so 1/3.
            assertThat(SimilarityFunctions.jaccard(Set.of(1L, 2L), Set.of(2L, 3L)))
                    .isCloseTo(1.0d / 3.0d, within(1e-9));
        }

        @Test
        @DisplayName("is 0 when either set is empty")
        void emptySet() {
            assertThat(SimilarityFunctions.jaccard(Set.of(), Set.of(1L))).isZero();
            assertThat(SimilarityFunctions.jaccard(Set.of(1L), Set.of())).isZero();
        }

        @Test
        @DisplayName("penalises breadth, which is why it suits genre comparison")
        void penalisesBreadth() {
            double focused = SimilarityFunctions.jaccard(Set.of(1L, 2L), Set.of(1L, 2L));
            double sprawling = SimilarityFunctions.jaccard(Set.of(1L, 2L), Set.of(1L, 2L, 3L, 4L, 5L));

            assertThat(focused)
                    .as("a sprawling film should not look identical to a focused one")
                    .isGreaterThan(sprawling);
        }
    }

    @Nested
    @DisplayName("Cosine similarity")
    class Cosine {

        @Test
        @DisplayName("is 1 for identical vectors")
        void identicalVectors() {
            assertThat(SimilarityFunctions.cosine(Map.of(1L, 1.0, 2L, 2.0), Map.of(1L, 1.0, 2L, 2.0)))
                    .isCloseTo(1.0d, within(1e-9));
        }

        @Test
        @DisplayName("is 0 when the vectors share no dimensions")
        void orthogonalVectors() {
            assertThat(SimilarityFunctions.cosine(Map.of(1L, 1.0), Map.of(2L, 1.0))).isZero();
        }

        @Test
        @DisplayName("is negative for opposing opinions")
        void opposingOpinions() {
            assertThat(SimilarityFunctions.cosine(Map.of(1L, 1.0), Map.of(1L, -1.0)))
                    .isNegative();
        }

        @Test
        @DisplayName("is 0 for an empty vector rather than undefined")
        void emptyVector() {
            assertThat(SimilarityFunctions.cosine(Map.of(), Map.of(1L, 1.0))).isZero();
        }

        @Test
        @DisplayName("ignores magnitude, comparing only direction")
        void ignoresMagnitude() {
            double unit = SimilarityFunctions.cosine(Map.of(1L, 1.0, 2L, 2.0), Map.of(1L, 1.0, 2L, 2.0));
            double scaled = SimilarityFunctions.cosine(Map.of(1L, 1.0, 2L, 2.0), Map.of(1L, 10.0, 2L, 20.0));

            assertThat(scaled).isCloseTo(unit, within(1e-9));
        }
    }

    @Nested
    @DisplayName("Bayesian average")
    class BayesianAverage {

        @Test
        @DisplayName("returns the prior when there are no votes")
        void noVotes() {
            assertThat(SimilarityFunctions.bayesianAverage(10.0, 0, 6.5, 1000))
                    .isEqualTo(6.5d);
        }

        @Test
        @DisplayName("barely moves for a single perfect vote")
        void singleVote() {
            assertThat(SimilarityFunctions.bayesianAverage(10.0, 1, 6.5, 1000))
                    .isLessThan(6.6d);
        }

        @Test
        @DisplayName("trusts the observed mean once the sample is large")
        void largeSample() {
            assertThat(SimilarityFunctions.bayesianAverage(8.5, 30_000, 6.5, 1000))
                    .isGreaterThan(8.4d);
        }

        @Test
        @DisplayName("ranks a well-supported 8.5 above a single 10")
        void manyVotesBeatOnePerfectVote() {
            double acclaimed = SimilarityFunctions.bayesianAverage(8.5, 30_000, 6.5, 1000);
            double fluke = SimilarityFunctions.bayesianAverage(10.0, 1, 6.5, 1000);

            assertThat(acclaimed)
                    .as("this is precisely the failure mode a raw average would have")
                    .isGreaterThan(fluke);
        }
    }

    @Nested
    @DisplayName("Exponential decay")
    class Decay {

        @ParameterizedTest(name = "age {0} days with half-life {1} gives {2}")
        @CsvSource({
                "0,    730, 1.0",
                "730,  730, 0.5",
                "1460, 730, 0.25",
        })
        @DisplayName("halves at each half-life")
        void halvesAtHalfLife(double age, double halfLife, double expected) {
            assertThat(SimilarityFunctions.exponentialDecay(age, halfLife))
                    .isCloseTo(expected, within(1e-9));
        }

        @Test
        @DisplayName("is monotonically decreasing")
        void isMonotonic() {
            assertThat(SimilarityFunctions.exponentialDecay(100, 730))
                    .isGreaterThan(SimilarityFunctions.exponentialDecay(2000, 730));
        }

        @Test
        @DisplayName("does not penalise an unreleased film")
        void clampsNegativeAge() {
            assertThat(SimilarityFunctions.exponentialDecay(-50, 730)).isEqualTo(1.0d);
        }

        @Test
        @DisplayName("rejects a non-positive half-life")
        void rejectsInvalidHalfLife() {
            assertThatThrownBy(() -> SimilarityFunctions.exponentialDecay(1, 0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("halfLifeInDays");
        }
    }

    @Nested
    @DisplayName("Affinity aggregation")
    class Affinity {

        private final Map<Long, Double> affinity = Map.of(1L, 1.0, 2L, 0.2);

        @Test
        @DisplayName("mean averages across a film's own attributes")
        void meanAveragesAttributes() {
            assertThat(SimilarityFunctions.meanAffinity(List.of(1L, 2L), affinity))
                    .isCloseTo(0.6d, within(1e-9));
        }

        @Test
        @DisplayName("peak reports the single strongest attribute")
        void peakReportsStrongest() {
            assertThat(SimilarityFunctions.peakAffinity(List.of(1L, 2L), affinity))
                    .isEqualTo(1.0d);
        }

        @Test
        @DisplayName("peak exceeds mean for uneven input, which is why crew uses it")
        void peakExceedsMean() {
            assertThat(SimilarityFunctions.peakAffinity(List.of(1L, 2L), affinity))
                    .isGreaterThan(SimilarityFunctions.meanAffinity(List.of(1L, 2L), affinity));
        }

        @Test
        @DisplayName("both are 0 when nothing is known")
        void emptyInputs() {
            assertThat(SimilarityFunctions.meanAffinity(List.of(), affinity)).isZero();
            assertThat(SimilarityFunctions.peakAffinity(List.of(9L), affinity)).isZero();
        }
    }

    @Nested
    @DisplayName("Normalisation helpers")
    class Normalisation {

        @Test
        @DisplayName("clamp01 bounds values into [0, 1]")
        void clampBounds() {
            assertThat(SimilarityFunctions.clamp01(-3)).isZero();
            assertThat(SimilarityFunctions.clamp01(4)).isEqualTo(1.0d);
            assertThat(SimilarityFunctions.clamp01(0.5)).isEqualTo(0.5d);
        }

        @Test
        @DisplayName("clamp01 maps NaN to zero rather than propagating it")
        void clampHandlesNaN() {
            assertThat(SimilarityFunctions.clamp01(Double.NaN)).isZero();
        }

        @Test
        @DisplayName("logNormalise compresses an unbounded value into [0, 1)")
        void logNormaliseCompresses() {
            assertThat(SimilarityFunctions.logNormalise(9999, 50)).isBetween(0d, 1d);
            assertThat(SimilarityFunctions.logNormalise(0, 50)).isZero();
        }

        @Test
        @DisplayName("logNormalise preserves ordering")
        void logNormalisePreservesOrdering() {
            assertThat(SimilarityFunctions.logNormalise(100, 50))
                    .isGreaterThan(SimilarityFunctions.logNormalise(10, 50));
        }
    }
}
