import { useMutation, useQueryClient } from '@tanstack/react-query';
import { watchlistService } from '@/services/interactions';
import { useToast } from './use-toast';
import { useAuth } from './use-auth';
import type { MovieSummary } from '@/types/api';
import { ApiError } from '@/lib/api-client';

/**
 * Adds or removes a film from the watchlist, updating the UI immediately.
 *
 * ## Optimistic update
 * The button flips before the request completes, because waiting ~200ms to
 * acknowledge a bookmark feels broken. Every cached query holding this movie is
 * patched in place, so the change is consistent across the grid, any rail and
 * the detail page at once. On failure the previous cache snapshot is restored
 * and the user is told, so an optimistic update can never silently lie.
 */
export function useWatchlistToggle() {
  const queryClient = useQueryClient();
  const { notify } = useToast();
  const { isAuthenticated } = useAuth();

  const mutation = useMutation({
    mutationFn: async (movie: MovieSummary) => {
      if (movie.inWatchlist) {
        await watchlistService.remove(movie.id);
        return { movieId: movie.id, saved: false };
      }
      await watchlistService.add(movie.id);
      return { movieId: movie.id, saved: true };
    },

    onMutate: async (movie: MovieSummary) => {
      // Stop in-flight refetches from overwriting the optimistic value.
      await queryClient.cancelQueries();
      const snapshot = queryClient.getQueriesData({ queryKey: [] });
      patchCachedMovie(queryClient, movie.id, !movie.inWatchlist);
      return { snapshot };
    },

    onError: (error, _movie, context) => {
      // Roll back to exactly what was cached before the attempt.
      context?.snapshot.forEach(([key, data]) => queryClient.setQueryData(key, data));
      const message =
        error instanceof ApiError && error.status === 409
          ? 'That film is already in your watchlist.'
          : error instanceof ApiError
            ? error.message
            : 'Could not update your watchlist.';
      notify(message, 'error');
    },

    onSuccess: ({ saved }, movie) => {
      notify(
        saved ? `Added "${movie.title}" to your watchlist` : `Removed "${movie.title}"`,
        'success',
      );
    },

    onSettled: () => {
      // Reconcile with the server once the dust settles.
      void queryClient.invalidateQueries({ queryKey: ['watchlist'] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
    },
  });

  const toggle = (movie: MovieSummary) => {
    if (!isAuthenticated) {
      notify('Sign in to save films to your watchlist', 'info');
      return;
    }
    mutation.mutate(movie);
  };

  return {
    toggle,
    pendingMovieId: mutation.isPending ? (mutation.variables?.id ?? null) : null,
  };
}

/**
 * Rewrites `inWatchlist` for one movie wherever it appears in the cache.
 *
 * Walks every cached query rather than targeting known keys, so a movie shown
 * in a rail, a grid and a detail page all update together without this hook
 * needing to know which screens exist.
 */
function patchCachedMovie(
  queryClient: ReturnType<typeof useQueryClient>,
  movieId: number,
  saved: boolean,
): void {
  queryClient.setQueriesData({ queryKey: [] }, (data: unknown) => {
    if (!data || typeof data !== 'object') return data;
    return patchValue(data, movieId, saved);
  });
}

/** Recursively patches matching movie objects inside an arbitrary structure. */
function patchValue(value: unknown, movieId: number, saved: boolean): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => patchValue(item, movieId, saved));
  }
  if (!value || typeof value !== 'object') return value;

  const record = value as Record<string, unknown>;

  // A movie-shaped object carrying the target id.
  if (record.id === movieId && 'inWatchlist' in record && 'title' in record) {
    return { ...record, inWatchlist: saved };
  }

  let changed = false;
  const next: Record<string, unknown> = {};
  for (const [key, entry] of Object.entries(record)) {
    const patched = patchValue(entry, movieId, saved);
    if (patched !== entry) changed = true;
    next[key] = patched;
  }
  return changed ? next : value;
}
