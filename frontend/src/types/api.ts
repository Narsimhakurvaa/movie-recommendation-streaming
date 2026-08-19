/**
 * Types mirroring the backend DTOs.
 *
 * Hand-written rather than generated from the OpenAPI document: the surface is
 * small enough that generation would add a build step and a lot of noise for
 * little benefit, and these stay readable at review time.
 */

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  first: boolean;
  last: boolean;
}

export interface MovieSummary {
  id: number;
  title: string;
  slug: string;
  releaseYear: number | null;
  posterUrl: string | null;
  externalRating: number;
  averageRating: number;
  ratingCount: number;
  runtimeMinutes: number | null;
  genres: string[];
  inWatchlist: boolean | null;
  userRating: number | null;
}

export interface CreditSummary {
  personId: number;
  name: string;
  characterName: string | null;
  job: string | null;
  profileUrl: string | null;
}

export interface MovieDetail {
  id: number;
  title: string;
  originalTitle: string | null;
  slug: string;
  tagline: string | null;
  overview: string | null;
  releaseDate: string | null;
  releaseYear: number | null;
  runtimeMinutes: number | null;
  originalLanguage: string | null;
  originCountry: string | null;
  status: string;
  posterUrl: string | null;
  backdropUrl: string | null;
  trailerUrl: string | null;
  homepageUrl: string | null;
  externalRating: number;
  externalVoteCount: number;
  averageRating: number;
  ratingCount: number;
  popularity: number;
  budget: number | null;
  revenue: number | null;
  adult: boolean;
  productionCompanies: string[];
  genres: string[];
  keywords: string[];
  cast: CreditSummary[];
  directors: CreditSummary[];
  writers: CreditSummary[];
  inWatchlist: boolean | null;
  userRating: number | null;
}

export interface MovieSuggestion {
  id: number;
  title: string;
  slug: string;
  posterUrl: string | null;
  releaseYear: number | null;
}

export interface Genre {
  id: number;
  name: string;
  slug: string;
  movieCount: number;
}

export type RecommendationType =
  | 'HYBRID'
  | 'CONTENT_BASED'
  | 'COLLABORATIVE'
  | 'POPULARITY'
  | 'COLD_START'
  | 'SIMILAR';

export interface RecommendationItem {
  movie: MovieSummary;
  score: number;
  /** Derived from the signals that actually drove the ranking. */
  reason: string;
  recommendationType: RecommendationType;
}

export interface RecommendationHistoryItem {
  movie: MovieSummary;
  score: number;
  reason: string;
  recommendationType: string;
  generatedAt: string;
}

export interface AuthenticatedUser {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  roles: string[];
  emailVerified: boolean;
  onboardingCompleted: boolean;
}

export interface AuthResponse {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
  expiresIn: number;
  user: AuthenticatedUser;
}

export interface WatchlistItem {
  id: number;
  movie: MovieSummary;
  note: string | null;
  addedAt: string;
}

export interface RatingResponse {
  movieId: number;
  score: number;
  movieAverageRating: number;
  movieRatingCount: number;
  updatedAt: string;
}

export interface ReviewAuthor {
  id: number;
  displayName: string;
  avatarUrl: string | null;
}

export interface Review {
  id: number;
  movieId: number;
  movieTitle: string;
  author: ReviewAuthor;
  title: string | null;
  body: string;
  containsSpoilers: boolean;
  status: 'PUBLISHED' | 'HIDDEN' | 'FLAGGED';
  authorRating: number | null;
  ownedByCurrentUser: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface WatchHistoryEntry {
  id: number;
  movie: MovieSummary;
  interactionType: string;
  progressPercent: number | null;
  occurredAt: string;
}

export interface ActivitySummary {
  ratingCount: number;
  reviewCount: number;
  watchlistCount: number;
  historyCount: number;
}

export interface Preferences {
  preferredLanguages: string[];
  includeAdult: boolean;
  minimumRating: number;
  preferredDecadeFrom: number | null;
  preferredDecadeTo: number | null;
  theme: 'light' | 'dark' | 'system';
  emailNotifications: boolean;
}

export interface FavouriteGenre {
  id: number;
  name: string;
  slug: string;
  weight: number;
}

export interface UserProfile {
  id: number;
  email: string;
  displayName: string;
  avatarUrl: string | null;
  biography: string | null;
  roles: string[];
  emailVerified: boolean;
  onboardingCompleted: boolean;
  createdAt: string;
  lastLoginAt: string | null;
  activity: ActivitySummary;
  preferences: Preferences;
  favouriteGenres: FavouriteGenre[];
}

export interface MovieFilters {
  query?: string;
  genres?: string[];
  yearFrom?: number;
  yearTo?: number;
  minRating?: number;
  maxRating?: number;
  languages?: string[];
  minRuntime?: number;
  maxRuntime?: number;
  sort?: 'popularity' | 'rating' | 'releaseDate' | 'oldest' | 'title' | 'runtime';
}

/* ----------------------------- administration ---------------------------- */

export interface DashboardStatistics {
  users: { total: number; enabled: number; disabled: number; joinedLast30Days: number };
  catalogue: { movies: number; genres: number; people: number; releasedLast12Months: number };
  engagement: {
    ratings: number;
    reviews: number;
    hiddenReviews: number;
    watchlistEntries: number;
    interactionsLast7Days: number;
    recommendationsLast7Days: number;
  };
  mostPopular: Array<{
    id: number;
    title: string;
    interactions: number;
    averageRating: number;
    ratingCount: number;
  }>;
  mostActive: Array<{ id: number; displayName: string; email: string; interactions: number }>;
  recommendationsByType: Record<string, number>;
  generatedAt: string;
}

export interface AdminUser {
  id: number;
  email: string;
  displayName: string;
  enabled: boolean;
  emailVerified: boolean;
  roles: string[];
  ratingCount: number;
  reviewCount: number;
  createdAt: string;
  lastLoginAt: string | null;
}

export interface SyncResult {
  provider: string;
  fetched: number;
  created: number;
  updated: number;
  skipped: number;
  warnings: string[];
  completedAt: string;
}
