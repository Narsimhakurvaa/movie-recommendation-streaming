import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import axios from 'axios';
import type { AxiosInstance } from 'axios';
import { tokenStore } from '../token-store';

/**
 * Pulls an interceptor handler off the client, asserting it was registered.
 *
 * Axios types every entry in `handlers` as possibly undefined (slots are nulled
 * out when an interceptor is ejected), so a plain index access does not
 * type-check. Failing loudly here also means a test cannot silently pass
 * because the interceptor it meant to exercise was never installed.
 */
function requestHandler(client: AxiosInstance) {
  const handler = client.interceptors.request.handlers?.[0];
  if (!handler?.fulfilled) throw new Error('no request interceptor is registered');
  return handler.fulfilled;
}

function responseRejectionHandler(client: AxiosInstance) {
  const handler = client.interceptors.response.handlers?.[0];
  if (!handler?.rejected) throw new Error('no response rejection interceptor is registered');
  return handler.rejected as (error: unknown) => Promise<unknown>;
}

/**
 * Behaviour of the shared HTTP client.
 *
 * The refresh path is the riskiest code in the frontend: the backend treats a
 * reused refresh token as theft and revokes the whole session, so a client that
 * fires concurrent refreshes would log people out at random. These tests pin
 * that behaviour down.
 */
