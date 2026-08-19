import { useEffect, useState } from 'react';
import { Link, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Bookmark, BookmarkCheck, Clock, Film, Play, Star, Users } from 'lucide-react';
import { movieService } from '@/services/movies';
import { historyService, ratingService } from '@/services/interactions';
import { useAuth } from '@/hooks/use-auth';
import { useToast } from '@/hooks/use-toast';
import { useWatchlistToggle } from '@/hooks/use-watchlist-toggle';
import { MoviePoster } from '@/components/movie/MoviePoster';
import { MovieRail } from '@/components/movie/MovieRail';
import { StarRating } from '@/components/ui/Rating';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { ReviewSection } from '@/features/movies/ReviewSection';
import { formatCount, formatDate, formatRating, formatRuntime, youTubeIdFrom } from '@/lib/utils';

export function MovieDetailPage() {
  const { movieId } = useParams<{ movieId: string }>();
  const id = Number(movieId);
  const { isAuthenticated } = useAuth();
  const { notify } = useToast();
  const { toggle, pendingMovieId } = useWatchlistToggle();
  const queryClient = useQueryClient();
  const [trailerOpen, setTrailerOpen] = useState(false);

  const { data: movie, isLoading, isError } = useQuery({
    queryKey: ['movie', id],
    queryFn: () => movieService.detail(id),
    enabled: Number.isFinite(id),
  });

  const { data: similar } = useQuery({
    queryKey: ['movie', id, 'similar'],
    queryFn: () => movieService.similar(id, 12),
    enabled: Number.isFinite(id),
    staleTime: 10 * 60_000,
  });

  const rateMutation = useMutation({
    mutationFn: (score: number) => ratingService.rate(id, score),
    onSuccess: (result) => {
      notify(`Rated ${result.score} out of 5`, 'success');
      void queryClient.invalidateQueries({ queryKey: ['movie', id] });
      // A new rating changes the taste profile, so recommendations are stale.
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
    },
    onError: () => notify('Could not save your rating', 'error'),
  });

  const clearRatingMutation = useMutation({
    mutationFn: () => ratingService.remove(id),
    onSuccess: () => {
      notify('Rating removed', 'success');
      void queryClient.invalidateQueries({ queryKey: ['movie', id] });
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
    },
    onError: () => notify('Could not remove your rating', 'error'),
  });

  const trailerId = youTubeIdFrom(movie?.trailerUrl);

  // Recorded as an implicit signal; failures are silent by design.
  useEffect(() => {
    if (isAuthenticated && movie) {
      void historyService.record(movie.id, 'VIEWED_DETAILS');
    }
  }, [isAuthenticated, movie]);

  if (isLoading) return <DetailSkeleton />;

  if (isError || !movie) {
    return (
      <div className="mx-auto max-w-3xl px-4 py-16">
        <EmptyState
          icon={Film}
          title="Film not found"
          description="This title may have been removed from the catalogue."
          action={
            <Link to="/movies">
              <Button>Browse the catalogue</Button>
            </Link>
          }
        />
      </div>
    );
  }

  const saved = movie.inWatchlist === true;

  return (
    <article className="pb-16">
      <div className="relative">
        {movie.backdropUrl ? (
          <div className="absolute inset-0 overflow-hidden" aria-hidden="true">
            <img
              src={movie.backdropUrl}
              alt=""
              className="h-full w-full object-cover opacity-20"
              loading="eager"
            />
            <div className="absolute inset-0 bg-gradient-to-t from-[var(--surface)] via-[var(--surface)]/85 to-[var(--surface)]/60" />
          </div>
        ) : null}

        <div className="relative mx-auto flex max-w-7xl flex-col gap-8 px-4 py-10 sm:px-6 md:flex-row lg:px-8">
          <div className="mx-auto w-48 shrink-0 sm:w-56 md:mx-0 md:w-64">
            <MoviePoster
              src={movie.posterUrl}
              title={movie.title}
              priority
              className="aspect-[2/3] w-full rounded-[var(--radius-card)] shadow-2xl"
            />
          </div>

          <div className="min-w-0 flex-1">
            <h1 className="font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight sm:text-4xl">
              {movie.title}
            </h1>
            {movie.originalTitle && movie.originalTitle !== movie.title ? (
              <p className="mt-1 text-sm text-[var(--text-muted)]">{movie.originalTitle}</p>
            ) : null}
            {movie.tagline ? (
              <p className="mt-2 text-base italic text-[var(--text-secondary)]">“{movie.tagline}”</p>
            ) : null}

            <div className="mt-4 flex flex-wrap items-center gap-x-4 gap-y-2 text-sm text-[var(--text-secondary)]">
              {movie.externalRating > 0 ? (
                <span className="flex items-center gap-1.5 font-semibold text-[var(--text-primary)]">
                  <Star className="h-4 w-4 fill-[var(--accent)] text-[var(--accent)]" aria-hidden="true" />
                  {formatRating(movie.externalRating)}
                  <span className="font-normal text-[var(--text-muted)]">
                    ({formatCount(movie.externalVoteCount)} votes)
                  </span>
                </span>
              ) : null}
              {movie.ratingCount > 0 ? (
                <span className="flex items-center gap-1.5">
                  <Users className="h-4 w-4" aria-hidden="true" />
                  {formatRating(movie.averageRating)}/5 from {formatCount(movie.ratingCount)} here
                </span>
              ) : null}
              {movie.runtimeMinutes ? (
                <span className="flex items-center gap-1.5">
                  <Clock className="h-4 w-4" aria-hidden="true" />
                  {formatRuntime(movie.runtimeMinutes)}
                </span>
              ) : null}
              {movie.releaseDate ? <span>{formatDate(movie.releaseDate)}</span> : null}
            </div>

            {movie.genres.length > 0 ? (
              <ul className="mt-4 flex flex-wrap gap-2">
                {movie.genres.map((genre) => (
                  <li key={genre}>
                    <span className="rounded-full border border-[var(--border-subtle)] px-3 py-1 text-xs">
                      {genre}
                    </span>
                  </li>
                ))}
              </ul>
            ) : null}

            {movie.overview ? (
              <p className="mt-5 max-w-2xl leading-relaxed text-[var(--text-secondary)]">
                {movie.overview}
              </p>
            ) : null}

            <div className="mt-6 flex flex-wrap gap-3">
              {trailerId ? (
                <Button
                  onClick={() => {
                    setTrailerOpen(true);
                    if (isAuthenticated) void historyService.record(movie.id, 'WATCHED_TRAILER');
                  }}
                >
                  <Play className="h-4 w-4" aria-hidden="true" />
                  Watch trailer
                </Button>
              ) : null}
              <Button
                variant="secondary"
                onClick={() =>
                  toggle({
                    id: movie.id,
                    title: movie.title,
                    slug: movie.slug,
                    releaseYear: movie.releaseYear,
                    posterUrl: movie.posterUrl,
                    externalRating: movie.externalRating,
                    averageRating: movie.averageRating,
                    ratingCount: movie.ratingCount,
                    runtimeMinutes: movie.runtimeMinutes,
                    genres: movie.genres,
                    inWatchlist: movie.inWatchlist,
                    userRating: movie.userRating,
                  })
                }
                isLoading={pendingMovieId === movie.id}
              >
                {saved ? (
                  <BookmarkCheck className="h-4 w-4" aria-hidden="true" />
                ) : (
                  <Bookmark className="h-4 w-4" aria-hidden="true" />
                )}
                {saved ? 'In watchlist' : 'Add to watchlist'}
              </Button>
            </div>

            <div className="mt-6 rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4">
              {isAuthenticated ? (
                <>
                  <p className="mb-2 text-sm font-medium">Your rating</p>
                  <StarRating
                    value={movie.userRating}
                    onChange={(score) => rateMutation.mutate(score)}
                    onClear={() => clearRatingMutation.mutate()}
                  />
                </>
              ) : (
                <p className="text-sm text-[var(--text-secondary)]">
                  <Link to="/login" className="font-medium text-[var(--accent)] hover:underline">
                    Sign in
                  </Link>{' '}
                  to rate this film and improve your recommendations.
                </p>
              )}
            </div>
          </div>
        </div>
      </div>

      <div className="mx-auto max-w-7xl space-y-10 px-4 sm:px-6 lg:px-8">
        {trailerOpen && trailerId ? (
          <section aria-label="Trailer">
            <div className="aspect-video w-full overflow-hidden rounded-[var(--radius-card)] bg-black">
              <iframe
                src={`https://www.youtube-nocookie.com/embed/${trailerId}?autoplay=1`}
                title={`${movie.title} official trailer`}
                allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture"
                allowFullScreen
                className="h-full w-full"
              />
            </div>
          </section>
        ) : null}

        {movie.directors.length > 0 || movie.cast.length > 0 ? (
          <section aria-labelledby="cast-crew">
            <h2 id="cast-crew" className="mb-4 font-[family-name:var(--font-display)] text-xl font-bold">
              Cast &amp; crew
            </h2>
            {movie.directors.length > 0 ? (
              <p className="mb-3 text-sm">
                <span className="text-[var(--text-muted)]">Directed by </span>
                <span className="font-medium">
                  {movie.directors.map((person) => person.name).join(', ')}
                </span>
              </p>
            ) : null}
            {movie.writers.length > 0 ? (
              <p className="mb-4 text-sm">
                <span className="text-[var(--text-muted)]">Written by </span>
                <span className="font-medium">
                  {movie.writers.map((person) => person.name).join(', ')}
                </span>
              </p>
            ) : null}
            {movie.cast.length > 0 ? (
              <ul className="flex flex-wrap gap-2">
                {movie.cast.map((person) => (
                  <li
                    key={`${person.personId}-${person.name}`}
                    className="rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-raised)] px-3 py-2 text-sm"
                  >
                    {person.name}
                    {person.characterName ? (
                      <span className="block text-xs text-[var(--text-muted)]">
                        as {person.characterName}
                      </span>
                    ) : null}
                  </li>
                ))}
              </ul>
            ) : null}
          </section>
        ) : null}

        <ReviewSection movieId={movie.id} />

        <MovieRail
          title="More like this"
          subtitle="Based on shared genres, themes, cast and crew"
          items={(similar?.content ?? []).map((item) => ({
            movie: item.movie,
            reason: item.reason,
          }))}
          onToggleWatchlist={toggle}
          pendingMovieId={pendingMovieId}
        />
      </div>
    </article>
  );
}

function DetailSkeleton() {
  return (
    <div className="mx-auto flex max-w-7xl flex-col gap-8 px-4 py-10 sm:px-6 md:flex-row lg:px-8">
      <Skeleton className="mx-auto aspect-[2/3] w-48 rounded-[var(--radius-card)] sm:w-56 md:mx-0 md:w-64" />
      <div className="flex-1 space-y-4">
        <Skeleton className="h-10 w-3/4" />
        <Skeleton className="h-4 w-1/3" />
        <Skeleton className="h-20 w-full" />
        <div className="flex gap-3">
          <Skeleton className="h-10 w-36" />
          <Skeleton className="h-10 w-40" />
        </div>
      </div>
    </div>
  );
}
