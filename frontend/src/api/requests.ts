import { apiFetch } from './client';
import type {
  CreateRequestRequest,
  ExecuteTransitionRequest,
  PageResponse,
  RequestDetailResponse,
  RequestHistoryResponse,
  RequestStatus,
  RequestSummaryResponse,
  UpdateRequestRequest,
} from './types';

// §6.4 - Gestion des demandes (brouillon, soumission, annulation) et §6.5 (transitions de
// workflow). Un seul type de réponse, RequestDetailResponse, pour toutes ces opérations :
// le back-end relit systématiquement l'état courant après chaque action
// (RequestController - "chaque méthode ne fait qu'appeler RequestService puis relire
// l'état courant via getDetail").

/** §6.9/§9.4 - "vue demandeur" (RG-06 - "un demandeur ne voit que ses dossiers"), y
 * compris ses brouillons - contrairement aux files de travail du §6.6 (api/tasks.ts). */
export function listMine(status?: RequestStatus, page = 0, size = 20) {
  return apiFetch<PageResponse<RequestSummaryResponse>>('/requests', { searchParams: { status, page, size } });
}

export function createDraft(request: CreateRequestRequest) {
  return apiFetch<RequestDetailResponse>('/requests', { method: 'POST', body: request });
}

export function getRequest(id: number) {
  return apiFetch<RequestDetailResponse>(`/requests/${id}`);
}

/** §6.4 - "Affichage d'une frise d'avancement et de l'historique complet." */
export function getHistory(id: number) {
  return apiFetch<RequestHistoryResponse[]>(`/requests/${id}/history`);
}

export function updateDraft(id: number, request: UpdateRequestRequest) {
  return apiFetch<RequestDetailResponse>(`/requests/${id}`, { method: 'PUT', body: request });
}

export function submitRequest(id: number) {
  return apiFetch<RequestDetailResponse>(`/requests/${id}/submit`, { method: 'POST' });
}

export function cancelRequest(id: number) {
  return apiFetch<RequestDetailResponse>(`/requests/${id}/cancel`, { method: 'POST' });
}

export function executeTransition(id: number, request: ExecuteTransitionRequest) {
  return apiFetch<RequestDetailResponse>(`/requests/${id}/transitions`, { method: 'POST', body: request });
}

/** RG-08/ADR-14 - jamais résolue via executeTransition : voir WorkflowAction.REOPEN's own javadoc côté back-end. */
export function reopenRequest(id: number) {
  return apiFetch<RequestDetailResponse>(`/requests/${id}/reopen`, { method: 'POST' });
}
