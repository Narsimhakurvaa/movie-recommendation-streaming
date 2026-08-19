import { Link } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { History } from 'lucide-react';
import { historyService } from '@/services/interactions';
import { MoviePoster } from '@/components/movie/MoviePoster';
import { Skeleton } from '@/components/ui/Skeleton';
import { EmptyState } from '@/components/ui/EmptyState';
import { Button } from '@/components/ui/Button';
import { formatInteraction, formatRelativeTime } from '@/lib/utils';

/** Chronological record of the viewer's interactions. */
export function HistoryPage() {
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['history'],
    queryFn: () => historyService.list(0, 50),
  });

  const entries = data?.content ?? [];

  return (
    <div className="mx-auto max-w-4xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-6">
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight">
          Your activity
        </h1>
        <p className="mt-1 text-sm text-[var(--text-secondary)]">
          What you have viewed, rated and saved. These signals shape your recommendations.
        </p>
      </header>

      {isLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 6 }, (_, index) => (
            <Skeleton key={index} className="h-20 w-full rounded-[var(--radius-card)]" />
          ))}
        </div>
      ) : isError ? (
        <EmptyState
          icon={History}
          title="Could not load your activity"
          description="Something went wrong."
          action={<Button onClick={() => refetch()}>Try again</Button>}
        />
      ) : entries.length === 0 ? (
        <EmptyState
          icon={History}
          title="No activity yet"
          description="Browse some films and your history will build up here."
          action={
            <Link to="/movies">
              <Button>Start exploring</Button>
            </Link>
          }
        />
      ) : (
        <ol className="space-y-2">
          {entries.map((entry) => (
            <li
              key={entry.id}
              className="flex items-center gap-4 rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-3"
            >
              <Link to={`/movies/${entry.movie.id}`} className="shrink-0">
                <MoviePoster
                  src={entry.movie.posterUrl}
                  title={entry.movie.title}
                  className="h-16 w-11 rounded"
                />
              </Link>
              <div className="min-w-0 flex-1">
                <Link
                  to={`/movies/${entry.movie.id}`}
                  className="truncate font-medium hover:text-[var(--accent)]"
                >
                  {entry.movie.title}
                </Link>
                <p className="text-sm text-[var(--text-secondary)]">
                  {formatInteraction(entry.interactionType)}
                  {entry.progressPercent !== null ? ` · ${entry.progressPercent}%` : ''}
                </p>
              </div>
              <time
                dateTime={entry.occurredAt}
                className="shrink-0 text-xs text-[var(--text-muted)]"
              >
                {formatRelativeTime(entry.occurredAt)}
              </time>
            </li>
          ))}
        </ol>
      )}
    </div>
  );
}
