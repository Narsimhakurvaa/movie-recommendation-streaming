import { cn } from '@/lib/utils';

/**
 * Loading placeholder.
 *
 * Skeletons are shaped like the content they replace so the layout does not
 * shift when data arrives, which is both less jarring and better for CLS.
 */
export function Skeleton({ className }: { className?: string }) {
  return (
    <div
      className={cn('animate-[shimmer_1.8s_ease-in-out_infinite] rounded-md bg-[var(--surface-sunken)]', className)}
      aria-hidden="true"
    />
  );
}

/** Placeholder matching the movie card's aspect ratio and metadata rows. */
export function MovieCardSkeleton() {
  return (
    <div className="space-y-2">
      <Skeleton className="aspect-[2/3] w-full rounded-[var(--radius-card)]" />
      <Skeleton className="h-4 w-4/5" />
      <Skeleton className="h-3 w-2/5" />
    </div>
  );
}

/** A row of card skeletons, matching the rail layout. */
export function MovieRailSkeleton({ count = 6 }: { count?: number }) {
  return (
    <div className="flex gap-4 overflow-hidden" role="status" aria-label="Loading movies">
      {Array.from({ length: count }, (_, index) => (
        <div key={index} className="w-[150px] shrink-0 sm:w-[170px]">
          <MovieCardSkeleton />
        </div>
      ))}
      <span className="sr-only">Loading movies</span>
    </div>
  );
}

/** Placeholder for a paginated grid. */
export function MovieGridSkeleton({ count = 12 }: { count?: number }) {
  return (
    <div
      className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 lg:grid-cols-5 xl:grid-cols-6"
      role="status"
      aria-label="Loading movies"
    >
      {Array.from({ length: count }, (_, index) => (
        <MovieCardSkeleton key={index} />
      ))}
      <span className="sr-only">Loading movies</span>
    </div>
  );
}
