import { useMemo } from 'react';
import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { Filter, SearchX } from 'lucide-react';
import { movieService } from '@/services/movies';
import { useWatchlistToggle } from '@/hooks/use-watchlist-toggle';
import { MovieCard } from '@/components/movie/MovieCard';
import { MovieGridSkeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';
import type { MovieFilters } from '@/types/api';

const SORT_OPTIONS = [
  { value: 'popularity', label: 'Most popular' },
  { value: 'rating', label: 'Highest rated' },
  { value: 'releaseDate', label: 'Newest first' },
  { value: 'oldest', label: 'Oldest first' },
  { value: 'title', label: 'Title A–Z' },
  { value: 'runtime', label: 'Longest' },
] as const;

const CURRENT_YEAR = new Date().getFullYear();

/**
 * Catalogue discovery with filters, sorting and pagination.
 *
 * Filter state lives in the URL rather than in component state, so a filtered
 * view is shareable, survives a reload, and works with the browser's back
 * button — none of which is true of `useState`.
 */
export function MoviesPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const { toggle, pendingMovieId } = useWatchlistToggle();

  const page = Number(searchParams.get('page') ?? '0');
  const filters = useMemo<MovieFilters>(
    () => ({
      query: searchParams.get('q') ?? undefined,
      genres: searchParams.getAll('genre'),
      yearFrom: searchParams.get('yearFrom') ? Number(searchParams.get('yearFrom')) : undefined,
      yearTo: searchParams.get('yearTo') ? Number(searchParams.get('yearTo')) : undefined,
      minRating: searchParams.get('minRating') ? Number(searchParams.get('minRating')) : undefined,
      sort: (searchParams.get('sort') as MovieFilters['sort']) ?? 'popularity',
    }),
    [searchParams],
  );

  const { data, isLoading, isError, error, refetch, isFetching } = useQuery({
    queryKey: ['movies', 'discover', filters, page],
    queryFn: () => movieService.discover(filters, page, 24),
    // Keeps the previous page visible while the next loads, avoiding a
    // full-page skeleton flash on every pagination click.
    placeholderData: keepPreviousData,
  });

  const { data: genres = [] } = useQuery({
    queryKey: ['genres'],
    queryFn: movieService.genres,
    staleTime: 30 * 60_000,
  });

  const updateParam = (key: string, value: string | null) => {
    const next = new URLSearchParams(searchParams);
    if (value === null || value === '') next.delete(key);
    else next.set(key, value);
    // Any filter change invalidates the current page number.
    next.delete('page');
    setSearchParams(next);
  };

  const toggleGenre = (slug: string) => {
    const next = new URLSearchParams(searchParams);
    const active = next.getAll('genre');
    next.delete('genre');
    const updated = active.includes(slug)
      ? active.filter((entry) => entry !== slug)
      : [...active, slug];
    updated.forEach((entry) => next.append('genre', entry));
    next.delete('page');
    setSearchParams(next);
  };

  const activeFilterCount =
    (filters.genres?.length ?? 0) +
    (filters.yearFrom ? 1 : 0) +
    (filters.yearTo ? 1 : 0) +
    (filters.minRating ? 1 : 0);

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-6">
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight">
          Discover
        </h1>
        <p className="mt-1 text-sm text-[var(--text-secondary)]">
          {data ? `${data.totalElements.toLocaleString()} films` : 'Browse the catalogue'}
        </p>
      </header>

      <div className="mb-6 space-y-4 rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4">
        <div className="flex flex-wrap items-center gap-3">
          <div className="flex items-center gap-2 text-sm font-medium">
            <Filter className="h-4 w-4" aria-hidden="true" />
            Filters
            {activeFilterCount > 0 ? (
              <span className="rounded-full bg-[var(--accent)] px-2 py-0.5 text-xs text-[var(--accent-contrast)]">
                {activeFilterCount}
              </span>
            ) : null}
          </div>

          <div className="ml-auto flex flex-wrap items-center gap-3">
            <label htmlFor="sort" className="text-sm text-[var(--text-secondary)]">
              Sort by
            </label>
            <select
              id="sort"
              value={filters.sort}
              onChange={(event) => updateParam('sort', event.target.value)}
              className="h-9 rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-2 text-sm focus-visible:outline-2"
            >
              {SORT_OPTIONS.map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>

            <label htmlFor="minRating" className="text-sm text-[var(--text-secondary)]">
              Min rating
            </label>
            <select
              id="minRating"
              value={filters.minRating ?? ''}
              onChange={(event) => updateParam('minRating', event.target.value || null)}
              className="h-9 rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-2 text-sm focus-visible:outline-2"
            >
              <option value="">Any</option>
              {[6, 7, 8, 9].map((rating) => (
                <option key={rating} value={rating}>
                  {rating}+
                </option>
              ))}
            </select>

            <label htmlFor="yearFrom" className="text-sm text-[var(--text-secondary)]">
              From
            </label>
            <input
              id="yearFrom"
              type="number"
              min={1900}
              max={CURRENT_YEAR}
              placeholder="Year"
              value={filters.yearFrom ?? ''}
              onChange={(event) => updateParam('yearFrom', event.target.value || null)}
              className="h-9 w-24 rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-2 text-sm focus-visible:outline-2"
            />

            {activeFilterCount > 0 ? (
              <Button variant="ghost" size="sm" onClick={() => setSearchParams(new URLSearchParams())}>
                Clear all
              </Button>
            ) : null}
          </div>
        </div>

        <fieldset>
          <legend className="sr-only">Filter by genre</legend>
          <div className="flex flex-wrap gap-2">
            {genres
              .filter((genre) => genre.movieCount > 0)
              .map((genre) => {
                const active = filters.genres?.includes(genre.slug) ?? false;
                return (
                  <button
                    key={genre.id}
                    type="button"
                    onClick={() => toggleGenre(genre.slug)}
                    aria-pressed={active}
                    className={`rounded-full border px-3 py-1 text-xs font-medium transition-colors ${
                      active
                        ? 'border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-contrast)]'
                        : 'border-[var(--border-subtle)] text-[var(--text-secondary)] hover:border-[var(--accent)]'
                    }`}
                  >
                    {genre.name}
                  </button>
                );
              })}
          </div>
        </fieldset>
      </div>

      {isLoading ? (
        <MovieGridSkeleton count={18} />
      ) : isError ? (
        <EmptyState
          icon={SearchX}
          title="Could not load films"
          description={error instanceof Error ? error.message : 'Something went wrong.'}
          action={<Button onClick={() => refetch()}>Try again</Button>}
        />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={SearchX}
          title="No films match those filters"
          description="Try widening the year range, lowering the minimum rating, or clearing a genre."
          action={
            <Button variant="secondary" onClick={() => setSearchParams(new URLSearchParams())}>
              Clear filters
            </Button>
          }
        />
      ) : (
        <>
          <div
            className={`grid grid-cols-2 gap-4 transition-opacity sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6 ${
              isFetching ? 'opacity-60' : ''
            }`}
          >
            {data?.content.map((movie) => (
              <MovieCard
                key={movie.id}
                movie={movie}
                onToggleWatchlist={toggle}
                isTogglingWatchlist={pendingMovieId === movie.id}
              />
            ))}
          </div>

          {data && data.totalPages > 1 ? (
            <nav
              className="mt-8 flex items-center justify-center gap-3"
              aria-label="Pagination"
            >
              <Button
                variant="secondary"
                disabled={data.first}
                onClick={() => updatePage(searchParams, setSearchParams, page - 1)}
              >
                Previous
              </Button>
              <span className="text-sm text-[var(--text-secondary)]" aria-live="polite">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <Button
                variant="secondary"
                disabled={data.last}
                onClick={() => updatePage(searchParams, setSearchParams, page + 1)}
              >
                Next
              </Button>
            </nav>
          ) : null}
        </>
      )}
    </div>
  );
}

function updatePage(
  searchParams: URLSearchParams,
  setSearchParams: (params: URLSearchParams) => void,
  nextPage: number,
): void {
  const next = new URLSearchParams(searchParams);
  next.set('page', String(Math.max(0, nextPage)));
  setSearchParams(next);
  window.scrollTo({ top: 0, behavior: 'smooth' });
}
