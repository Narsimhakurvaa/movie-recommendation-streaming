import { describe, expect, it, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router-dom';
import { RequireAuth } from '../RequireAuth';
import { renderWithProviders } from '@/test/render';
import { tokenStore } from '@/lib/token-store';

function signIn(roles: string[]) {
  tokenStore.set({
    accessToken: 'access-1',
    refreshToken: 'refresh-1',
    user: {
      id: 1, email: 'a@example.com', displayName: 'A', avatarUrl: null,
      roles, emailVerified: true, onboardingCompleted: true,
    },
  });
}

function renderGuarded(requireAdmin = false) {
  return renderWithProviders(
    <Routes>
      <Route path="/login" element={<p>Login page</p>} />
      <Route path="/" element={<p>Home page</p>} />
      <Route
        path="/protected"
        element={
          <RequireAuth requireAdmin={requireAdmin}>
            <p>Secret content</p>
          </RequireAuth>
        }
      />
    </Routes>,
    { route: '/protected' },
  );
}

describe('RequireAuth', () => {
  beforeEach(() => window.localStorage.clear());

  it('redirects an anonymous visitor to the login page', () => {
    renderGuarded();

    expect(screen.getByText('Login page')).toBeInTheDocument();
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument();
  });

  it('renders the page for a signed-in user', () => {
    signIn(['ROLE_USER']);
    renderGuarded();

    expect(screen.getByText('Secret content')).toBeInTheDocument();
  });

  it('sends a non-admin away from an admin-only route', () => {
    signIn(['ROLE_USER']);
    renderGuarded(true);

    // Home, not login: they are authenticated, just not authorised.
    expect(screen.getByText('Home page')).toBeInTheDocument();
    expect(screen.queryByText('Secret content')).not.toBeInTheDocument();
  });

  it('admits an administrator to an admin-only route', () => {
    signIn(['ROLE_USER', 'ROLE_ADMIN']);
    renderGuarded(true);

    expect(screen.getByText('Secret content')).toBeInTheDocument();
  });
});
