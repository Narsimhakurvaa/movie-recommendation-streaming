import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  Activity, Database, Film, RefreshCw, ShieldAlert, Users,
} from 'lucide-react';
import { adminService } from '@/services/admin';
import { useToast } from '@/hooks/use-toast';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { ConfirmDialog } from '@/components/ui/Modal';
import { useDebouncedValue } from '@/hooks/use-debounced-value';
import { formatRelativeTime } from '@/lib/utils';
import type { AdminUser } from '@/types/api';

type Tab = 'overview' | 'users' | 'moderation';

/**
 * Administrative dashboard.
 *
 * The route is guarded client-side purely for navigation; every endpoint it
 * calls is independently restricted to ROLE_ADMIN on the server, so bypassing
 * this UI gains nothing.
 */
export function AdminPage() {
  const [tab, setTab] = useState<Tab>('overview');

  return (
    <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
      <header className="mb-6">
        <h1 className="font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight">
          Administration
        </h1>
        <p className="mt-1 text-sm text-[var(--text-secondary)]">
          Platform metrics, user management and review moderation.
        </p>
      </header>

      <div role="tablist" aria-label="Admin sections" className="mb-6 flex gap-1 border-b border-[var(--border-subtle)]">
        {([
          ['overview', 'Overview'],
          ['users', 'Users'],
          ['moderation', 'Moderation'],
        ] as const).map(([key, label]) => (
          <button
            key={key}
            role="tab"
            aria-selected={tab === key}
            aria-controls={`panel-${key}`}
            id={`tab-${key}`}
            onClick={() => setTab(key)}
            className={`-mb-px border-b-2 px-4 py-2 text-sm font-medium transition-colors ${
              tab === key
                ? 'border-[var(--accent)] text-[var(--accent)]'
                : 'border-transparent text-[var(--text-secondary)] hover:text-[var(--text-primary)]'
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      <div role="tabpanel" id={`panel-${tab}`} aria-labelledby={`tab-${tab}`}>
        {tab === 'overview' ? <OverviewTab /> : null}
        {tab === 'users' ? <UsersTab /> : null}
        {tab === 'moderation' ? <ModerationTab /> : null}
      </div>
    </div>
  );
}

function OverviewTab() {
  const { notify } = useToast();
  const queryClient = useQueryClient();

  const { data: stats, isLoading } = useQuery({
    queryKey: ['admin', 'statistics'],
    queryFn: adminService.statistics,
  });

  const { data: provider } = useQuery({
    queryKey: ['admin', 'provider'],
    queryFn: adminService.providerStatus,
  });

  const sync = useMutation({
    mutationFn: () => adminService.syncCatalogue(1),
    onSuccess: (result) => {
      notify(
        `Sync complete: ${result.created} added, ${result.updated} updated, ${result.skipped} skipped`,
        'success',
      );
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
    },
    onError: () => notify('Catalogue sync failed', 'error'),
  });

  if (isLoading || !stats) {
    return (
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {Array.from({ length: 8 }, (_, index) => (
          <Skeleton key={index} className="h-24 rounded-[var(--radius-card)]" />
        ))}
      </div>
    );
  }

  const cards = [
    { icon: Users, label: 'Total users', value: stats.users.total, hint: `${stats.users.enabled} active` },
    { icon: Users, label: 'New this month', value: stats.users.joinedLast30Days },
    { icon: Film, label: 'Films', value: stats.catalogue.movies, hint: `${stats.catalogue.genres} genres` },
    { icon: Database, label: 'People', value: stats.catalogue.people },
    { icon: Activity, label: 'Ratings', value: stats.engagement.ratings },
    { icon: Activity, label: 'Reviews', value: stats.engagement.reviews, hint: `${stats.engagement.hiddenReviews} hidden` },
    { icon: Activity, label: 'Interactions (7d)', value: stats.engagement.interactionsLast7Days },
    { icon: Activity, label: 'Recommendations (7d)', value: stats.engagement.recommendationsLast7Days },
  ];

  return (
    <div className="space-y-6">
      <div className="grid grid-cols-2 gap-4 lg:grid-cols-4">
        {cards.map((card) => (
          <div
            key={card.label}
            className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4"
          >
            <card.icon className="mb-2 h-5 w-5 text-[var(--accent)]" aria-hidden="true" />
            <p className="text-2xl font-bold">{card.value.toLocaleString()}</p>
            <p className="text-xs text-[var(--text-muted)]">{card.label}</p>
            {card.hint ? <p className="mt-0.5 text-xs text-[var(--text-muted)]">{card.hint}</p> : null}
          </div>
        ))}
      </div>

      <section className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <div>
            <h2 className="font-semibold">Metadata provider</h2>
            <p className="mt-1 text-sm text-[var(--text-secondary)]">
              Active provider: <strong>{provider?.provider ?? '—'}</strong>
              {provider?.available === false ? ' (unavailable)' : ''} ·{' '}
              {provider?.catalogueSize ?? 0} films indexed
            </p>
          </div>
          <Button onClick={() => sync.mutate()} isLoading={sync.isPending}>
            <RefreshCw className="h-4 w-4" aria-hidden="true" />
            Sync catalogue
          </Button>
        </div>
      </section>

      <div className="grid gap-6 lg:grid-cols-2">
        <section className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5">
          <h2 className="mb-3 font-semibold">Most engaged films</h2>
          {stats.mostPopular.length === 0 ? (
            <p className="text-sm text-[var(--text-muted)]">No interactions recorded yet.</p>
          ) : (
            <ol className="space-y-2">
              {stats.mostPopular.map((movie, index) => (
                <li key={movie.id} className="flex items-center gap-3 text-sm">
                  <span className="w-5 shrink-0 text-[var(--text-muted)]">{index + 1}</span>
                  <span className="min-w-0 flex-1 truncate">{movie.title}</span>
                  <span className="shrink-0 text-[var(--text-muted)]">{movie.interactions}</span>
                </li>
              ))}
            </ol>
          )}
        </section>

        <section className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-5">
          <h2 className="mb-3 font-semibold">Recommendation mix</h2>
          {Object.keys(stats.recommendationsByType).length === 0 ? (
            <p className="text-sm text-[var(--text-muted)]">No recommendations served yet.</p>
          ) : (
            <ul className="space-y-2">
              {Object.entries(stats.recommendationsByType).map(([type, count]) => (
                <li key={type} className="flex items-center justify-between text-sm">
                  <span className="capitalize">{type.toLowerCase().replace(/_/g, ' ')}</span>
                  <span className="text-[var(--text-muted)]">{count}</span>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}

function UsersTab() {
  const { notify } = useToast();
  const queryClient = useQueryClient();
  const [search, setSearch] = useState('');
  const [pendingToggle, setPendingToggle] = useState<AdminUser | null>(null);
  const debouncedSearch = useDebouncedValue(search, 350);

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'users', debouncedSearch],
    queryFn: () => adminService.users(debouncedSearch, undefined, 0, 25),
  });

  const toggleEnabled = useMutation({
    mutationFn: (user: AdminUser) => adminService.setUserEnabled(user.id, !user.enabled),
    onSuccess: (updated) => {
      notify(`${updated.displayName} ${updated.enabled ? 'enabled' : 'disabled'}`, 'success');
      setPendingToggle(null);
      void queryClient.invalidateQueries({ queryKey: ['admin'] });
    },
    onError: () => notify('Could not update that account', 'error'),
  });

  return (
    <div className="space-y-4">
      <label htmlFor="user-search" className="sr-only">
        Search users
      </label>
      <input
        id="user-search"
        type="search"
        placeholder="Search by name or email…"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        className="h-10 w-full max-w-sm rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-3 text-sm focus-visible:outline-2"
      />

      {isLoading ? (
        <Skeleton className="h-64 w-full rounded-[var(--radius-card)]" />
      ) : (
        <div className="overflow-x-auto rounded-[var(--radius-card)] border border-[var(--border-subtle)]">
          <table className="w-full text-sm">
            <caption className="sr-only">Registered users</caption>
            <thead className="bg-[var(--surface-sunken)] text-left">
              <tr>
                <th scope="col" className="p-3 font-medium">User</th>
                <th scope="col" className="p-3 font-medium">Roles</th>
                <th scope="col" className="p-3 font-medium">Activity</th>
                <th scope="col" className="p-3 font-medium">Last seen</th>
                <th scope="col" className="p-3 font-medium">Status</th>
                <th scope="col" className="p-3 font-medium"><span className="sr-only">Actions</span></th>
              </tr>
            </thead>
            <tbody>
              {data?.content.map((user) => (
                <tr key={user.id} className="border-t border-[var(--border-subtle)]">
                  <td className="p-3">
                    <p className="font-medium">{user.displayName}</p>
                    <p className="text-xs text-[var(--text-muted)]">{user.email}</p>
                  </td>
                  <td className="p-3 text-xs text-[var(--text-secondary)]">
                    {user.roles.map((role) => role.replace('ROLE_', '')).join(', ')}
                  </td>
                  <td className="p-3 text-xs text-[var(--text-secondary)]">
                    {user.ratingCount} ratings · {user.reviewCount} reviews
                  </td>
                  <td className="p-3 text-xs text-[var(--text-muted)]">
                    {user.lastLoginAt ? formatRelativeTime(user.lastLoginAt) : 'Never'}
                  </td>
                  <td className="p-3">
                    <span
                      className={`rounded-full px-2 py-0.5 text-xs font-medium ${
                        user.enabled
                          ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
                          : 'bg-red-500/15 text-red-700 dark:text-red-300'
                      }`}
                    >
                      {user.enabled ? 'Active' : 'Disabled'}
                    </span>
                  </td>
                  <td className="p-3 text-right">
                    <Button variant="ghost" size="sm" onClick={() => setPendingToggle(user)}>
                      {user.enabled ? 'Disable' : 'Enable'}
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      <ConfirmDialog
        open={pendingToggle !== null}
        title={pendingToggle?.enabled ? 'Disable this account?' : 'Enable this account?'}
        message={
          pendingToggle?.enabled
            ? `${pendingToggle.displayName} will be signed out everywhere and unable to sign back in.`
            : `${pendingToggle?.displayName} will be able to sign in again.`
        }
        confirmLabel={pendingToggle?.enabled ? 'Disable' : 'Enable'}
        destructive={pendingToggle?.enabled ?? false}
        isLoading={toggleEnabled.isPending}
        onConfirm={() => pendingToggle && toggleEnabled.mutate(pendingToggle)}
        onCancel={() => setPendingToggle(null)}
      />
    </div>
  );
}

function ModerationTab() {
  const { notify } = useToast();
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: ['admin', 'reviews'],
    queryFn: () => adminService.reviews(undefined, 0, 25),
  });

  const moderate = useMutation({
    mutationFn: ({ id, status }: { id: number; status: string }) =>
      adminService.moderateReview(id, status),
    onSuccess: () => {
      notify('Review updated', 'success');
      void queryClient.invalidateQueries({ queryKey: ['admin', 'reviews'] });
    },
    onError: () => notify('Could not moderate that review', 'error'),
  });

  if (isLoading) return <Skeleton className="h-64 w-full rounded-[var(--radius-card)]" />;

  const reviews = data?.content ?? [];

  if (reviews.length === 0) {
    return (
      <p className="rounded-[var(--radius-card)] border border-dashed border-[var(--border-subtle)] p-10 text-center text-sm text-[var(--text-muted)]">
        No reviews to moderate.
      </p>
    );
  }

  return (
    <ul className="space-y-3">
      {reviews.map((review) => (
        <li
          key={review.id}
          className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-4"
        >
          <div className="mb-2 flex flex-wrap items-center gap-2">
            <span className="font-medium">{review.author.displayName}</span>
            <span className="text-xs text-[var(--text-muted)]">on {review.movieTitle}</span>
            <span
              className={`ml-auto rounded-full px-2 py-0.5 text-xs font-medium ${
                review.status === 'PUBLISHED'
                  ? 'bg-emerald-500/15 text-emerald-700 dark:text-emerald-300'
                  : review.status === 'HIDDEN'
                    ? 'bg-red-500/15 text-red-700 dark:text-red-300'
                    : 'bg-amber-500/15 text-amber-700 dark:text-amber-300'
              }`}
            >
              {review.status}
            </span>
          </div>
          <p className="mb-3 line-clamp-3 text-sm text-[var(--text-secondary)]">{review.body}</p>
          <div className="flex flex-wrap gap-2">
            {review.status !== 'PUBLISHED' ? (
              <Button
                size="sm"
                variant="secondary"
                onClick={() => moderate.mutate({ id: review.id, status: 'PUBLISHED' })}
              >
                Publish
              </Button>
            ) : null}
            {review.status !== 'HIDDEN' ? (
              <Button
                size="sm"
                variant="danger"
                onClick={() => moderate.mutate({ id: review.id, status: 'HIDDEN' })}
              >
                <ShieldAlert className="h-3.5 w-3.5" aria-hidden="true" />
                Hide
              </Button>
            ) : null}
          </div>
        </li>
      ))}
    </ul>
  );
}
