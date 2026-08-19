import type { ReactNode } from 'react';
import { Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/hooks/use-auth';

/**
 * Route guard.
 *
 * A navigation convenience only. Authorisation is enforced by the API on every
 * request, so removing this component would change what the UI shows, not what
 * a user can actually access.
 */
export function RequireAuth({
  children,
  requireAdmin = false,
}: {
  children: ReactNode;
  requireAdmin?: boolean;
}) {
  const { isAuthenticated, isAdmin } = useAuth();
  const location = useLocation();

  if (!isAuthenticated) {
    // Remember the destination so login can return the user to it.
    return <Navigate to="/login" state={{ from: location.pathname }} replace />;
  }
  if (requireAdmin && !isAdmin) {
    return <Navigate to="/" replace />;
  }
  return <>{children}</>;
}