describe('apiClient', () => {
  beforeEach(() => {
    vi.resetModules();
    window.localStorage.clear();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('attaches the access token as a bearer header', async () => {
    tokenStore.set({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      user: {
        id: 1, email: 'a@example.com', displayName: 'A', avatarUrl: null,
        roles: ['ROLE_USER'], emailVerified: true, onboardingCompleted: true,
      },
    });

    const { apiClient } = await import('../api-client');
    const config = await requestHandler(apiClient)({
      url: '/movies',
      headers: new axios.AxiosHeaders(),
    });

    expect(config.headers.get('Authorization')).toBe('Bearer access-1');
  });

  it('never attaches a token to the login or refresh endpoints', async () => {
    tokenStore.set({
      accessToken: 'access-1',
      refreshToken: 'refresh-1',
      user: {
        id: 1, email: 'a@example.com', displayName: 'A', avatarUrl: null,
        roles: ['ROLE_USER'], emailVerified: true, onboardingCompleted: true,
      },
    });

    const { apiClient } = await import('../api-client');
    for (const url of ['/auth/login', '/auth/refresh', '/auth/register']) {
      const config = await requestHandler(apiClient)({
        url,
        headers: new axios.AxiosHeaders(),
      });
      expect(config.headers.get('Authorization')).toBeFalsy();
    }
  });

  it('reports a network failure as a retryable error rather than throwing raw axios', async () => {
    const { apiClient, ApiError } = await import('../api-client');
    const rejected = responseRejectionHandler(apiClient);

    await expect(rejected({ config: { url: '/movies' }, response: undefined }))
      .rejects.toMatchObject({ status: 0, code: 'NETWORK_ERROR' });

    const error = await rejected({ config: { url: '/movies' }, response: undefined })
      .catch((e: unknown) => e);
    expect(error).toBeInstanceOf(ApiError);
    expect((error as InstanceType<typeof ApiError>).isRetryable).toBe(true);
  });

  it('distinguishes a timeout from a generic network failure', async () => {
    const { apiClient } = await import('../api-client');
    const rejected = responseRejectionHandler(apiClient);

    await expect(
      rejected({ config: { url: '/movies' }, response: undefined, code: 'ECONNABORTED' }),
    ).rejects.toMatchObject({ code: 'TIMEOUT' });
  });

  it('surfaces field-level validation errors keyed by field name', async () => {
    const { apiClient } = await import('../api-client');
    const rejected = responseRejectionHandler(apiClient);

    const error = await rejected({
      config: { url: '/auth/register' },
      response: {
        status: 400,
        data: {
          status: 400,
          code: 'VALIDATION_FAILED',
          message: 'Request validation failed',
          validationErrors: [{ field: 'email', message: 'must be a well-formed email address' }],
        },
      },
    }).catch((e: unknown) => e);

    expect((error as { fieldErrors: Record<string, string> }).fieldErrors).toEqual({
      email: 'must be a well-formed email address',
    });
  });

  it('does not treat a 4xx as retryable', async () => {
    const { apiClient, ApiError } = await import('../api-client');
    const rejected = responseRejectionHandler(apiClient);

    const error = (await rejected({
      config: { url: '/movies/999' },
      response: { status: 404, data: { status: 404, code: 'RESOURCE_NOT_FOUND', message: 'Movie not found: 999' } },
    }).catch((e: unknown) => e)) as InstanceType<typeof ApiError>;

    expect(error.status).toBe(404);
    expect(error.isRetryable).toBe(false);
  });

  it('clears the session and signals expiry when refresh is impossible', async () => {
    // No refresh token stored, so the 401 cannot be recovered from.
    const { apiClient } = await import('../api-client');
    const rejected = responseRejectionHandler(apiClient);
    const listener = vi.fn();
    window.addEventListener('cinevault:session-expired', listener);

    await expect(
      rejected({
        config: { url: '/watchlist', headers: {} },
        response: { status: 401, data: { status: 401, code: 'AUTHENTICATION_REQUIRED', message: 'Unauthorized' } },
      }),
    ).rejects.toMatchObject({ code: 'SESSION_EXPIRED' });

    expect(listener).toHaveBeenCalled();
    expect(tokenStore.isAuthenticated()).toBe(false);
    window.removeEventListener('cinevault:session-expired', listener);
  });

  it('refreshes once and replays the original request', async () => {
    tokenStore.set({
      accessToken: 'expired',
      refreshToken: 'refresh-1',
      user: {
        id: 1, email: 'a@example.com', displayName: 'A', avatarUrl: null,
        roles: ['ROLE_USER'], emailVerified: true, onboardingCompleted: true,
      },
    });

    const postSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        accessToken: 'fresh',
        refreshToken: 'refresh-2',
        user: {
          id: 1, email: 'a@example.com', displayName: 'A', avatarUrl: null,
          roles: ['ROLE_USER'], emailVerified: true, onboardingCompleted: true,
        },
      },
    });

    const { apiClient } = await import('../api-client');
    const requestSpy = vi.spyOn(apiClient, 'request').mockResolvedValue({ data: 'ok' });
    const rejected = responseRejectionHandler(apiClient);

    await rejected({
      config: { url: '/watchlist', headers: {} },
      response: { status: 401, data: {} },
    });

    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(requestSpy).toHaveBeenCalledTimes(1);
    // The rotated token must replace the old one.
    expect(tokenStore.getAccessToken()).toBe('fresh');
    expect(tokenStore.getRefreshToken()).toBe('refresh-2');
  });

  it('issues only ONE refresh for concurrent 401s (reuse detection would revoke the session)', async () => {
    tokenStore.set({
      accessToken: 'expired',
      refreshToken: 'refresh-1',
      user: {
        id: 1, email: 'a@example.com', displayName: 'A', avatarUrl: null,
        roles: ['ROLE_USER'], emailVerified: true, onboardingCompleted: true,
      },
    });

    let resolveRefresh: (value: unknown) => void = () => {};
    const pending = new Promise((resolve) => {
      resolveRefresh = resolve;
    });
    const postSpy = vi.spyOn(axios, 'post').mockImplementation(() => pending as never);

    const { apiClient } = await import('../api-client');
    vi.spyOn(apiClient, 'request').mockResolvedValue({ data: 'ok' });
    const rejected = responseRejectionHandler(apiClient);

    // Five requests fail with 401 simultaneously, as happens on a page load.
    const attempts = [1, 2, 3, 4, 5].map((n) =>
      rejected({ config: { url: `/endpoint-${n}`, headers: {} }, response: { status: 401, data: {} } }),
    );

    resolveRefresh({
      data: {
        accessToken: 'fresh',
        refreshToken: 'refresh-2',
        user: {
          id: 1, email: 'a@example.com', displayName: 'A', avatarUrl: null,
          roles: ['ROLE_USER'], emailVerified: true, onboardingCompleted: true,
        },
      },
    });
    await Promise.all(attempts);

    expect(postSpy).toHaveBeenCalledTimes(1);
  });

  it('does not retry a request that has already been retried once', async () => {
    tokenStore.set({
      accessToken: 'expired',
      refreshToken: 'refresh-1',
      user: {
        id: 1, email: 'a@example.com', displayName: 'A', avatarUrl: null,
        roles: ['ROLE_USER'], emailVerified: true, onboardingCompleted: true,
      },
    });

    const postSpy = vi.spyOn(axios, 'post');
    const { apiClient } = await import('../api-client');
    const rejected = responseRejectionHandler(apiClient);

    // `_retried` marks a request that already went through the refresh path.
    await expect(
      rejected({
        config: { url: '/watchlist', headers: {}, _retried: true },
        response: { status: 401, data: { code: 'AUTHENTICATION_REQUIRED', message: 'Unauthorized' } },
      }),
    ).rejects.toMatchObject({ status: 401 });

    expect(postSpy).not.toHaveBeenCalled();
  });
});
