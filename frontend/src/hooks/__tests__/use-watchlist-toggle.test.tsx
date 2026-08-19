import { describe, expect, it, vi, beforeEach } from 'vitest';
import { renderHook, waitFor, act } from '@testing-library/react';
import type { ReactNode } from 'react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router-dom';
import { ToastProvider } from '@/hooks/use-toast';
import { AuthProvider } from '@/hooks/use-auth';
import { ThemeProvider } from '@/hooks/use-theme';
import { useWatchlistToggle } from '../use-watchlist-toggle';
import { watchlistService } from '@/services/interactions';
import { tokenStore } from '@/lib/token-store';
import { ApiError } from '@/lib/api-client';
import { buildMovie } from '@/test/render';

vi.mock('@/services/interactions', () => ({
  watchlistService: { add: vi.fn(), remove: vi.fn(), list: vi.fn() },
}));

const addMock = vi.mocked(watchlistService.add);
const removeMock = vi.mocked(watchlistService.remove);

function signIn() {
  tokenStore.set({
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    user: {
      id: 1, email: 'a@example.com', displayName: 'A', avatarUrl: null,
      roles: ['ROLE_USER'], emailVerified: true, onboardingCompleted: true,
    },
  });
}

function setup() {
  const queryClient = new QueryClient({
    defaultOptions: {
      // gcTime must not be 0: entries seeded with setQueryData have no
      // observers, and a zero gcTime would collect them before the hook runs.
      queries: { retry: false, gcTime: Infinity, staleTime: Infinity },
      mutations: { retry: false },
    },
  });

  function wrapper({ children }: { children: ReactNode }) {
    return (
      <QueryClientProvider client={queryClient}>
        <ThemeProvider>
          <ToastProvider>
            <MemoryRouter>
              <AuthProvider>{children}</AuthProvider>
            </MemoryRouter>
          </ToastProvider>
        </ThemeProvider>
      </QueryClientProvider>
    );
  }

  const view = renderHook(() => useWatchlistToggle(), { wrapper });
  return { ...view, queryClient };
}

/** Reads inWatchlist for the movie out of a cached discover page. */
function cachedFlag(queryClient: QueryClient, key: unknown[]) {
  const data = queryClient.getQueryData(key) as { content: { inWatchlist: boolean }[] } | undefined;
  return data?.content[0].inWatchlist;
}

describe('useWatchlistToggle', () => {
  beforeEach(() => {
    window.localStorage.clear();
    addMock.mockReset();
    removeMock.mockReset();
  });

  it('does not call the API when signed out', async () => {
    const { result } = setup();

    act(() => result.current.toggle(buildMovie()));

    await waitFor(() => expect(addMock).not.toHaveBeenCalled());
  });

  it('adds a film that is not yet saved', async () => {
    signIn();
    addMock.mockResolvedValue(undefined as never);
    const { result } = setup();

    act(() => result.current.toggle(buildMovie({ inWatchlist: false })));

    await waitFor(() => expect(addMock).toHaveBeenCalledWith(1));
    expect(removeMock).not.toHaveBeenCalled();
  });

  it('removes a film that is already saved', async () => {
    signIn();
    removeMock.mockResolvedValue(undefined as never);
    const { result } = setup();

    act(() => result.current.toggle(buildMovie({ inWatchlist: true })));

    await waitFor(() => expect(removeMock).toHaveBeenCalledWith(1));
    expect(addMock).not.toHaveBeenCalled();
  });

  it('patches every cached list holding the movie, before the request resolves', async () => {
    signIn();
    let resolveAdd: () => void = () => {};
    addMock.mockImplementation(() => new Promise<void>((resolve) => { resolveAdd = resolve; }) as never);

    const { result, queryClient } = setup();
    const gridKey = ['movies', 'discover', {}, 0];
    const railKey = ['movies', 'trending'];
    queryClient.setQueryData(gridKey, { content: [buildMovie({ inWatchlist: false })] });
    queryClient.setQueryData(railKey, { content: [buildMovie({ inWatchlist: false })] });

    act(() => result.current.toggle(buildMovie({ inWatchlist: false })));

    // Optimistic: both caches flip while the request is still in flight.
    await waitFor(() => expect(cachedFlag(queryClient, gridKey)).toBe(true));
    expect(cachedFlag(queryClient, railKey)).toBe(true);

    await act(async () => { resolveAdd(); });
  });

  it('rolls the cache back when the request fails', async () => {
    signIn();
    addMock.mockRejectedValue(
      new ApiError({ code: 'INTERNAL_ERROR', message: 'Server exploded' }, 500),
    );

    const { result, queryClient } = setup();
    const gridKey = ['movies', 'discover', {}, 0];
    queryClient.setQueryData(gridKey, { content: [buildMovie({ inWatchlist: false })] });

    act(() => result.current.toggle(buildMovie({ inWatchlist: false })));

    await waitFor(() => expect(addMock).toHaveBeenCalled());
    // An optimistic update must never outlive a failed request.
    await waitFor(() => expect(cachedFlag(queryClient, gridKey)).toBe(false));
  });

  it('reports the pending movie so the button can disable itself', async () => {
    signIn();
    let resolveAdd: () => void = () => {};
    addMock.mockImplementation(() => new Promise<void>((resolve) => { resolveAdd = resolve; }) as never);

    const { result } = setup();
    expect(result.current.pendingMovieId).toBeNull();

    act(() => result.current.toggle(buildMovie()));

    await waitFor(() => expect(result.current.pendingMovieId).toBe(1));
    await act(async () => { resolveAdd(); });
    await waitFor(() => expect(result.current.pendingMovieId).toBeNull());
  });
});
