import { apiFetch } from './client';
import type {
  ChangePasswordRequest,
  MyProfileResponse,
  NotificationPreferenceResponse,
  NotificationType,
  UpdateNotificationPreferenceRequest,
  UpdateProfileRequest,
  UserResponse,
} from './types';

// §6.1 - libre-service sur son propre compte : "Consultation et mise à jour des
// informations de profil autorisées" et changement de mot de passe.
//
// Deux lectures qui ne servent pas le même besoin : GET /auth/me (api/auth.ts) dit "qui est
// connecté" à chaque chargement du SPA (ADR-01), GET /profile porte le rattachement, les
// habilitations et l'expiration de session - appelée par le seul écran de profil.

/** §6.1 - "Consultation ... des informations de profil autorisées", sur soi-même.
 * `background` (ADR-22) pour la relecture périodique qui tient le compte à rebours
 * d'expiration à jour : cette lecture-là ne doit surtout pas repousser l'échéance qu'elle
 * affiche. */
export function getMyProfile(background = false) {
  return apiFetch<MyProfileResponse>('/profile', { background });
}

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
