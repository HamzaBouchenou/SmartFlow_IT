import { apiFetch } from './client';
import type { FormDefinitionResponse, RequestTypeResponse, ServiceCatalogResponse } from './types';

// §6.2 - Catalogue de services. Non paginé (ServiceCatalogController) : un référentiel
// borné configuré par l'administration, pas une liste qui grandit avec l'activité.

export function searchServices(params: { q?: string; category?: string; departmentId?: number } = {}) {
  return apiFetch<ServiceCatalogResponse[]>('/services', { searchParams: params });
}

export function listRequestTypes(serviceCatalogId: number) {
  return apiFetch<RequestTypeResponse[]>(`/services/${serviceCatalogId}/request-types`);
}

export function getRequestType(requestTypeId: number) {
  return apiFetch<RequestTypeResponse>(`/request-types/${requestTypeId}`);
}

/** §6.3 - la définition de formulaire PUBLISHED active de ce type de demande. */
export function getRequestTypeForm(requestTypeId: number) {
  return apiFetch<FormDefinitionResponse>(`/request-types/${requestTypeId}/form`);
}
