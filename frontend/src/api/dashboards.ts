import { apiFetch } from './client';
import type { DashboardResponse, HomeDashboardResponse } from './types';

// §6.9 - "Vue responsable" et export CSV, gouvernés par canViewDashboard côté serveur.

export interface DashboardFilterParams {
  serviceId: number;
  from?: string;
  to?: string;
}

export function getServiceDashboard(filter: DashboardFilterParams) {
  return apiFetch<DashboardResponse>('/dashboards/service', { searchParams: filter });
}

/** §9.4 (écran Accueil) / §6.9 ("vue demandeur", "vue agent") - résumé borné à l'appelant. */
export function getHome() {
  return apiFetch<HomeDashboardResponse>('/dashboards/home');
}

/** Chemin relatif à l'API - à consommer via un lien `<a>` (GET, export CSV), jamais fetch/XHR. */
export function exportPath(filter: DashboardFilterParams): string {
  const params = new URLSearchParams();
  params.set('serviceId', String(filter.serviceId));
  if (filter.from) params.set('from', filter.from);
  if (filter.to) params.set('to', filter.to);
  return `/api/v1/dashboards/service/export?${params.toString()}`;
}
