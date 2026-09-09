import { apiFetch } from './client';
import type { NotificationResponse, PageResponse } from './types';

// §6.8 - "Centre de notifications... avec statut lu/non lu".

/** §6.8 - `unread` porte l'onglet "Non lues" côté serveur : filtrer une page déjà paginée
 * côté écran donnerait un compteur faux dès la deuxième page. */
export function listNotifications(page = 0, size = 20, unread = false) {
  return apiFetch<PageResponse<NotificationResponse>>('/notifications', {
    searchParams: { page, size, unread },
  });
}

/** ADR-22 - `background` marque l'interrogation périodique du badge (NotificationBell) :
 * elle lit un compteur, elle ne doit pas faire office de preuve d'activité de
 * l'utilisateur, sinon aucune session n'expirerait plus jamais tant qu'un onglet reste
 * ouvert. Un appel déclenché par l'utilisateur (ouverture de l'écran) le laisse à `false`. */
export function unreadCount(background = false) {
  return apiFetch<{ count: number }>('/notifications/unread-count', { background });
}

export function markRead(id: number) {
  return apiFetch<void>(`/notifications/${id}/read`, { method: 'POST' });
}

/** §6.8 - "tout marquer comme lu" : une écriture serveur, jamais une boucle d'appels côté
 * écran qui ne toucherait que la page affichée. */
export function markAllRead() {
  return apiFetch<{ marked: number }>('/notifications/read-all', { method: 'POST' });
}
