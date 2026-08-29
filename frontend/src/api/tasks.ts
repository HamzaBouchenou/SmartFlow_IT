import { apiFetch } from './client';
import type { PageResponse, RequestSummaryResponse, TaskQueueFilterParams } from './types';

// §6.6 - "File personnelle « Mes tâches » et file d'équipe", paginées et filtrées
// (TaskQueueController).

export function myTasks(filter: TaskQueueFilterParams = {}) {
  return apiFetch<PageResponse<RequestSummaryResponse>>('/tasks/mine', { searchParams: filter });
}

export function teamTasks(filter: TaskQueueFilterParams = {}) {
  return apiFetch<PageResponse<RequestSummaryResponse>>('/tasks/team', { searchParams: filter });
}
