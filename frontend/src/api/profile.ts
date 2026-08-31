import { apiFetch } from './client';
import type { ChangePasswordRequest, UpdateProfileRequest, UserResponse } from './types';

// §6.1 - libre-service sur son propre compte : "Consultation et mise à jour des
// informations de profil autorisées" (la lecture reste GET /auth/me, api/auth.ts - cet
// écran n'a rien à ajouter à la lecture) et changement de mot de passe.

export function updateProfile(request: UpdateProfileRequest) {
  return apiFetch<UserResponse>('/profile', { method: 'PUT', body: request });
}

export function changePassword(request: ChangePasswordRequest) {
  return apiFetch<void>('/profile/password', { method: 'POST', body: request });
}
