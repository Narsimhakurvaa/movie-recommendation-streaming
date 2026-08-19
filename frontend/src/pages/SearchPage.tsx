import { useSearchParams } from 'react-router-dom';
import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { SearchX } from 'lucide-react';
import { movieService } from '@/services/movies';
import { useWatchlistToggle } from '@/hooks/use-watchlist-toggle';
import { MovieCard } from '@/components/movie/MovieCard';
import { MovieGridSkeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';

/** Full search results for a term submitted from the header. */
export function SearchPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const query = searchParams.get('q') ?? '';
  const page = Number(searchParams.get('page') ?? '0');
  const { toggle, pendingMovieId } = useWatchlistToggle();

  const { data, isLoading, isError, refetch, isFetching } = useQuery({
    queryKey: ['movies', 'search', query, page],
    queryFn: () => movieService.search(query, {}, page, 24),
    enabled: query.trim().length > 0,
    placeholderData: keepPreviousData,
  });

  const goToPage = (next: number) => {
    const params = new URLSearchParams(searchParams);
    params.set('page', String(Math.max(0, next)));
    setSearchParams(params);
    window.scrollTo({ top: 0, behavior: 'smooth' });
  };

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-6">
        <h1 className="font-[family-name:var(--font-display)] text-2xl font-bold tracking-tight">
          {query ? <>Results for “{query}”</> : 'Search'}
        </h1>
        {data ? (
          <p className="mt-1 text-sm text-[var(--text-secondary)]" aria-live="polite">
            {data.totalElements.toLocaleString()} film{data.totalElements === 1 ? '' : 's'} found
          </p>
        ) : null}
      </header>

      {!query ? (
        <EmptyState
          icon={SearchX}
          title="Search the catalogue"
          description="Use the search box above to find films by title."
        />
      ) : isLoading ? (
        <MovieGridSkeleton count={18} />
      ) : isError ? (
        <EmptyState
          icon={SearchX}
          title="Search failed"
          description="Something went wrong running that search."
          action={<Button onClick={() => refetch()}>Try again</Button>}
        />
      ) : data && data.content.length === 0 ? (
        <EmptyState
          icon={SearchX}
          title={`No films match “${query}”`}
          description="Check the spelling, or try a shorter or more general term."
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
            <nav className="mt-8 flex items-center justify-center gap-3" aria-label="Pagination">
              <Button variant="secondary" disabled={data.first} onClick={() => goToPage(page - 1)}>
                Previous
              </Button>
              <span className="text-sm text-[var(--text-secondary)]" aria-live="polite">
                Page {data.page + 1} of {data.totalPages}
              </span>
              <Button variant="secondary" disabled={data.last} onClick={() => goToPage(page + 1)}>
                Next
              </Button>
            </nav>
          ) : null}
        </>
      )}
    </div>
  );
}
