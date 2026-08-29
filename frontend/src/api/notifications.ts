import { apiFetch } from './client';
import type { NotificationResponse, PageResponse } from './types';

// §6.8 - "Centre de notifications... avec statut lu/non lu".

export function listNotifications(page = 0, size = 20) {
  return apiFetch<PageResponse<NotificationResponse>>('/notifications', { searchParams: { page, size } });
}

export function unreadCount() {
  return apiFetch<{ count: number }>('/notifications/unread-count');
}

export function markRead(id: number) {
  return apiFetch<void>(`/notifications/${id}/read`, { method: 'POST' });
}
