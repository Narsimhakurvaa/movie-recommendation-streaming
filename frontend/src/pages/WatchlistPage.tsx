import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Bookmark } from 'lucide-react';
import { watchlistService } from '@/services/interactions';
import { useWatchlistToggle } from '@/hooks/use-watchlist-toggle';
import { MovieCard } from '@/components/movie/MovieCard';
import { MovieGridSkeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';

export function WatchlistPage() {
  const { toggle, pendingMovieId } = useWatchlistToggle();

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['watchlist'],
    queryFn: () => watchlistService.list(0, 48),
  });

  const items = data?.content ?? [];

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-6">
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight">
          Your watchlist
        </h1>
        {data ? (
          <p className="mt-1 text-sm text-[var(--text-secondary)]">
            {data.totalElements} film{data.totalElements === 1 ? '' : 's'} saved
          </p>
        ) : null}
      </header>

      {isLoading ? (
        <MovieGridSkeleton count={12} />
      ) : isError ? (
        <EmptyState
          icon={Bookmark}
          title="Could not load your watchlist"
          description="Something went wrong."
          action={<Button onClick={() => refetch()}>Try again</Button>}
        />
      ) : items.length === 0 ? (
        <EmptyState
          icon={Bookmark}
          title="Your watchlist is empty"
          description="Save films you want to watch later and they will appear here."
          action={
            <Link to="/movies">
              <Button>Find something to watch</Button>
            </Link>
          }
        />
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
          {items.map((item) => (
            <MovieCard
              key={item.id}
              movie={item.movie}
              onToggleWatchlist={toggle}
              isTogglingWatchlist={pendingMovieId === item.movie.id}
            />
          ))}
        </div>
      )}
    </div>
  );
}
