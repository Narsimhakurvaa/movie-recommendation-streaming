import { http } from '@/lib/api-client';
import type {
  PageResponse,
  RatingResponse,
  Review,
  WatchHistoryEntry,
  WatchlistItem,
} from '@/types/api';

export const watchlistService = {
  list: (page = 0, size = 20, direction: 'asc' | 'desc' = 'desc') =>
    http.get<PageResponse<WatchlistItem>>(
      `/watchlist?page=${page}&size=${size}&direction=${direction}`,
    ),

  add: (movieId: number, note?: string) =>
    http.post<WatchlistItem>(`/watchlist/${movieId}`, note ? { note } : {}),

  remove: (movieId: number) => http.delete<void>(`/watchlist/${movieId}`),

  status: (movieId: number) =>
    http.get<{ movieId: number; saved: boolean }>(`/watchlist/${movieId}/status`),
};

export const ratingService = {
  rate: (movieId: number, score: number) =>
    http.post<RatingResponse>(`/movies/${movieId}/ratings`, { score }),

  remove: (movieId: number) => http.delete<void>(`/movies/${movieId}/ratings`),
};

export const reviewService = {
  create: (movieId: number, body: { title?: string; body: string; containsSpoilers?: boolean }) =>
    http.post<Review>(`/movies/${movieId}/reviews`, body),

  update: (reviewId: number, body: { title?: string; body: string; containsSpoilers?: boolean }) =>
    http.put<Review>(`/reviews/${reviewId}`, body),

  remove: (reviewId: number) => http.delete<void>(`/reviews/${reviewId}`),

  mine: (page = 0, size = 10) =>
    http.get<PageResponse<Review>>(`/reviews/mine?page=${page}&size=${size}`),
};

export const historyService = {
  list: (page = 0, size = 20) =>
    http.get<PageResponse<WatchHistoryEntry>>(`/history?page=${page}&size=${size}`),

  /**
   * Reports an interaction.
   *
   * Failures are swallowed deliberately: analytics must never surface an error
   * to the user or block the action that triggered it.
   */
  record: (movieId: number, interactionType: string, progressPercent?: number) =>
    http
      .post<void>(`/history/movies/${movieId}`, { interactionType, progressPercent })
      .catch(() => undefined),
};
