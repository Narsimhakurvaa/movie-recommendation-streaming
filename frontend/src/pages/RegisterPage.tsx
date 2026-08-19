import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { useQuery } from '@tanstack/react-query';
import { Check, Clapperboard, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '@/hooks/use-auth';
import { useToast } from '@/hooks/use-toast';
import { movieService } from '@/services/movies';
import { Button } from '@/components/ui/Button';
import { ApiError } from '@/lib/api-client';
import { cn } from '@/lib/utils';

/**
 * Registration schema, mirroring the backend's Bean Validation rules exactly.
 * Divergence here would produce errors the user cannot resolve from the form.
 */
const registerSchema = z
  .object({
    displayName: z
      .string()
      .min(2, 'Name must be at least 2 characters')
      .max(80, 'Name must be 80 characters or fewer'),
    email: z.string().min(1, 'Email is required').email('Enter a valid email address'),
    password: z
      .string()
      .min(12, 'Use at least 12 characters')
      .max(128, 'Use 128 characters or fewer')
      .regex(/[A-Z]/, 'Include an uppercase letter')
      .regex(/[a-z]/, 'Include a lowercase letter')
      .regex(/\d/, 'Include a digit'),
    confirmPassword: z.string(),
  })
  .refine((values) => values.password === values.confirmPassword, {
    message: 'Passwords do not match',
    path: ['confirmPassword'],
  });

type RegisterValues = z.infer<typeof registerSchema>;

export function RegisterPage() {
  const { register: signUp } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const [showPassword, setShowPassword] = useState(false);
  const [selectedGenres, setSelectedGenres] = useState<string[]>([]);
  const [formError, setFormError] = useState<string | null>(null);

  const { data: genres = [] } = useQuery({
    queryKey: ['genres'],
    queryFn: movieService.genres,
    staleTime: 30 * 60_000,
  });

  const {
    register,
    handleSubmit,
    watch,
    formState: { errors, isSubmitting },
  } = useForm<RegisterValues>({ resolver: zodResolver(registerSchema), mode: 'onBlur' });

  const password = watch('password') ?? '';

  const toggleGenre = (slug: string) => {
    setSelectedGenres((current) =>
      current.includes(slug)
        ? current.filter((entry) => entry !== slug)
        : // Cap at five: beyond that the cold-start signal stops discriminating.
          current.length >= 5
          ? current
          : [...current, slug],
    );
  };

  const onSubmit = async (values: RegisterValues) => {
    setFormError(null);
    try {
      await signUp({
        email: values.email,
        password: values.password,
        displayName: values.displayName,
        favouriteGenreSlugs: selectedGenres,
      });
      notify('Welcome to CineVault', 'success');
      navigate('/', { replace: true });
    } catch (error) {
      setFormError(
        error instanceof ApiError ? error.message : 'Unable to create your account.',
      );
    }
  };

  return (
    <div className="mx-auto max-w-lg px-4 py-12">
      <div className="mb-8 text-center">
        <Clapperboard className="mx-auto mb-3 h-10 w-10 text-[var(--accent)]" aria-hidden="true" />
        <h1 className="font-[family-name:var(--font-display)] text-2xl font-bold">
          Create your account
        </h1>
        <p className="mt-1 text-sm text-[var(--text-secondary)]">
          Pick a few genres and your first recommendations will already be relevant.
        </p>
      </div>

      <form
        onSubmit={handleSubmit(onSubmit)}
        className="space-y-4 rounded-[var(--radius-card)] border border-[var(--border-subtle)] bg-[var(--surface-raised)] p-6"
        noValidate
      >
        {formError ? (
          <div
            role="alert"
            className="rounded-lg border border-red-500/40 bg-red-500/10 px-3 py-2 text-sm text-red-700 dark:text-red-300"
          >
            {formError}
          </div>
        ) : null}

        <Field
          id="displayName"
          label="Display name"
          error={errors.displayName?.message}
          inputProps={{ autoComplete: 'name', ...register('displayName') }}
        />

        <Field
          id="email"
          label="Email"
          error={errors.email?.message}
          inputProps={{ type: 'email', autoComplete: 'email', ...register('email') }}
        />

        <div>
          <label htmlFor="password" className="mb-1.5 block text-sm font-medium">
            Password
          </label>
          <div className="relative">
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="new-password"
              {...register('password')}
              aria-invalid={Boolean(errors.password)}
              aria-describedby="password-requirements"
              className="h-10 w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-3 pr-10 text-sm focus-visible:outline-2"
            />
            <button
              type="button"
              onClick={() => setShowPassword((shown) => !shown)}
              aria-label={showPassword ? 'Hide password' : 'Show password'}
              className="absolute right-2 top-1/2 -translate-y-1/2 rounded p-1 text-[var(--text-muted)] hover:text-[var(--text-primary)]"
            >
              {showPassword ? <EyeOff className="h-4 w-4" /> : <Eye className="h-4 w-4" />}
            </button>
          </div>
          {/* Live checklist: clearer than a single error at submit time. */}
          <ul id="password-requirements" className="mt-2 space-y-1">
            <Requirement met={password.length >= 12} label="At least 12 characters" />
            <Requirement met={/[A-Z]/.test(password)} label="An uppercase letter" />
            <Requirement met={/[a-z]/.test(password)} label="A lowercase letter" />
            <Requirement met={/\d/.test(password)} label="A digit" />
          </ul>
        </div>

        <Field
          id="confirmPassword"
          label="Confirm password"
          error={errors.confirmPassword?.message}
          inputProps={{
            type: showPassword ? 'text' : 'password',
            autoComplete: 'new-password',
            ...register('confirmPassword'),
          }}
        />

        <fieldset>
          <legend className="mb-2 text-sm font-medium">
            Favourite genres{' '}
            <span className="font-normal text-[var(--text-muted)]">
              (optional, up to 5 — {selectedGenres.length} selected)
            </span>
          </legend>
          <div className="flex flex-wrap gap-2">
            {genres.slice(0, 14).map((genre) => {
              const selected = selectedGenres.includes(genre.slug);
              return (
                <button
                  key={genre.id}
                  type="button"
                  onClick={() => toggleGenre(genre.slug)}
                  aria-pressed={selected}
                  className={cn(
                    'rounded-full border px-3 py-1.5 text-xs font-medium transition-colors',
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

        <Button type="submit" className="w-full" isLoading={isSubmitting}>
          Create account
        </Button>

        <p className="text-center text-sm text-[var(--text-secondary)]">
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-[var(--accent)] hover:underline">
            Sign in
          </Link>
        </p>
      </form>
    </div>
  );
}

function Field({
  id,
  label,
  error,
  inputProps,
}: {
  id: string;
  label: string;
  error?: string;
  inputProps: React.InputHTMLAttributes<HTMLInputElement>;
}) {
  return (
    <div>
      <label htmlFor={id} className="mb-1.5 block text-sm font-medium">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={Boolean(error)}
        aria-describedby={error ? `${id}-error` : undefined}
        className="h-10 w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-3 text-sm focus-visible:outline-2"
        {...inputProps}
      />
      {error ? (
        <p id={`${id}-error`} role="alert" className="mt-1 text-xs text-red-500">
          {error}
        </p>
      ) : null}
    </div>
  );
}

function Requirement({ met, label }: { met: boolean; label: string }) {
  return (
    <li
      className={cn(
        'flex items-center gap-1.5 text-xs',
        met ? 'text-emerald-600 dark:text-emerald-400' : 'text-[var(--text-muted)]',
      )}
    >
      <Check className={cn('h-3 w-3', met ? 'opacity-100' : 'opacity-30')} aria-hidden="true" />
      {label}
      <span className="sr-only">{met ? ' (met)' : ' (not yet met)'}</span>
    </li>
  );
}
