import { apiFetch, ApiError } from './client';
import type { LoginRequest, LoginResponse } from './types';

// §6.1 - Authentification.

export function login(request: LoginRequest): Promise<LoginResponse> {
  return apiFetch<LoginResponse>('/auth/login', { method: 'POST', body: request });
}

/** §11.2 - seul moyen pour le SPA de savoir qui est connecté après un rechargement de
 * page (le cookie de session est HttpOnly, ADR-01). Renvoie null plutôt que de laisser
 * l'appelant traiter un 401 comme une erreur : "personne n'est connecté" est un état
 * normal au chargement de l'application, pas une panne. */
export async function me(): Promise<LoginResponse | null> {
  try {
    return await apiFetch<LoginResponse>('/auth/me');
  } catch (error) {
    if (error instanceof ApiError && error.status === 401) {
      return null;
    }
    throw error;
  }
}

export function logout(): Promise<void> {
  return apiFetch<void>('/auth/logout', { method: 'POST' });
}
