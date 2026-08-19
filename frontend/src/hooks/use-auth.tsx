import { createContext, useCallback, useContext, useEffect, useMemo, useState } from 'react';
import type { ReactNode } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import { tokenStore } from '@/lib/token-store';
import { authService } from '@/services/auth';
import type { AuthenticatedUser } from '@/types/api';

interface AuthContextValue {
  user: AuthenticatedUser | null;
  isAuthenticated: boolean;
  isAdmin: boolean;
  login: (email: string, password: string) => Promise<void>;
  register: (input: {
    email: string;
    password: string;
    displayName: string;
    favouriteGenreSlugs?: string[];
  }) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: (user: AuthenticatedUser) => void;
}

const AuthContext = createContext<AuthContextValue | null>(null);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthenticatedUser | null>(() => tokenStore.getUser());
  const queryClient = useQueryClient();

  /*
   * The API client dispatches this when a refresh attempt fails, which is the
   * only place that knows the session is truly over. Listening for it keeps
   * that knowledge out of every component.
   */
  useEffect(() => {
    const onSessionExpired = () => {
      setUser(null);
      queryClient.clear();
    };
    window.addEventListener('cinevault:session-expired', onSessionExpired);
    return () => window.removeEventListener('cinevault:session-expired', onSessionExpired);
  }, [queryClient]);

  const login = useCallback(
    async (email: string, password: string) => {
      const response = await authService.login({ email, password });
      setUser(response.user);
      // Cached data belongs to the previous visitor; drop it.
      queryClient.clear();
    },
    [queryClient],
  );

  const register = useCallback(
    async (input: {
      email: string;
      password: string;
      displayName: string;
      favouriteGenreSlugs?: string[];
    }) => {
      const response = await authService.register(input);
      setUser(response.user);
      queryClient.clear();
    },
    [queryClient],
  );

  const logout = useCallback(async () => {
    await authService.logout();
    setUser(null);
    queryClient.clear();
  }, [queryClient]);

  const refreshUser = useCallback((next: AuthenticatedUser) => setUser(next), []);

  const value = useMemo<AuthContextValue>(
    () => ({
      user,
      isAuthenticated: user !== null,
      isAdmin: user?.roles.includes('ROLE_ADMIN') ?? false,
      login,
      register,
      logout,
      refreshUser,
    }),
    [user, login, register, logout, refreshUser],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used within an AuthProvider');
  }
  return context;
}
