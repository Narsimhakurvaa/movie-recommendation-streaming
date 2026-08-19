import { Suspense, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import { Clapperboard } from 'lucide-react';
import { Header } from './Header';
import { Toaster } from '@/components/ui/Toaster';
import { ErrorBoundary } from '@/components/ui/ErrorBoundary';
import { MovieGridSkeleton } from '@/components/ui/Skeleton';

/**
 * Application shell.
 *
 * Provides the skip link, header, routed content and footer, plus the
 * suspense boundary that covers lazily-loaded routes.
 */
export function Layout() {
  const location = useLocation();

  /*
   * Restore scroll position on navigation. A client-side router does not do
   * this natively, so without it every new page opens part-way down.
   */
  useEffect(() => {
    window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
  }, [location.pathname]);

  return (
    <div className="flex min-h-screen flex-col">
      {/* First tab stop, so keyboard users can bypass the navigation. */}
      <a href="#main-content" className="skip-link">
        Skip to main content
      </a>

      <Header />

      <main id="main-content" className="flex-1" tabIndex={-1}>
        <ErrorBoundary>
          <Suspense
            fallback={
              <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">
                <MovieGridSkeleton />
              </div>
            }
          >
            <Outlet />
          </Suspense>
        </ErrorBoundary>
      </main>

      <footer className="border-t border-[var(--border-subtle)] bg-[var(--surface-sunken)]">
        <div className="mx-auto max-w-7xl px-4 py-8 sm:px-6 lg:px-8">
          <div className="flex flex-col gap-4 sm:flex-row sm:items-center sm:justify-between">
            <div className="flex items-center gap-2">
              <Clapperboard className="h-5 w-5 text-[var(--accent)]" aria-hidden="true" />
              <span className="font-[family-name:var(--font-display)] font-bold">CineVault</span>
            </div>
            <p className="max-w-xl text-xs leading-relaxed text-[var(--text-muted)]">
              A movie discovery and recommendation platform. CineVault indexes metadata and links
              to officially published trailers; it does not host or stream any film.
            </p>
          </div>
        </div>
      </footer>

      <Toaster />
    </div>
  );
}
