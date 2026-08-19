import { http } from '@/lib/api-client';
import type {
  Genre,
  MovieDetail,
  MovieFilters,
  MovieSuggestion,
  MovieSummary,
  PageResponse,
  RecommendationItem,
  Review,
} from '@/types/api';

/** Turns a filter object into query parameters, omitting empty values. */
function toParams(filters: MovieFilters, page: number, size: number): URLSearchParams {
  const params = new URLSearchParams();
  params.set('page', String(page));
  params.set('size', String(size));

  if (filters.query) params.set('query', filters.query);
  if (filters.sort) params.set('sort', filters.sort);
  if (filters.yearFrom) params.set('yearFrom', String(filters.yearFrom));
  if (filters.yearTo) params.set('yearTo', String(filters.yearTo));
  if (filters.minRating) params.set('minRating', String(filters.minRating));
  if (filters.maxRating) params.set('maxRating', String(filters.maxRating));
  if (filters.minRuntime) params.set('minRuntime', String(filters.minRuntime));
  if (filters.maxRuntime) params.set('maxRuntime', String(filters.maxRuntime));
  // Repeated keys, which Spring binds to a List.
  filters.genres?.forEach((g) => params.append('genres', g));
  filters.languages?.forEach((l) => params.append('languages', l));
  return params;
}

export const movieService = {
  discover: (filters: MovieFilters, page = 0, size = 20) =>
    http.get<PageResponse<MovieSummary>>(`/movies?${toParams(filters, page, size)}`),

  search: (query: string, filters: MovieFilters = {}, page = 0, size = 20) => {
    const params = toParams({ ...filters, query }, page, size);
    return http.get<PageResponse<MovieSummary>>(`/movies/search?${params}`);
  },

  suggest: (query: string, limit = 8) =>
    http.get<MovieSuggestion[]>(
      `/movies/suggest?query=${encodeURIComponent(query)}&limit=${limit}`,
    ),

  detail: (movieId: number) => http.get<MovieDetail>(`/movies/${movieId}`),

  reviews: (movieId: number, page = 0, size = 10, sort = 'newest') =>
    http.get<PageResponse<Review>>(
      `/movies/${movieId}/reviews?page=${page}&size=${size}&sort=${sort}`,
    ),

  genres: () => http.get<Genre[]>('/genres'),

  byGenre: (slug: string, page = 0, size = 20) =>
    http.get<PageResponse<MovieSummary>>(`/genres/${slug}/movies?page=${page}&size=${size}`),

  similar: (movieId: number, size = 12) =>
    http.get<PageResponse<RecommendationItem>>(
      `/recommendations/similar/${movieId}?size=${size}`,
    ),
};

export const recommendationService = {
  forMe: (page = 0, size = 20) =>
    http.get<PageResponse<RecommendationItem>>(`/recommendations?page=${page}&size=${size}`),

  trending: (size = 20) =>
    http.get<PageResponse<MovieSummary>>(`/recommendations/trending?size=${size}`),

  popular: (size = 20) =>
    http.get<PageResponse<MovieSummary>>(`/recommendations/popular?size=${size}`),

  topRated: (size = 20) =>
    http.get<PageResponse<MovieSummary>>(`/recommendations/top-rated?size=${size}`),

  newReleases: (size = 20) =>
    http.get<PageResponse<MovieSummary>>(`/recommendations/new-releases?size=${size}`),
};
