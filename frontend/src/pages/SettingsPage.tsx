import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { authService, profileService } from '@/services/auth';
import { movieService } from '@/services/movies';
import { useToast } from '@/hooks/use-toast';
import { useTheme, type Theme } from '@/hooks/use-theme';
import { Button } from '@/components/ui/Button';
import { Skeleton } from '@/components/ui/Skeleton';
import { ApiError } from '@/lib/api-client';
import { cn } from '@/lib/utils';
import { useState } from 'react';
import type { UserProfile } from '@/types/api';

const profileSchema = z.object({
  displayName: z.string().min(2, 'At least 2 characters').max(80, 'At most 80 characters'),
  avatarUrl: z.string().url('Enter a valid URL').or(z.literal('')),
  biography: z.string().max(500, 'At most 500 characters'),
});

const passwordSchema = z
  .object({
    currentPassword: z.string().min(1, 'Enter your current password'),
    newPassword: z
      .string()
      .min(12, 'Use at least 12 characters')
      .regex(/[A-Z]/, 'Include an uppercase letter')
      .regex(/[a-z]/, 'Include a lowercase letter')
      .regex(/\d/, 'Include a digit'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.newPassword === values.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

export function SettingsPage() {
  const { notify } = useToast();
  const { theme, setTheme } = useTheme();
  const queryClient = useQueryClient();
  const [selectedGenres, setSelectedGenres] = useState<string[]>([]);

  const { data: profile, isLoading } = useQuery({
    queryKey: ['profile'],
    queryFn: profileService.get,
  });

  const { data: genres = [] } = useQuery({
    queryKey: ['genres'],
    queryFn: movieService.genres,
    staleTime: 30 * 60_000,
  });

  const profileForm = useForm<z.infer<typeof profileSchema>>({
    resolver: zodResolver(profileSchema),
    values: {
      displayName: profile?.displayName ?? '',
      avatarUrl: profile?.avatarUrl ?? '',
      biography: profile?.biography ?? '',
    },
  });

  const passwordForm = useForm<z.infer<typeof passwordSchema>>({
    resolver: zodResolver(passwordSchema),
  });

  // Seed the genre selection once the profile arrives. Done during render so
  // the checkboxes are correct on first paint rather than after a flash of
  // nothing selected; the identity check means a user's later edits are kept.
  const [seededProfile, setSeededProfile] = useState<UserProfile | null>(null);
  if (profile && seededProfile !== profile) {
    setSeededProfile(profile);
    setSelectedGenres(profile.favouriteGenres.map((genre) => genre.slug));
  }

  const updateProfile = useMutation({
    mutationFn: (values: z.infer<typeof profileSchema>) => profileService.update(values),
    onSuccess: () => {
      notify('Profile updated', 'success');
      void queryClient.invalidateQueries({ queryKey: ['profile'] });
    },
    onError: (error) =>
      notify(error instanceof ApiError ? error.message : 'Could not save your profile', 'error'),
  });

  const updatePreferences = useMutation({
    mutationFn: (input: Record<string, unknown>) => profileService.updatePreferences(input),
    onSuccess: () => {
      notify('Preferences saved', 'success');
      void queryClient.invalidateQueries({ queryKey: ['profile'] });
      // Genre changes feed the recommendation engine directly.
      void queryClient.invalidateQueries({ queryKey: ['recommendations'] });
    },
    onError: () => notify('Could not save your preferences', 'error'),
  });

  const changePassword = useMutation({
    mutationFn: (values: z.infer<typeof passwordSchema>) =>
      authService.changePassword(values.currentPassword, values.newPassword),
    onSuccess: () => {
      notify('Password changed. Other sessions have been signed out.', 'success');
      passwordForm.reset();
    },
    onError: (error) =>
      notify(error instanceof ApiError ? error.message : 'Could not change your password', 'error'),
  });

  if (isLoading || !profile) {
    return (
      <div className="mx-auto max-w-2xl space-y-4 px-4 py-8">
        <Skeleton className="h-48 w-full rounded-[var(--radius-card)]" />
        <Skeleton className="h-48 w-full rounded-[var(--radius-card)]" />
      </div>
    );
  }

  return (
    <div className="mx-auto max-w-2xl space-y-6 px-4 py-8 sm:px-6">
      <h1 className="font-[family-name:var(--font-display)] text-3xl font-bold tracking-tight">
        Settings
      </h1>

      <Panel title="Profile">
        <form
          onSubmit={profileForm.handleSubmit((values) => updateProfile.mutate(values))}
          className="space-y-4"
          noValidate
        >
          <TextField
            id="displayName"
            label="Display name"
            error={profileForm.formState.errors.displayName?.message}
            registration={profileForm.register('displayName')}
          />
          <TextField
            id="avatarUrl"
            label="Avatar URL"
            placeholder="https://…"
            error={profileForm.formState.errors.avatarUrl?.message}
            registration={profileForm.register('avatarUrl')}
          />
          <div>
            <label htmlFor="biography" className="mb-1.5 block text-sm font-medium">
              About you
            </label>
            <textarea
              id="biography"
              rows={3}
              {...profileForm.register('biography')}
              className="w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] p-3 text-sm focus-visible:outline-2"
            />
            {profileForm.formState.errors.biography ? (
              <p role="alert" className="mt-1 text-xs text-red-500">
                {profileForm.formState.errors.biography.message}
              </p>
            ) : null}
          </div>
          <Button type="submit" isLoading={updateProfile.isPending}>
            Save profile
          </Button>
        </form>
      </Panel>

      <Panel title="Appearance">
        <fieldset>
          <legend className="mb-2 text-sm font-medium">Theme</legend>
          <div className="flex gap-2" role="radiogroup" aria-label="Theme">
            {(['light', 'dark', 'system'] as Theme[]).map((option) => (
              <button
                key={option}
                type="button"
                role="radio"
                aria-checked={theme === option}
                onClick={() => setTheme(option)}
                className={cn(
                  'flex-1 rounded-lg border px-4 py-2 text-sm font-medium capitalize transition-colors',
                  theme === option
                    ? 'border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-contrast)]'
                    : 'border-[var(--border-subtle)] hover:border-[var(--accent)]',
                )}
              >
                {option}
              </button>
            ))}
          </div>
        </fieldset>
      </Panel>

      <Panel
        title="Recommendation preferences"
        description="These are applied as filters when your recommendations are generated."
      >
        <fieldset className="mb-4">
          <legend className="mb-2 text-sm font-medium">Favourite genres</legend>
          <div className="flex flex-wrap gap-2">
            {genres.map((genre) => {
              const selected = selectedGenres.includes(genre.slug);
              return (
                <button
                  key={genre.id}
                  type="button"
                  aria-pressed={selected}
                  onClick={() =>
                    setSelectedGenres((current) =>
                      current.includes(genre.slug)
                        ? current.filter((entry) => entry !== genre.slug)
                        : [...current, genre.slug],
                    )
                  }
                  className={cn(
                    'rounded-full border px-3 py-1 text-xs font-medium transition-colors',
                    selected
                      ? 'border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-contrast)]'
                      : 'border-[var(--border-subtle)] text-[var(--text-secondary)] hover:border-[var(--accent)]',
                  )}
                >
                  {genre.name}
                </button>
              );
            })}
          </div>
        </fieldset>

        <Button
          onClick={() =>
            updatePreferences.mutate({
              favouriteGenreSlugs: selectedGenres,
              theme,
            })
          }
          isLoading={updatePreferences.isPending}
        >
          Save preferences
        </Button>
      </Panel>

      <Panel
        title="Change password"
        description="Changing your password signs you out of every other device."
      >
        <form
          onSubmit={passwordForm.handleSubmit((values) => changePassword.mutate(values))}
          className="space-y-4"
          noValidate
        >
          <TextField
            id="currentPassword"
            label="Current password"
            type="password"
            error={passwordForm.formState.errors.currentPassword?.message}
            registration={passwordForm.register('currentPassword')}
          />
          <TextField
            id="newPassword"
            label="New password"
            type="password"
            error={passwordForm.formState.errors.newPassword?.message}
            registration={passwordForm.register('newPassword')}
          />
          <TextField
            id="confirmPassword"
            label="Confirm new password"
            type="password"
            error={passwordForm.formState.errors.confirmPassword?.message}
            registration={passwordForm.register('confirmPassword')}
          />
          <Button type="submit" isLoading={changePassword.isPending}>
            Change password
          </Button>
        </form>
      </Panel>
    </div>
  );
}

function Panel({
  title,
  description,
  children,
}: {
  title: string;
  description?: string;
  children: React.ReactNode;
}) {
  return (
    <section className="rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-6">
      <h2 className="font-semibold">{title}</h2>
      {description ? (
        <p className="mb-4 mt-1 text-sm text-[var(--text-secondary)]">{description}</p>
      ) : (
        <div className="mb-4" />
      )}
      {children}
    </section>
  );
}

function TextField({
  id,
  label,
  type = 'text',
  placeholder,
  error,
  registration,
}: {
  id: string;
  label: string;
  type?: string;
  placeholder?: string;
  error?: string;
  registration: ReturnType<ReturnType<typeof useForm>['register']>;
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        type={type}
        placeholder={placeholder}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        className="h-10 w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-3 text-sm focus-visible:outline-2"
        {...registration}
      />
      {error ? (
        <p id={`${id}-error`} role="alert" className="mt-1 text-xs text-red-500">
          {error}
        </p>
      ) : null}
    </div>
  );
}
