import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { Clapperboard, Eye, EyeOff } from 'lucide-react';
import { useAuth } from '@/hooks/use-auth';
import { useToast } from '@/hooks/use-toast';
import { Button } from '@/components/ui/Button';
import { ApiError } from '@/lib/api-client';

/**
 * Client-side schema.
 *
 * Mirrors the backend constraints for fast feedback. The server remains
 * authoritative: this only avoids a round trip for obvious mistakes.
 */
const loginSchema = z.object({
  email: z.string().min(1, 'Email is required').email('Enter a valid email address'),
  password: z.string().min(1, 'Password is required'),
});

type LoginValues = z.infer<typeof loginSchema>;

export function LoginPage() {
  const { login } = useAuth();
  const { notify } = useToast();
  const navigate = useNavigate();
  const location = useLocation();
  const [showPassword, setShowPassword] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);

  // Return the user to wherever they were headed before the redirect.
  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/';

  const {
    register,
    handleSubmit,
    formState: { errors, isSubmitting },
  } = useForm<LoginValues>({ resolver: zodResolver(loginSchema) });

  const onSubmit = async (values: LoginValues) => {
    setFormError(null);
    try {
      await login(values.email, values.password);
      notify('Welcome back', 'success');
      navigate(redirectTo, { replace: true });
    } catch (error) {
      const message =
        error instanceof ApiError ? error.message : 'Unable to sign in. Please try again.';
      setFormError(message);
    }
  };

  return (
    <div className="mx-auto flex min-h-[calc(100vh-4rem)] max-w-md flex-col justify-center px-4 py-12">
      <div className="mb-8 text-center">
        <Clapperboard className="mx-auto mb-3 h-10 w-10 text-[var(--accent)]" aria-hidden="true" />
        <h1 className="font-[family-name:var(--font-display)] text-2xl font-bold">Welcome back</h1>
        <p className="mt-1 text-sm text-[var(--text-secondary)]">
          Sign in to pick up your watchlist and recommendations.
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

        <div>
          <label htmlFor="email" className="mb-1.5 block text-sm font-medium">
            Email
          </label>
          <input
            id="email"
            type="email"
            autoComplete="email"
            {...register('email')}
            aria-invalid={Boolean(errors.email)}
            aria-describedby={errors.email ? 'email-error' : undefined}
            className="h-10 w-full rounded-lg border border-[var(--border-subtle)] bg-[var(--surface-sunken)] px-3 text-sm focus-visible:outline-2"
          />
          {errors.email ? (
            <p id="email-error" role="alert" className="mt-1 text-xs text-red-500">
              {errors.email.message}
            </p>
          ) : null}
        </div>

        <div>
          <label htmlFor="password" className="mb-1.5 block text-sm font-medium">
            Password
          </label>
          <div className="relative">
            <input
              id="password"
              type={showPassword ? 'text' : 'password'}
              autoComplete="current-password"
              {...register('password')}
              aria-invalid={Boolean(errors.password)}
              aria-describedby={errors.password ? 'password-error' : undefined}
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
          {errors.password ? (
            <p id="password-error" role="alert" className="mt-1 text-xs text-red-500">
              {errors.password.message}
            </p>
          ) : null}
        </div>

        <Button type="submit" className="w-full" isLoading={isSubmitting}>
          Sign in
        </Button>

        <p className="text-center text-sm text-[var(--text-secondary)]">
          New here?{' '}
          <Link to="/register" className="font-medium text-[var(--accent)] hover:underline">
            Create an account
          </Link>
        </p>
      </form>
    </div>
  );
}
