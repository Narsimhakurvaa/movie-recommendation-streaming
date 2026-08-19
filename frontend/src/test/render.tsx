import type { ReactElement, ReactNode } from 'react';
import { render, type RenderOptions } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@/hooks/use-theme';
import { ToastProvider } from '@/hooks/use-toast';
import { AuthProvider } from '@/hooks/use-auth';

/**
 * Renders a component inside the providers the application supplies at runtime.
 *
 * Retries are disabled so a deliberately failing request surfaces immediately
 * rather than after the production backoff schedule.
 */
export function renderWithProviders(
  ui: ReactElement,
  { route = '/', ...options }: RenderOptions & { route?: string } = {},
) {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, gcTime: 0, staleTime: 0 },
      mutations: { retry: false },
    },
  });

  function Wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <ToastProvider>
            <MemoryRouter initialEntries={[route]}>
              <AuthProvider>{children}</AuthProvider>
            </MemoryRouter>
          </ToastProvider>
        </ThemeProvider>
      </QueryClientProvider>
    );
  }

  return { queryClient, ...render(ui, { wrapper: Wrapper, ...options }) };
}

/** A movie fixture; override only the fields a test cares about. */
export function buildMovie(overrides: Partial<import('@/types/api').MovieSummary> = {}) {
  return {
    id: 1,
    title: 'Interstellar',
    slug: 'interstellar-2014',
    releaseYear: 2014,
    posterUrl: 'https://example.com/poster.jpg',
    externalRating: 8.4,
    averageRating: 4.6,
    ratingCount: 128,
    runtimeMinutes: 169,
    genres: ['Science Fiction', 'Drama'],
    inWatchlist: false,
    userRating: null,
    ...overrides,
  };
}
