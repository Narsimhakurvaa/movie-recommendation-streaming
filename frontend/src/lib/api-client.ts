import axios, {
  AxiosError,
  type AxiosInstance,
  type AxiosRequestConfig,
  type InternalAxiosRequestConfig,
} from 'axios';
import { tokenStore } from './token-store';

/**
 * Shared HTTP client.
 *
 * Uses a relative base URL so the browser always talks to the origin it was
 * served from: the Vite dev server proxies `/api` in development, and a reverse
 * proxy does the same in production. Pointing the browser at `localhost:8080`
 * would break the moment the app is opened from anywhere but the API host.
 */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api';

/** Error shape returned by the backend's global exception handler. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  correlationId?: string;
  validationErrors?: Array<{ field: string; message: string }>;
}

/**
 * Normalised error thrown by every request.
 *
 * Callers get one predictable type whether the failure was an HTTP status, a
 * network drop, or a timeout.
 */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: Record<string, string>;
  readonly correlationId?: string;

  constructor(body: Partial<ApiErrorBody>, status: number) {
    super(body.message ?? 'Something went wrong. Please try again.');
    this.name = 'ApiError';
    this.status = status;
    this.code = body.code ?? 'UNKNOWN';
    this.correlationId = body.correlationId;
    this.fieldErrors = Object.fromEntries(
      (body.validationErrors ?? []).map((v) => [v.field, v.message]),
    );
  }

  /** True when retrying is plausible (network blip or server-side fault). */
  get isRetryable(): boolean {
    return this.status === 0 || this.status >= 500;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }
}

export const apiClient: AxiosInstance = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15_000,
  headers: { 'Content-Type': 'application/json' },
});

/** Endpoints that must never carry a token or trigger a refresh. */
const AUTH_FREE_PATHS = ['/auth/login', '/auth/register', '/auth/refresh'];

apiClient.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = tokenStore.getAccessToken();
  const isAuthFree = AUTH_FREE_PATHS.some((path) => config.url?.includes(path));
  if (token && !isAuthFree) {
    config.headers.set('Authorization', `Bearer ${token}`);
  }
  return config;
});

/*
 * Single-flight refresh.
 *
 * When several requests fail with 401 at once, only the first triggers a
 * refresh; the rest await the same promise. Without this, a page issuing five
 * parallel requests would fire five refreshes, and because refresh tokens are
 * single-use with reuse detection, four of them would be treated as token theft
 * and log the user out entirely.
 */
let refreshPromise: Promise<string> | null = null;

async function refreshAccessToken(): Promise<string> {
  const refreshToken = tokenStore.getRefreshToken();
  if (!refreshToken) {
    throw new ApiError({ message: 'Session expired', code: 'NO_REFRESH_TOKEN' }, 401);
  }

  // A bare axios call, so this request cannot recurse through the interceptor.
  const response = await axios.post(
    `${API_BASE_URL}/auth/refresh`,
    { refreshToken },
    { headers: { 'Content-Type': 'application/json' }, timeout: 15_000 },
  );
  const { accessToken, refreshToken: rotated, user } = response.data;
  tokenStore.set({ accessToken, refreshToken: rotated, user });
  return accessToken;
}

apiClient.interceptors.response.use(
  (response) => response,
  async (error: AxiosError<ApiErrorBody>) => {
    const original = error.config as (AxiosRequestConfig & { _retried?: boolean }) | undefined;

    // No response at all: DNS failure, offline, CORS, or timeout.
    if (!error.response) {
      const isTimeout = error.code === 'ECONNABORTED';
      return Promise.reject(
        new ApiError(
          {
            message: isTimeout
              ? 'The request timed out. Please check your connection and try again.'
              : 'Unable to reach the server. Please check your connection.',
            code: isTimeout ? 'TIMEOUT' : 'NETWORK_ERROR',
          },
          0,
        ),
      );
    }

    const { status, data } = error.response;
    const isAuthFree = AUTH_FREE_PATHS.some((path) => original?.url?.includes(path));

    if (status === 401 && original && !original._retried && !isAuthFree) {
      original._retried = true;
      try {
        refreshPromise ??= refreshAccessToken().finally(() => {
          refreshPromise = null;
        });
        const token = await refreshPromise;
        original.headers = { ...original.headers, Authorization: `Bearer ${token}` };
        return apiClient.request(original);
      } catch {
        // Refresh failed: the session is genuinely over.
        tokenStore.clear();
        window.dispatchEvent(new CustomEvent('cinevault:session-expired'));
        return Promise.reject(
          new ApiError({ message: 'Your session has expired. Please sign in again.', code: 'SESSION_EXPIRED' }, 401),
        );
      }
    }

    return Promise.reject(new ApiError(data ?? {}, status));
  },
);

/** Thin typed helpers over the client. */
export const http = {
  get: <T>(url: string, config?: AxiosRequestConfig) =>
    apiClient.get<T>(url, config).then((r) => r.data),
  post: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    apiClient.post<T>(url, body, config).then((r) => r.data),
  put: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    apiClient.put<T>(url, body, config).then((r) => r.data),
  patch: <T>(url: string, body?: unknown, config?: AxiosRequestConfig) =>
    apiClient.patch<T>(url, body, config).then((r) => r.data),
  delete: <T>(url: string, config?: AxiosRequestConfig) =>
    apiClient.delete<T>(url, config).then((r) => r.data),
};
