import { useQuery } from '@tanstack/react-query';
import { Sparkles } from 'lucide-react';
import { recommendationService } from '@/services/movies';
import { useWatchlistToggle } from '@/hooks/use-watchlist-toggle';
import { MovieCard } from '@/components/movie/MovieCard';
import { MovieGridSkeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';
import { Link } from 'react-router-dom';

/** Explains what produced the current set of recommendations. */
const STRATEGY_COPY: Record<string, string> = {
  HYBRID: 'Blended from your ratings, viewing history and viewers with similar taste.',
  CONTENT_BASED: 'Matched on the genres, themes, cast and directors you gravitate towards.',
  COLLABORATIVE: 'Drawn from what viewers with overlapping taste rated highly.',
  POPULARITY: 'Ranked by quality and popularity across the catalogue.',
  COLD_START: 'Based on the genres you picked. Rate a few films and these will sharpen.',
  SIMILAR: 'Similar to what you were just viewing.',
};

export function RecommendationsPage() {
  const { toggle, pendingMovieId } = useWatchlistToggle();

  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['recommendations', 'full'],
    queryFn: () => recommendationService.forMe(0, 36),
  });

  const items = data?.content ?? [];
  const strategy = items[0]?.recommendationType;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-6">
        <div className="flex items-center gap-2">
          <Sparkles className="h-6 w-6 text-[var(--accent)]" aria-hidden="true" />
          <h1 className="font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight">
            For you
          </h1>
        </div>
        {strategy ? (
          <p className="mt-2 max-w-2xl text-sm text-[var(--text-secondary)]">
            {STRATEGY_COPY[strategy] ?? STRATEGY_COPY.HYBRID}
          </p>
        ) : null}
      </header>

      {isLoading ? (
        <MovieGridSkeleton count={18} />
      ) : isError ? (
        <EmptyState
          icon={Sparkles}
          title="Could not load recommendations"
          description="Something went wrong building your list."
          action={<Button onClick={() => refetch()}>Try again</Button>}
        />
      ) : items.length === 0 ? (
        <EmptyState
          icon={Sparkles}
          title="Nothing to recommend yet"
          description="Rate a few films or add some to your watchlist and this page will fill up."
          action={
            <Link to="/movies">
              <Button>Browse the catalogue</Button>
            </Link>
          }
        />
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
          {items.map((item) => (
            <MovieCard
              key={item.movie.id}
              movie={item.movie}
              reason={item.reason}
              onToggleWatchlist={toggle}
              isTogglingWatchlist={pendingMovieId === item.movie.id}
            />
          ))}
        </div>
      )}
    </div>
  );
}
