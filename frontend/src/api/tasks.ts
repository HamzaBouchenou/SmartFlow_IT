import { apiFetch } from './client';
import type { BulkActionResultResponse, BulkAssignRequest, PageResponse, RequestSummaryResponse, TaskQueueFilterParams } from './types';

// §6.6 - "File personnelle « Mes tâches » et file d'équipe", paginées et filtrées
// (TaskQueueController).

export function myTasks(filter: TaskQueueFilterParams = {}) {
  return apiFetch<PageResponse<RequestSummaryResponse>>('/tasks/mine', { searchParams: filter });
}

export function teamTasks(filter: TaskQueueFilterParams = {}) {
  return apiFetch<PageResponse<RequestSummaryResponse>>('/tasks/team', { searchParams: filter });
}

/** §6.6 - "Actions en masse limitées aux changements ne présentant pas de risque fonctionnel" : ASSIGN seule. */
export function bulkAssign(request: BulkAssignRequest) {
  return apiFetch<BulkActionResultResponse>('/tasks/bulk-assign', { method: 'POST', body: request });
}
