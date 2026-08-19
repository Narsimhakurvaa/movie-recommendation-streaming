import { memo } from 'react';
import { Link } from 'react-router-dom';
import { Bookmark, BookmarkCheck, Star } from 'lucide-react';
import type { MovieSummary } from '@/types/api';
import { cn, formatRating } from '@/lib/utils';
import { MoviePoster } from './MoviePoster';

interface MovieCardProps {
  movie: MovieSummary;
  onToggleWatchlist?: (movie: MovieSummary) => void;
  isTogglingWatchlist?: boolean;
  /** Explanation text shown on recommendation cards. */
  reason?: string;
  priority?: boolean;
  className?: string;
}

/**
 * The catalogue's primary card.
 *
 * The whole card is a single link, with the watchlist toggle as a sibling
 * control rather than a nested button - nesting interactive elements inside an
 * anchor is invalid HTML and breaks keyboard and screen-reader behaviour.
 *
 * Memoised because grids render dozens at a time and re-render whenever a
 * single sibling's watchlist state changes.
 */
export const MovieCard = memo(function MovieCard({
  movie,
  onToggleWatchlist,
  isTogglingWatchlist = false,
  reason,
  priority = false,
  className,
}: MovieCardProps) {
  const saved = movie.inWatchlist === true;

  return (
    <article className={cn('group relative', className)}>
      <div className="relative overflow-hidden rounded-[var(--radius-card)] bg-[var(--surface-sunken)]">
        <Link
          to={`/movies/${movie.id}`}
          className="block focus-visible:outline-2 focus-visible:outline-offset-2"
          aria-label={`${movie.title}${movie.releaseYear ? `, ${movie.releaseYear}` : ''}`}
        >
          <MoviePoster
            src={movie.posterUrl}
            title={movie.title}
            priority={priority}
            className="aspect-[2/3] w-full transition-transform duration-300 group-hover:scale-105"
          />
          {/* Gradient improves contrast for the rating chip over pale posters. */}
          <div
            className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/70 via-transparent to-transparent opacity-0 transition-opacity duration-300 group-hover:opacity-100"
            aria-hidden="true"
          />
        </Link>

        {movie.externalRating > 0 ? (
          <div className="pointer-events-none absolute left-2 top-2 flex items-center gap-1 rounded-md bg-black/75 px-1.5 py-0.5 text-xs font-semibold text-white backdrop-blur-sm">
            <Star className="h-3 w-3 fill-[var(--color-brand-400)] text-[var(--color-brand-400)]" aria-hidden="true" />
            {formatRating(movie.externalRating)}
          </div>
        ) : null}

        {onToggleWatchlist ? (
          <button
            type="button"
            onClick={() => onToggleWatchlist(movie)}
            disabled={isTogglingWatchlist}
            aria-pressed={saved}
            aria-label={saved ? `Remove ${movie.title} from watchlist` : `Add ${movie.title} to watchlist`}
            className={cn(
              'absolute right-2 top-2 rounded-md p-1.5 backdrop-blur-sm transition-all',
              'focus-visible:outline-2 focus-visible:outline-offset-2 disabled:opacity-50',
              // Always visible once saved, so state is not hover-dependent.
              saved
                ? 'bg-[var(--accent)] text-[var(--accent-contrast)] opacity-100'
                : 'bg-black/70 text-white opacity-0 group-hover:opacity-100 focus-visible:opacity-100',
            )}
          >
            {saved ? (
              <BookmarkCheck className="h-4 w-4" aria-hidden="true" />
            ) : (
              <Bookmark className="h-4 w-4" aria-hidden="true" />
            )}
          </button>
        ) : null}

        {movie.userRating ? (
          <div className="pointer-events-none absolute bottom-2 left-2 flex items-center gap-1 rounded-md bg-[var(--accent)] px-1.5 py-0.5 text-xs font-bold text-[var(--accent-contrast)]">
            <Star className="h-3 w-3 fill-current" aria-hidden="true" />
            {movie.userRating}
          </div>
        ) : null}
      </div>

      <div className="mt-2 space-y-0.5">
        <h3 className="line-clamp-2 text-sm font-semibold leading-snug text-[var(--text-primary)]">
          <Link to={`/movies/${movie.id}`} className="hover:text-[var(--accent)]">
            {movie.title}
          </Link>
        </h3>
        <p className="text-xs text-[var(--text-muted)]">
          {movie.releaseYear ?? 'TBA'}
          {movie.genres.length > 0 ? ` · ${movie.genres[0]}` : ''}
        </p>
        {reason ? (
          <p className="line-clamp-2 pt-1 text-xs italic text-[var(--accent)]">{reason}</p>
        ) : null}
      </div>
    </article>
  );
});
