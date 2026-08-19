import { http } from '@/lib/api-client';
import { tokenStore } from '@/lib/token-store';
import type { AuthResponse, UserProfile } from '@/types/api';

export const authService = {
  register: async (input: {
    email: string;
    password: string;
    displayName: string;
    favouriteGenreSlugs?: string[];
  }) => {
    const response = await http.post<AuthResponse>('/auth/register', input);
    tokenStore.set(response);
    return response;
  },

  login: async (input: { email: string; password: string }) => {
    const response = await http.post<AuthResponse>('/auth/login', input);
    tokenStore.set(response);
    return response;
  },

  /** Clears the session locally even if the server call fails. */
  logout: async () => {
    const refreshToken = tokenStore.getRefreshToken();
    try {
      if (refreshToken) {
        await http.post('/auth/logout', { refreshToken });
      }
    } finally {
      tokenStore.clear();
    }
  },

  requestPasswordReset: (email: string) =>
    http.post<{ message: string }>('/auth/password-reset/request', { email }),

  confirmPasswordReset: (token: string, newPassword: string) =>
    http.post<{ message: string }>('/auth/password-reset/confirm', { token, newPassword }),

  changePassword: (currentPassword: string, newPassword: string) =>
    http.post<{ message: string }>('/auth/change-password', { currentPassword, newPassword }),
};

export const profileService = {
  get: () => http.get<UserProfile>('/profile'),

  update: (input: { displayName?: string; avatarUrl?: string; biography?: string }) =>
    http.put<UserProfile>('/profile', input),

  updatePreferences: (input: Record<string, unknown>) =>
    http.put<UserProfile>('/profile/preferences', input),

  recommendationHistory: (page = 0, size = 20) =>
    http.get(`/profile/recommendation-history?page=${page}&size=${size}`),
};
