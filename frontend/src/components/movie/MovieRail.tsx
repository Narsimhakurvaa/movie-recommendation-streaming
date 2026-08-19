import { useRef } from 'react';
import { Link } from 'react-router-dom';
import { ChevronLeft, ChevronRight } from 'lucide-react';
import type { MovieSummary } from '@/types/api';
import { Button } from '@/components/ui/Button';
import { MovieRailSkeleton } from '@/components/ui/Skeleton';
import { MovieCard } from './MovieCard';

interface MovieRailItem {
  movie: MovieSummary;
  reason?: string;
}

interface MovieRailProps {
  title: string;
  subtitle?: string;
  items: MovieRailItem[];
  isLoading?: boolean;
  viewAllHref?: string;
  onToggleWatchlist?: (movie: MovieSummary) => void;
  pendingMovieId?: number | null;
}

/**
 * Horizontally scrolling row of movie cards.
 *
 * Uses native overflow scrolling with CSS snap points rather than a JavaScript
 * carousel: it works without JS, supports touch and trackpad gestures for free,
 * and remains keyboard-scrollable. The arrow buttons are a convenience on top,
 * and are hidden from assistive technology because the list itself is already
 * reachable and traversable.
 */
export function MovieRail({
  title,
  subtitle,
  items,
  isLoading = false,
  viewAllHref,
  onToggleWatchlist,
  pendingMovieId,
}: MovieRailProps) {
  const scrollerRef = useRef<HTMLUListElement>(null);

  const scrollBy = (direction: 1 | -1) => {
    const scroller = scrollerRef.current;
    if (!scroller) return;
    // Page by roughly one viewport of the rail, keeping a card of context.
    scroller.scrollBy({ left: direction * (scroller.clientWidth * 0.8), behavior: 'smooth' });
  };

  if (!isLoading && items.length === 0) return null;

  return (
    <section className="space-y-3" aria-labelledby={`rail-${title.replace(/\s+/g, '-').toLowerCase()}`}>
      <div className="flex items-end justify-between gap-4">
        <div>
          <h2
            id={`rail-${title.replace(/\s+/g, '-').toLowerCase()}`}
            className="font-[family-name:var(--font-display)] text-xl font-bold tracking-tight sm:text-2xl"
          >
            {title}
          </h2>
          {subtitle ? (
            <p className="mt-0.5 text-sm text-[var(--text-secondary)]">{subtitle}</p>
          ) : null}
        </div>
        <div className="flex shrink-0 items-center gap-2">
          {viewAllHref ? (
            <Link
              to={viewAllHref}
              className="text-sm font-medium text-[var(--accent)] hover:underline"
            >
              View all
            </Link>
          ) : null}
          {/* Decorative: the list is already scrollable by keyboard and touch. */}
          <div className="hidden gap-1 sm:flex" aria-hidden="true">
            <Button variant="secondary" size="icon" tabIndex={-1} onClick={() => scrollBy(-1)}>
              <ChevronLeft className="h-4 w-4" />
            </Button>
            <Button variant="secondary" size="icon" tabIndex={-1} onClick={() => scrollBy(1)}>
              <ChevronRight className="h-4 w-4" />
            </Button>
          </div>
        </div>
      </div>

      {isLoading ? (
        <MovieRailSkeleton />
      ) : (
        <ul ref={scrollerRef} className="rail-scroll flex gap-4 overflow-x-auto pb-3">
          {items.map(({ movie, reason }) => (
            <li key={movie.id} className="rail-item w-[150px] shrink-0 sm:w-[170px]">
              <MovieCard
                movie={movie}
                reason={reason}
                onToggleWatchlist={onToggleWatchlist}
                isTogglingWatchlist={pendingMovieId === movie.id}
              />
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
