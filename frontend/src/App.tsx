import { lazy } from 'react';
import { Route, Routes } from 'react-router-dom';
import { Layout } from '@/components/layout/Layout';
import { RequireAuth } from '@/components/RequireAuth';
import { HomePage } from '@/pages/HomePage';

/*
 * Route-level code splitting.
 *
 * The home page is bundled eagerly because it is the common entry point;
 * everything else loads on demand, so a first-time visitor does not download
 * the admin dashboard or the settings forms to look at a movie.
 */
const MoviesPage = lazy(() => import('@/pages/MoviesPage').then((m) => ({ default: m.MoviesPage })));
const MovieDetailPage = lazy(() =>
  import('@/pages/MovieDetailPage').then((m) => ({ default: m.MovieDetailPage })),
);
const SearchPage = lazy(() => import('@/pages/SearchPage').then((m) => ({ default: m.SearchPage })));
const GenrePage = lazy(() => import('@/pages/GenrePage').then((m) => ({ default: m.GenrePage })));
const LoginPage = lazy(() => import('@/pages/LoginPage').then((m) => ({ default: m.LoginPage })));
const RegisterPage = lazy(() =>
  import('@/pages/RegisterPage').then((m) => ({ default: m.RegisterPage })),
);
const RecommendationsPage = lazy(() =>
  import('@/pages/RecommendationsPage').then((m) => ({ default: m.RecommendationsPage })),
);
const WatchlistPage = lazy(() =>
  import('@/pages/WatchlistPage').then((m) => ({ default: m.WatchlistPage })),
);
const HistoryPage = lazy(() =>
  import('@/pages/HistoryPage').then((m) => ({ default: m.HistoryPage })),
);
const ProfilePage = lazy(() =>
  import('@/pages/ProfilePage').then((m) => ({ default: m.ProfilePage })),
);
const SettingsPage = lazy(() =>
  import('@/pages/SettingsPage').then((m) => ({ default: m.SettingsPage })),
);
const AdminPage = lazy(() => import('@/pages/AdminPage').then((m) => ({ default: m.AdminPage })));
const NotFoundPage = lazy(() =>
  import('@/pages/NotFoundPage').then((m) => ({ default: m.NotFoundPage })),
);

export function App() {
  return (
    <Routes>
      <Route element={<Layout />}>
        {/* Public */}
        <Route index element={<HomePage />} />
        <Route path="movies" element={<MoviesPage />} />
        <Route path="movies/:movieId" element={<MovieDetailPage />} />
        <Route path="search" element={<SearchPage />} />
        <Route path="genres" element={<GenrePage />} />
        <Route path="genres/:slug" element={<GenrePage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />

        {/* Requires a session */}
        <Route
          path="recommendations"
          element={
            <RequireAuth>
              <RecommendationsPage />
            </RequireAuth>
          }
        />
        <Route
          path="watchlist"
          element={
            <RequireAuth>
              <WatchlistPage />
            </RequireAuth>
          }
        />
        <Route
          path="history"
          element={
            <RequireAuth>
              <HistoryPage />
            </RequireAuth>
          }
        />
        <Route
          path="profile"
          element={
            <RequireAuth>
              <ProfilePage />
            </RequireAuth>
          }
        />
        <Route
          path="settings"
          element={
            <RequireAuth>
              <SettingsPage />
            </RequireAuth>
          }
        />

        {/* Administrators only (also enforced server-side) */}
        <Route
          path="admin"
          element={
            <RequireAuth requireAdmin>
              <AdminPage />
            </RequireAuth>
          }
        />

        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}
