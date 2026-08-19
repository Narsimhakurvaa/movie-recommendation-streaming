import { Link, useParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { Film } from 'lucide-react';
import { movieService } from '@/services/movies';
import { useWatchlistToggle } from '@/hooks/use-watchlist-toggle';
import { MovieCard } from '@/components/movie/MovieCard';
import { MovieGridSkeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';

/** All films in a single genre, or the genre index when no slug is given. */
export function GenrePage() {
  const { slug } = useParams<{ slug: string }>();
  const { toggle, pendingMovieId } = useWatchlistToggle();

  const { data: genres = [] } = useQuery({
    queryKey: ['genres'],
    queryFn: movieService.genres,
    staleTime: 30 * 60_000,
  });

  const { data, isLoading, isError } = useQuery({
    queryKey: ['genres', slug, 'movies'],
    queryFn: () => movieService.byGenre(slug!, 0, 30),
    enabled: Boolean(slug),
  });

  if (!slug) {
    return (
      <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
        <h1 className="mb-6 font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight">
          Genres
        </h1>
        <ul className="grid grid-cols-2 gap-3 sm:grid-cols-3 lg:grid-cols-4">
          {genres.map((genre) => (
            <li key={genre.id}>
              <Link
                to={`/genres/${genre.slug}`}
                className="flex items-center justify-between rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4 transition-colors hover:border-[var(--accent)]"
              >
                <span className="font-medium">{genre.name}</span>
                <span className="text-sm text-[var(--text-muted)]">{genre.movieCount}</span>
              </Link>
            </li>
          ))}
        </ul>
      </div>
    );
  }

  const genreName = genres.find((genre) => genre.slug === slug)?.name ?? slug;

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-6">
        <Link to="/genres" className="text-sm text-[var(--accent)] hover:underline">
          ← All genres
        </Link>
        <h1 className="mt-2 font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight capitalize">
          {genreName}
        </h1>
        {data ? (
          <p className="mt-1 text-sm text-[var(--text-secondary)]">
            {data.totalElements.toLocaleString()} films
          </p>
        ) : null}
      </header>

      {isLoading ? (
        <MovieGridSkeleton count={18} />
      ) : isError || !data || data.content.length === 0 ? (
        <EmptyState
          icon={Film}
          title="No films in this genre yet"
          description="Try another genre, or browse the whole catalogue."
          action={
            <Link to="/movies">
              <Button>Browse all films</Button>
            </Link>
          }
        />
      ) : (
        <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6">
          {data.content.map((movie) => (
            <MovieCard
              key={movie.id}
              movie={movie}
              onToggleWatchlist={toggle}
              isTogglingWatchlist={pendingMovieId === movie.id}
            />
          ))}
        </div>
      )}
    </div>
  );
}
