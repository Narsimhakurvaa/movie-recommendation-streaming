package com.cinevault.interaction.service;

import com.cinevault.catalogue.domain.Movie;
import com.cinevault.catalogue.repository.MovieRepository;
import com.cinevault.catalogue.service.MovieMapper;
import com.cinevault.common.exception.DuplicateResourceException;
import com.cinevault.common.exception.ResourceNotFoundException;
import com.cinevault.interaction.domain.InteractionType;
import com.cinevault.interaction.domain.WatchlistItem;
import com.cinevault.interaction.repository.WatchlistRepository;
import com.cinevault.user.domain.User;
import com.cinevault.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Unit tests for watchlist behaviour, including the concurrency path. */
@ExtendWith(MockitoExtension.class)
class WatchlistServiceTest {

    @Mock private WatchlistRepository watchlistRepository;
    @Mock private MovieRepository movieRepository;
    @Mock private UserRepository userRepository;
    @Mock private WatchHistoryService watchHistoryService;
    @Mock private MovieMapper movieMapper;

    @InjectMocks private WatchlistService watchlistService;

    @Test
    @DisplayName("rejects a film that is already saved")
    void rejectsDuplicate() {
        when(watchlistRepository.existsByUserIdAndMovieId(1L, 10L)).thenReturn(true);

        assertThatThrownBy(() -> watchlistService.add(1L, 10L, null))
                .isInstanceOf(DuplicateResourceException.class)
                .hasMessageContaining("already in your watchlist");

        verify(watchlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("rejects a film that does not exist")
    void rejectsUnknownMovie() {
        when(watchlistRepository.existsByUserIdAndMovieId(1L, 999L)).thenReturn(false);
        when(userRepository.getReferenceById(1L)).thenReturn(new User("a@example.com", "hash", "A"));
        when(movieRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> watchlistService.add(1L, 999L, null))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("records an interaction so the engine sees the intent signal")
    void recordsInteractionOnAdd() {
        Movie movie = new Movie("Interstellar", "interstellar-2014");
        when(watchlistRepository.existsByUserIdAndMovieId(1L, 10L)).thenReturn(false);
        when(userRepository.getReferenceById(1L)).thenReturn(new User("a@example.com", "hash", "A"));
        when(movieRepository.findById(10L)).thenReturn(Optional.of(movie));
        when(watchlistRepository.save(any(WatchlistItem.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        watchlistService.add(1L, 10L, "must watch");

        verify(watchHistoryService).record(1L, 10L, InteractionType.ADDED_TO_WATCHLIST, null);
    }

    @Test
    @DisplayName("translates a lost insert race into a 409 rather than a 500")
    void handlesConcurrentInsertRace() {
        Movie movie = new Movie("Interstellar", "interstellar-2014");
        when(watchlistRepository.existsByUserIdAndMovieId(1L, 10L)).thenReturn(false);
        when(userRepository.getReferenceById(1L)).thenReturn(new User("a@example.com", "hash", "A"));
        when(movieRepository.findById(10L)).thenReturn(Optional.of(movie));
        // Another request inserted the same row between the check and the save.
        when(watchlistRepository.save(any(WatchlistItem.class)))
                .thenThrow(new DataIntegrityViolationException("uq_watchlist_user_movie"));

        assertThatThrownBy(() -> watchlistService.add(1L, 10L, null))
                .isInstanceOf(DuplicateResourceException.class);
    }

    @Test
    @DisplayName("reports removal of a film that was never saved")
    void rejectsRemovingUnsavedFilm() {
        when(watchlistRepository.deleteByUserIdAndMovieId(1L, 10L)).thenReturn(0L);

        assertThatThrownBy(() -> watchlistService.remove(1L, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("not in your watchlist");
    }

    @Test
    @DisplayName("removes a saved film")
    void removesSavedFilm() {
        when(watchlistRepository.deleteByUserIdAndMovieId(1L, 10L)).thenReturn(1L);

        watchlistService.remove(1L, 10L);

        verify(watchlistRepository).deleteByUserIdAndMovieId(1L, 10L);
    }

    @Test
    @DisplayName("reports saved status without loading the whole list")
    void reportsStatus() {
        when(watchlistRepository.existsByUserIdAndMovieId(1L, 10L)).thenReturn(true);

        assertThat(watchlistService.status(1L, 10L).saved()).isTrue();
        verify(watchlistRepository, never()).findByUserId(anyLong(), any());
    }
}
