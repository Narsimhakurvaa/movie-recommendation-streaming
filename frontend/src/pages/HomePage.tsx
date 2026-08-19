import { Link } from 'react-router-dom';
import { useQueries, useQuery } from '@tanstack/react-query';
import { Info, Play, Sparkles } from 'lucide-react';
import { movieService, recommendationService } from '@/services/movies';
import { useAuth } from '@/hooks/use-auth';
import { useWatchlistToggle } from '@/hooks/use-watchlist-toggle';
import { MovieRail } from '@/components/movie/MovieRail';
import { MoviePoster } from '@/components/movie/MoviePoster';
import { Button } from '@/components/ui/Button';
import { formatRating, formatRuntime } from '@/lib/utils';
import type { MovieSummary, RecommendationItem } from '@/types/api';

/**
 * Landing page.
 *
 * The rails are fetched in parallel with `useQueries` rather than sequentially,
 * so the page is bounded by the slowest request instead of their sum. Each rail
 * renders as soon as its own data lands.
 */
export function HomePage() {
  const { isAuthenticated, user } = useAuth();
  const { toggle, pendingMovieId } = useWatchlistToggle();

  const personalised = useQuery({
    queryKey: ['recommendations', 'home'],
    queryFn: () => recommendationService.forMe(0, 18),
    staleTime: 2 * 60_000,
  });

  const rails = useQueries({
    queries: [
      {
        queryKey: ['movies', 'trending'],
        queryFn: () => recommendationService.trending(18),
        staleTime: 5 * 60_000,
      },
      {
        queryKey: ['movies', 'popular'],
        queryFn: () => recommendationService.popular(18),
        staleTime: 5 * 60_000,
      },
      {
        queryKey: ['movies', 'top-rated'],
        queryFn: () => recommendationService.topRated(18),
        staleTime: 10 * 60_000,
      },
      {
        queryKey: ['movies', 'new-releases'],
        queryFn: () => recommendationService.newReleases(18),
        staleTime: 10 * 60_000,
      },
    ],
  });

  const [trending, popular, topRated, newReleases] = rails;
  const recommendations: RecommendationItem[] = personalised.data?.content ?? [];

  // The hero is the strongest recommendation, falling back to the top trending
  // title so a signed-out visitor still gets a considered choice.
  const heroItem = recommendations[0];
  const heroMovie: MovieSummary | undefined =
    heroItem?.movie ?? trending.data?.content?.[0] ?? popular.data?.content?.[0];

  const toRailItems = (movies: MovieSummary[] = []) => movies.map((movie) => ({ movie }));

  return (
    <div className="pb-16">
      {heroMovie ? (
        <Hero movie={heroMovie} reason={heroItem?.reason} onToggleWatchlist={toggle} />
      ) : null}

      <div className="mx-auto max-w-7xl space-y-10 px-4 pt-10 sm:px-6 lg:px-8">
        {isAuthenticated ? (
          <MovieRail
            title="Recommended for you"
            subtitle={
              recommendations[0]?.recommendationType === 'COLD_START'
                ? 'Tell us what you like and these will sharpen quickly'
                : 'Chosen from what you have watched and rated'
            }
            items={recommendations.slice(0, 18).map((item) => ({
              movie: item.movie,
              reason: item.reason,
            }))}
            isLoading={personalised.isLoading}
            viewAllHref="/recommendations"
            onToggleWatchlist={toggle}
            pendingMovieId={pendingMovieId}
          />
        ) : (
          <SignedOutPrompt />
        )}

        <MovieRail
          title="Trending now"
          subtitle="What people are watching this month"
          items={toRailItems(trending.data?.content)}
          isLoading={trending.isLoading}
          onToggleWatchlist={toggle}
          pendingMovieId={pendingMovieId}
        />

        <MovieRail
          title="Popular on CineVault"
          items={toRailItems(popular.data?.content)}
          isLoading={popular.isLoading}
          viewAllHref="/movies?sort=popularity"
          onToggleWatchlist={toggle}
          pendingMovieId={pendingMovieId}
        />

        <MovieRail
          title="Top rated"
          subtitle="Critically acclaimed, with enough votes to mean something"
          items={toRailItems(topRated.data?.content)}
          isLoading={topRated.isLoading}
          viewAllHref="/movies?sort=rating"
          onToggleWatchlist={toggle}
          pendingMovieId={pendingMovieId}
        />

        <MovieRail
          title="New releases"
          items={toRailItems(newReleases.data?.content)}
          isLoading={newReleases.isLoading}
          viewAllHref="/movies?sort=releaseDate"
          onToggleWatchlist={toggle}
          pendingMovieId={pendingMovieId}
        />

        <GenreStrip />

        {isAuthenticated && user ? (
          <p className="pt-4 text-center text-sm text-[var(--text-muted)]">
            Rate a few more films and your recommendations get sharper.{' '}
            <Link to="/profile" className="text-[var(--accent)] hover:underline">
              View your profile
            </Link>
          </p>
        ) : null}
      </div>
    </div>
  );
}

