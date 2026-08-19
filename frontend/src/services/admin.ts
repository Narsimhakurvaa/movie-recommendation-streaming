import { http } from '@/lib/api-client';
import type { AdminUser, DashboardStatistics, PageResponse, Review, SyncResult } from '@/types/api';

export const adminService = {
  statistics: () => http.get<DashboardStatistics>('/admin/statistics'),

  users: (search: string, enabled: boolean | undefined, page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (search) params.set('search', search);
    if (enabled !== undefined) params.set('enabled', String(enabled));
    return http.get<PageResponse<AdminUser>>(`/admin/users?${params}`);
  },

  setUserEnabled: (userId: number, enabled: boolean, reason?: string) =>
    http.patch<AdminUser>(`/admin/users/${userId}/enabled`, { enabled, reason }),

  reviews: (status: string | undefined, page = 0, size = 20) => {
    const params = new URLSearchParams({ page: String(page), size: String(size) });
    if (status) params.set('status', status);
    return http.get<PageResponse<Review>>(`/admin/reviews?${params}`);
  },

  moderateReview: (reviewId: number, status: string, moderationNote?: string) =>
    http.patch<Review>(`/admin/reviews/${reviewId}`, { status, moderationNote }),

  syncCatalogue: (pages = 1) => http.post<SyncResult>(`/admin/catalogue/sync?pages=${pages}`),

  providerStatus: () =>
    http.get<{ provider: string; available: boolean; catalogueSize: number }>('/admin/provider'),
};
