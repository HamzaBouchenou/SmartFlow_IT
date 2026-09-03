import { apiFetch } from './client';
import type {
  ChangePasswordRequest,
  NotificationPreferenceResponse,
  NotificationType,
  UpdateNotificationPreferenceRequest,
  UpdateProfileRequest,
  UserResponse,
} from './types';

// §6.1 - libre-service sur son propre compte : "Consultation et mise à jour des
// informations de profil autorisées" (la lecture reste GET /auth/me, api/auth.ts - cet
// écran n'a rien à ajouter à la lecture) et changement de mot de passe.

export function updateProfile(request: UpdateProfileRequest) {
  return apiFetch<UserResponse>('/profile', { method: 'PUT', body: request });
}

export function changePassword(request: ChangePasswordRequest) {
  return apiFetch<void>('/profile/password', { method: 'POST', body: request });
}

/** §6.8 - préférences de notification de l'appelant lui-même (aucun :id côté serveur). */
export function listNotificationPreferences() {
  return apiFetch<NotificationPreferenceResponse[]>('/profile/notification-preferences');
}

export function updateNotificationPreference(type: NotificationType, request: UpdateNotificationPreferenceRequest) {
  return apiFetch<NotificationPreferenceResponse>(`/profile/notification-preferences/${type}`, {
    method: 'PUT',
    body: request,
  });
}