function Hero({
  movie,
  reason,
  onToggleWatchlist,
}: {
  movie: MovieSummary;
  reason?: string;
  onToggleWatchlist: (movie: MovieSummary) => void;
}) {
  return (
    <section className="relative overflow-hidden border-b border-[var(--border-subtle)]" aria-label="Featured film">
      <div className="absolute inset-0" aria-hidden="true">
        <MoviePoster
          src={movie.posterUrl}
          title={movie.title}
          priority
          className="h-full w-full scale-110 opacity-25 blur-2xl"
        />
        <div className="absolute inset-0 bg-gradient-to-t from-[var(--surface)] via-[var(--surface)]/80 to-transparent" />
      </div>

      <div className="relative mx-auto flex max-w-7xl flex-col gap-8 px-4 py-12 sm:px-6 md:flex-row md:items-center md:py-16 lg:px-8">
        <div className="mx-auto w-40 shrink-0 sm:w-48 md:mx-0 md:w-56">
          <MoviePoster
            src={movie.posterUrl}
            title={movie.title}
            priority
            className="aspect-[2/3] w-full rounded-[var(--radius-card)] shadow-2xl"
          />
        </div>

        <div className="min-w-0 flex-1 text-center md:text-left">
          {reason ? (
            <p className="mb-2 inline-flex items-center gap-1.5 rounded-full bg-[var(--accent)]/15 px-3 py-1 text-xs font-semibold text-[var(--accent)]">
              <Sparkles className="h-3 w-3" aria-hidden="true" />
              {reason}
            </p>
          ) : null}

          <h1 className="font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight sm:text-4xl lg:text-5xl">
            {movie.title}
          </h1>

          <div className="mt-3 flex flex-wrap items-center justify-center gap-x-3 gap-y-1 text-sm text-[var(--text-secondary)] md:justify-start">
            {movie.releaseYear ? <span>{movie.releaseYear}</span> : null}
            {movie.runtimeMinutes ? (
              <>
                <span aria-hidden="true">·</span>
                <span>{formatRuntime(movie.runtimeMinutes)}</span>
              </>
            ) : null}
            {movie.externalRating > 0 ? (
              <>
                <span aria-hidden="true">·</span>
                <span className="font-semibold text-[var(--accent)]">
                  {formatRating(movie.externalRating)}/10
                </span>
              </>
            ) : null}
          </div>

          {movie.genres.length > 0 ? (
            <div className="mt-3 flex flex-wrap justify-center gap-2 md:justify-start">
              {movie.genres.slice(0, 4).map((genre) => (
                <span
                  key={genre}
                  className="rounded-full border border-[var(--border-subtle)] px-2.5 py-0.5 text-xs text-[var(--text-secondary)]"
                >
                  {genre}
                </span>
              ))}
            </div>
          ) : null}

          <div className="mt-6 flex flex-wrap justify-center gap-3 md:justify-start">
            <Link to={`/movies/${movie.id}`}>
              <Button>
                <Play className="h-4 w-4" aria-hidden="true" />
                View details
              </Button>
            </Link>
            <Button variant="secondary" onClick={() => onToggleWatchlist(movie)}>
              {movie.inWatchlist ? 'In your watchlist' : 'Add to watchlist'}
            </Button>
          </div>
        </div>
      </div>
    </section>
  );
}

function SignedOutPrompt() {
  return (
    <section className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-6 sm:p-8">
      <div className="flex flex-col items-start gap-4 sm:flex-row sm:items-center sm:justify-between">
        <div className="flex gap-3">
          <Info className="mt-0.5 h-5 w-5 shrink-0 text-[var(--accent)]" aria-hidden="true" />
          <div>
            <h2 className="font-[family-name:var(--font-display)] text-lg font-bold">
              Get recommendations built around your taste
            </h2>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">
              Rate films, build a watchlist, and CineVault learns what you actually enjoy — and
              explains why it suggested each title.
            </p>
          </div>
        </div>
        <div className="flex shrink-0 gap-2">
          <Link to="/register">
            <Button>Create an account</Button>
          </Link>
          <Link to="/login">
            <Button variant="secondary">Sign in</Button>
          </Link>
        </div>
      </div>
    </section>
  );
}

function GenreStrip() {
  const { data: genres = [], isLoading } = useQuery({
    queryKey: ['genres'],
    queryFn: movieService.genres,
    staleTime: 30 * 60_000,
  });

  if (isLoading || genres.length === 0) return null;

  return (
    <section aria-labelledby="browse-genres">
      <h2
        id="browse-genres"
        className="mb-3 font-[family-name:var(--font-display)] text-xl font-bold tracking-tight sm:text-2xl"
      >
        Browse by genre
      </h2>
      <ul className="flex flex-wrap gap-2">
        {genres
          .filter((genre) => genre.movieCount > 0)
          .map((genre) => (
            <li key={genre.id}>
              <Link
                to={`/genres/${genre.slug}`}
                className="inline-flex items-center gap-2 rounded-full border border-[var(--border-subtle)] bg-[var(--surface-raised)] px-4 py-2 text-sm font-medium transition-colors hover:border-[var(--accent)] hover:text-[var(--accent)]"
              >
                {genre.name}
                <span className="text-xs text-[var(--text-muted)]">{genre.movieCount}</span>
              </Link>
            </li>
          ))}
      </ul>
    </section>
  );
}
