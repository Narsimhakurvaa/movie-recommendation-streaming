import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { Bookmark, History, MessageSquare, Star } from 'lucide-react';
import { profileService } from '@/services/auth';
import { Skeleton } from '@/components/ui/Skeleton';
import { Button } from '@/components/ui/Button';
import { formatDate } from '@/lib/utils';

/** Profile overview: identity, activity counts and stated preferences. */
export function ProfilePage() {
  const { data: profile, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: profileService.get,
  });

  if (isLoading || !profile) {
    return (
      <div className="mx-auto max-w-4xl space-y-4 px-4 py-8 sm:px-6 lg:px-8">
        <Skeleton className="h-32 w-full rounded-[var(--radius-card)]" />
        <Skeleton className="h-24 w-full rounded-[var(--radius-card)]" />
      </div>
    );
  }

  const stats = [
    { icon: Star, label: 'Ratings', value: profile.activity.ratingCount, to: '/history' },
    { icon: MessageSquare, label: 'Reviews', value: profile.activity.reviewCount, to: '/history' },
    { icon: Bookmark, label: 'Watchlist', value: profile.activity.watchlistCount, to: '/watchlist' },
    { icon: History, label: 'Interactions', value: profile.activity.historyCount, to: '/history' },
  ];

  return (
    <div className="mx-auto max-w-4xl space-y-6 px-4 py-8 sm:px-6 lg:px-8">
      <section className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-6">
        <div className="flex flex-col gap-4 sm:flex-row sm:items-center">
          <div className="flex h-20 w-20 shrink-0 items-center justify-center rounded-full bg-[var(--accent)] text-3xl font-bold text-[var(--accent-contrast)]">
            {profile.displayName.charAt(0).toUpperCase()}
          </div>
          <div className="min-w-0 flex-1">
            <h1 className="font-[family-name:var(--font-display)] text-2xl font-bold">
              {profile.displayName}
            </h1>
            <p className="text-sm text-[var(--text-secondary)]">{profile.email}</p>
            <p className="mt-1 text-xs text-[var(--text-muted)]">
              Member since {formatDate(profile.createdAt)}
              {profile.roles.includes('ROLE_ADMIN') ? ' · Administrator' : ''}
            </p>
            {profile.biography ? (
              <p className="mt-3 text-sm text-[var(--text-secondary)]">{profile.biography}</p>
            ) : null}
          </div>
          <Link to="/settings" className="shrink-0">
            <Button variant="secondary">Edit profile</Button>
          </Link>
        </div>
      </section>

      <section aria-labelledby="activity-heading">
        <h2 id="activity-heading" className="sr-only">
          Activity summary
        </h2>
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          {stats.map((stat) => (
            <Link
              key={stat.label}
              to={stat.to}
              className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4 transition-colors hover:border-[var(--accent)]"
            >
              <stat.icon className="mb-2 h-5 w-5 text-[var(--accent)]" aria-hidden="true" />
              <p className="text-2xl font-bold">{stat.value}</p>
              <p className="text-xs text-[var(--text-muted)]">{stat.label}</p>
            </Link>
          ))}
        </div>
      </section>

      {profile.favouriteGenres.length > 0 ? (
        <section
          aria-labelledby="genres-heading"
          className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-6"
        >
          <h2 id="genres-heading" className="mb-3 font-semibold">
            Favourite genres
          </h2>
          <ul className="flex flex-wrap gap-2">
            {profile.favouriteGenres.map((genre) => (
              <li key={genre.id}>
                <Link
                  to={`/genres/${genre.slug}`}
                  className="rounded-full border border-[var(--border-subtle)] px-3 py-1 text-sm hover:border-[var(--accent)] hover:text-[var(--accent)]"
                >
                  {genre.name}
                </Link>
              </li>
            ))}
          </ul>
        </section>
      ) : null}
    </div>
  );
}
