package com.cinevault.interaction.service;

import com.cinevault.catalogue.domain.Movie;
import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.common.exception.AccessDeniedAppException;
import com.cinevault.common.exception.BadRequestException;
import com.cinevault.common.exception.DuplicateResourceException;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.domain.Review;
import com.cinevault.interaction.domain.ReviewStatus;
import com.cinevault.interaction.dto.InteractionDtos.ReviewRequest;
import com.cinevault.interaction.repository.RatingRepository;
import com.cinevault.interaction.repository.ReviewRepository;
import com.cinevault.user.domain.User;
import com.cinevault.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for review creation, ownership enforcement and spam rejection.
 *
 * <p>Ownership is checked in the service rather than the controller, so these
 * tests are the authoritative statement of that rule.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private RatingRepository ratingRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private UserRepository userRepository;

    @InjectMocks private ReviewService reviewService;

    private static final String VALID_BODY =
            "A genuinely considered review with enough substance to clear the length check.";

    private User author;
    private Movie movie;

    @BeforeEach
    void setUp() throws Exception {
        author = new User("author@example.com", "hash", "Author");
        setId(author, 1L);
        movie = new Movie("Interstellar", "interstellar-2014");
        setId(movie, 10L);
    }

    @Test
    @DisplayName("rejects a second review of the same film by the same user")
    void rejectsDuplicateReview() {
        when(movieRepository.existsById(10L)).thenReturn(true);
        when(reviewRepository.existsByUserIdAndMovieId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() ->
                reviewService.create(1L, 10L, new ReviewRequest(null, VALID_BODY, false)))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already reviewed");
    }

    @Test
    @DisplayName("rejects a review of a film that does not exist")
    void rejectsUnknownMovie() {
        when(movieRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() ->
                reviewService.create(1L, 999L, new ReviewRequest(null, VALID_BODY, false)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @ParameterizedTest(name = "rejects low-effort body: \"{0}\"")
    @ValueSource(strings = {
            "too short",
            "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
            "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!",
    })
    @DisplayName("rejects padding and filler that a length check alone would allow")
    void rejectsSpam(String body) {
        when(movieRepository.existsById(10L)).thenReturn(true);
        when(reviewRepository.existsByUserIdAndMovieId(1L, 10L)).thenReturn(false);

        assertThatThrownBy(() ->
                reviewService.create(1L, 10L, new ReviewRequest(null, body, false)))
                .isInstanceOf(BadRequestException.class);

        verify(reviewRepository, never()).save(any());
    }

    @Test
    @DisplayName("accepts a substantive review")
    void acceptsGenuineReview() {
        when(movieRepository.existsById(10L)).thenReturn(true);
        when(reviewRepository.existsByUserIdAndMovieId(1L, 10L)).thenReturn(false);
        when(userRepository.getReferenceById(1L)).thenReturn(author);
        when(movieRepository.getReferenceById(10L)).thenReturn(movie);
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ratingRepository.findByUserIdAndMovieId(1L, 10L)).thenReturn(Optional.empty());

        assertThatCode(() ->
                reviewService.create(1L, 10L, new ReviewRequest("Great", VALID_BODY, false)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("refuses to let one user edit another user's review")
    void enforcesOwnershipOnEdit() {
        Review review = new Review(author, movie, VALID_BODY);
        when(reviewRepository.findById(5L)).thenReturn(Optional.of(review));

        // User 2 is not the author.
        assertThatThrownBy(() ->
                reviewService.update(2L, 5L, new ReviewRequest(null, VALID_BODY, false)))
                .isInstanceOf(AccessDeniedAppException.class)
                .hasMessageContaining("only edit your own");
    }

    @Test
    @DisplayName("refuses to let a non-owner delete a review")
    void enforcesOwnershipOnDelete() {
        Review review = new Review(author, movie, VALID_BODY);
        when(reviewRepository.findById(5L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.delete(2L, 5L, false))
                .isInstanceOf(AccessDeniedAppException.class);

        verify(reviewRepository, never()).delete(any());
    }

    @Test
    @DisplayName("allows an administrator to delete any review")
    void allowsAdminDeletion() {
        Review review = new Review(author, movie, VALID_BODY);
        when(reviewRepository.findById(5L)).thenReturn(Optional.of(review));

        reviewService.delete(2L, 5L, true);

        verify(reviewRepository).delete(review);
    }

    @Test
    @DisplayName("hides rather than destroys content when moderating")
    void moderationIsReversible() {
        Review review = new Review(author, movie, VALID_BODY);
        when(reviewRepository.findById(5L)).thenReturn(Optional.of(review));
        when(reviewRepository.save(any(Review.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(ratingRepository.findByUserIdAndMovieId(any(), any())).thenReturn(Optional.empty());

        reviewService.moderate(5L, "HIDDEN", "off topic", 99L);

        org.assertj.core.api.Assertions.assertThat(review.getStatus()).isEqualTo(ReviewStatus.HIDDEN);
        org.assertj.core.api.Assertions.assertThat(review.getBody())
                .as("moderation must not erase the author's words")
                .isEqualTo(VALID_BODY);
    }

    @Test
    @DisplayName("rejects an unknown moderation status")
    void rejectsUnknownStatus() {
        Review review = new Review(author, movie, VALID_BODY);
        when(reviewRepository.findById(5L)).thenReturn(Optional.of(review));

        assertThatThrownBy(() -> reviewService.moderate(5L, "BANISHED", null, 99L))
                .isInstanceOf(BadRequestException.class);
    }

    /** Entity ids are database-generated, so tests set them reflectively. */
    private static void setId(Object entity, Long id) throws Exception {
        Class<?> type = entity.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("id");
                field.setAccessible(true);
                field.set(entity, id);
                return;
            } catch (NoSuchFieldException continueUp) {
                type = type.getSuperclass();
            }
        }
        throw new IllegalStateException("no id field on " + entity.getClass());
    }
}
