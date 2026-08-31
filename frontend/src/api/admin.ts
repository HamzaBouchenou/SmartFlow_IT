import { apiFetch } from './client';
import type {
  AuditLogFilterParams,
  AuditLogResponse,
  CreateDepartmentRequest,
  CreateRoleAssignmentRequest,
  CreateTeamRequest,
  CreateUserRequest,
  DepartmentResponse,
  DiagnosticsResponse,
  EmailTemplateResponse,
  FormDefinitionAdminResponse,
  FormFieldAdminResponse,
  FieldOptionAdminResponse,
  PageResponse,
  RequestTypeAdminResponse,
  ResetPasswordRequest,
  ServiceCatalogAdminResponse,
  SlaResponse,
  StepAdminResponse,
  SystemParameterResponse,
  TeamResponse,
  TransitionAdminResponse,
  UpdateDepartmentRequest,
  UpdateEmailTemplateRequest,
  UpdateSystemParameterRequest,
  UpdateTeamRequest,
  UpdateUserRequest,
  UpsertFieldOptionRequest,
  UpsertFormFieldRequest,
  UpsertRequestTypeRequest,
  UpsertServiceCatalogRequest,
  UpsertSlaRequest,
  UpsertStepRequest,
  UpsertTransitionRequest,
  UserResponse,
  UserRoleAssignmentResponse,
  WorkflowDefinitionAdminResponse,
} from './types';

// §6.10 - Administration et audit. Accès gouverné côté serveur
// (AuthorizationService.canViewAuditLog/isFunctionalAdmin) - un rôle non habilité reçoit un
// 404, affiché comme n'importe quelle autre ErrorBanner (même raisonnement que DashboardPage).

/** §13.1 - "Journal d'audit consultable avec filtres par utilisateur, action, objet et période." */
export function searchAuditLog(filter: AuditLogFilterParams = {}) {
  return apiFetch<PageResponse<AuditLogResponse>>('/admin/audit-log', { searchParams: filter });
}

/** §6.10 - "Paramètres généraux", catalogue fermé (SystemParameterAdminService). */
export function listSystemParameters() {
  return apiFetch<SystemParameterResponse[]>('/admin/system-parameters');
}

export function updateSystemParameter(key: string, request: UpdateSystemParameterRequest) {
  return apiFetch<SystemParameterResponse>(`/admin/system-parameters/${encodeURIComponent(key)}`, {
    method: 'PUT',
    body: request,
  });
}

// --- §4.1 - directions et services (Department) ---------------------------------------
// RG-02/RG-12 - jamais de suppression physique : activate/deactivate plutôt qu'un DELETE.

export function listDepartments() {
  return apiFetch<DepartmentResponse[]>('/admin/departments');
}

export function createDepartment(request: CreateDepartmentRequest) {
  return apiFetch<DepartmentResponse>('/admin/departments', { method: 'POST', body: request });
}

export function updateDepartment(id: number, request: UpdateDepartmentRequest) {
  return apiFetch<DepartmentResponse>(`/admin/departments/${id}`, { method: 'PUT', body: request });
}

export function activateDepartment(id: number) {
  return apiFetch<DepartmentResponse>(`/admin/departments/${id}/activate`, { method: 'POST' });
}

export function deactivateDepartment(id: number) {
  return apiFetch<DepartmentResponse>(`/admin/departments/${id}/deactivate`, { method: 'POST' });
}

// --- §4.1/§6.6 - équipes (Team) --------------------------------------------------------

export function listTeams(departmentId?: number) {
  return apiFetch<TeamResponse[]>('/admin/teams', { searchParams: { departmentId } });
}

export function createTeam(request: CreateTeamRequest) {
  return apiFetch<TeamResponse>('/admin/teams', { method: 'POST', body: request });
}

export function updateTeam(id: number, request: UpdateTeamRequest) {
  return apiFetch<TeamResponse>(`/admin/teams/${id}`, { method: 'PUT', body: request });
}

export function activateTeam(id: number) {
  return apiFetch<TeamResponse>(`/admin/teams/${id}/activate`, { method: 'POST' });
}

export function deactivateTeam(id: number) {
  return apiFetch<TeamResponse>(`/admin/teams/${id}/deactivate`, { method: 'POST' });
}

// --- §6.8/§6.10 - modèles d'e-mail, un par NotificationType (catalogue fermé) ----------

export function listEmailTemplates() {
  return apiFetch<EmailTemplateResponse[]>('/admin/email-templates');
}

export function updateEmailTemplate(code: string, request: UpdateEmailTemplateRequest) {
  return apiFetch<EmailTemplateResponse>(`/admin/email-templates/${encodeURIComponent(code)}`, {
    method: 'PUT',
    body: request,
  });
}

// --- §6.7/§6.10 - cibles SLA d'un type de demande, une par Priority --------------------

export function listSla(requestTypeId: number) {
  return apiFetch<SlaResponse[]>(`/admin/request-types/${requestTypeId}/sla`);
}

export function upsertSla(requestTypeId: number, priority: string, request: UpsertSlaRequest) {
  return apiFetch<SlaResponse>(`/admin/request-types/${requestTypeId}/sla/${priority}`, {
    method: 'PUT',
    body: request,
  });
}

// --- §6.10/§15.3 - page de diagnostic (état des services techniques, sans secrets) -----

export function checkDiagnostics() {
  return apiFetch<DiagnosticsResponse>('/admin/diagnostics');
}

// --- §6.1/§6.10 - utilisateurs -----------------------------------------------------------
// RG-02 - jamais de suppression physique : activate/deactivate plutôt qu'un DELETE.

export function listUsers() {
  return apiFetch<UserResponse[]>('/admin/users');
}

export function createUser(request: CreateUserRequest) {
  return apiFetch<UserResponse>('/admin/users', { method: 'POST', body: request });
}

export function updateUser(id: number, request: UpdateUserRequest) {
  return apiFetch<UserResponse>(`/admin/users/${id}`, { method: 'PUT', body: request });
}

export function activateUser(id: number) {
  return apiFetch<UserResponse>(`/admin/users/${id}/activate`, { method: 'POST' });
}

export function deactivateUser(id: number) {
  return apiFetch<UserResponse>(`/admin/users/${id}/deactivate`, { method: 'POST' });
}

export function resetPassword(id: number, request: ResetPasswordRequest) {
  return apiFetch<UserResponse>(`/admin/users/${id}/reset-password`, { method: 'POST', body: request });
}

/** ADR-13 - débloque un compte verrouillé avant l'expiration naturelle du verrou. */
export function unlockUser(id: number) {
  return apiFetch<UserResponse>(`/admin/users/${id}/unlock`, { method: 'POST' });
}

// --- §5.1/§6.10 - affectations de rôle d'un utilisateur ---------------------------------
// Révocation réelle (pas de drapeau logique sur UserRoleAssignment).

export function listRoleAssignments(userId: number) {
  return apiFetch<UserRoleAssignmentResponse[]>(`/admin/users/${userId}/role-assignments`);
}

export function grantRole(userId: number, request: CreateRoleAssignmentRequest) {
  return apiFetch<UserRoleAssignmentResponse>(`/admin/users/${userId}/role-assignments`, { method: 'POST', body: request });
}

export function revokeRole(userId: number, assignmentId: number) {
  return apiFetch<void>(`/admin/users/${userId}/role-assignments/${assignmentId}`, { method: 'DELETE' });
}

// --- §6.2/§6.10 - catalogue (ServiceCatalog/RequestType), non versionné (ADR-17) -------
// RG-02/RG-12 - jamais de suppression physique : activate/deactivate plutôt qu'un DELETE.

export function listServicesAdmin() {
  return apiFetch<ServiceCatalogAdminResponse[]>('/admin/services');
}

export function createServiceAdmin(request: UpsertServiceCatalogRequest) {
  return apiFetch<ServiceCatalogAdminResponse>('/admin/services', { method: 'POST', body: request });
}

export function updateServiceAdmin(id: number, request: UpsertServiceCatalogRequest) {
  return apiFetch<ServiceCatalogAdminResponse>(`/admin/services/${id}`, { method: 'PUT', body: request });
}

export function activateServiceAdmin(id: number) {
  return apiFetch<ServiceCatalogAdminResponse>(`/admin/services/${id}/activate`, { method: 'POST' });
}

export function deactivateServiceAdmin(id: number) {
  return apiFetch<ServiceCatalogAdminResponse>(`/admin/services/${id}/deactivate`, { method: 'POST' });
}

export function listRequestTypesAdmin(serviceCatalogId: number) {
  return apiFetch<RequestTypeAdminResponse[]>('/admin/request-types', { searchParams: { serviceCatalogId } });
}

export function createRequestTypeAdmin(request: UpsertRequestTypeRequest) {
  return apiFetch<RequestTypeAdminResponse>('/admin/request-types', { method: 'POST', body: request });
}

export function updateRequestTypeAdmin(id: number, request: UpsertRequestTypeRequest) {
  return apiFetch<RequestTypeAdminResponse>(`/admin/request-types/${id}`, { method: 'PUT', body: request });
}

export function activateRequestTypeAdmin(id: number) {
  return apiFetch<RequestTypeAdminResponse>(`/admin/request-types/${id}/activate`, { method: 'POST' });
}

export function deactivateRequestTypeAdmin(id: number) {
  return apiFetch<RequestTypeAdminResponse>(`/admin/request-types/${id}/deactivate`, { method: 'POST' });
}

// --- §6.3/§10.1/§6.10 - formulaires versionnés (ADR-17, docs/DECISIONS.md) -------------
// Un DRAFT à la fois par type de demande ; publier archive l'ancien PUBLISHED (RG-12).

export function listFormVersions(requestTypeId: number) {
  return apiFetch<FormDefinitionAdminResponse[]>(`/admin/request-types/${requestTypeId}/form-definitions`);
}

export function createFormDraft(requestTypeId: number) {
  return apiFetch<FormDefinitionAdminResponse>(`/admin/request-types/${requestTypeId}/form-definitions`, { method: 'POST' });
}

export function getFormVersion(formDefinitionId: number) {
  return apiFetch<FormDefinitionAdminResponse>(`/admin/form-definitions/${formDefinitionId}`);
}

export function deleteFormDraft(formDefinitionId: number) {
  return apiFetch<void>(`/admin/form-definitions/${formDefinitionId}`, { method: 'DELETE' });
}

export function publishFormVersion(formDefinitionId: number) {
  return apiFetch<FormDefinitionAdminResponse>(`/admin/form-definitions/${formDefinitionId}/publish`, { method: 'POST' });
}

export function addFormField(formDefinitionId: number, request: UpsertFormFieldRequest) {
  return apiFetch<FormFieldAdminResponse>(`/admin/form-definitions/${formDefinitionId}/fields`, { method: 'POST', body: request });
}

export function updateFormField(fieldId: number, request: UpsertFormFieldRequest) {
  return apiFetch<FormFieldAdminResponse>(`/admin/form-fields/${fieldId}`, { method: 'PUT', body: request });
}

export function deleteFormField(fieldId: number) {
  return apiFetch<void>(`/admin/form-fields/${fieldId}`, { method: 'DELETE' });
}

export function addFieldOption(fieldId: number, request: UpsertFieldOptionRequest) {
  return apiFetch<FieldOptionAdminResponse>(`/admin/form-fields/${fieldId}/options`, { method: 'POST', body: request });
}

export function updateFieldOption(optionId: number, request: UpsertFieldOptionRequest) {
  return apiFetch<FieldOptionAdminResponse>(`/admin/field-options/${optionId}`, { method: 'PUT', body: request });
}

export function deleteFieldOption(optionId: number) {
  return apiFetch<void>(`/admin/field-options/${optionId}`, { method: 'DELETE' });
}

// --- §6.5/§10.1/§6.10 - workflows versionnés (ADR-17, docs/DECISIONS.md ; RG-03) -------
// Un DRAFT à la fois par type de demande ; publier archive l'ancien PUBLISHED (RG-12) sans
// jamais toucher une demande déjà en cours sur lui (RG-03).

export function listWorkflowVersions(requestTypeId: number) {
  return apiFetch<WorkflowDefinitionAdminResponse[]>(`/admin/request-types/${requestTypeId}/workflow-definitions`);
}

export function createWorkflowDraft(requestTypeId: number) {
  return apiFetch<WorkflowDefinitionAdminResponse>(`/admin/request-types/${requestTypeId}/workflow-definitions`, {
    method: 'POST',
  });
}

export function getWorkflowVersion(workflowDefinitionId: number) {
  return apiFetch<WorkflowDefinitionAdminResponse>(`/admin/workflow-definitions/${workflowDefinitionId}`);
}

export function deleteWorkflowDraft(workflowDefinitionId: number) {
  return apiFetch<void>(`/admin/workflow-definitions/${workflowDefinitionId}`, { method: 'DELETE' });
}

export function publishWorkflowVersion(workflowDefinitionId: number) {
  return apiFetch<WorkflowDefinitionAdminResponse>(`/admin/workflow-definitions/${workflowDefinitionId}/publish`, {
    method: 'POST',
  });
}

export function addStepAdmin(workflowDefinitionId: number, request: UpsertStepRequest) {
  return apiFetch<StepAdminResponse>(`/admin/workflow-definitions/${workflowDefinitionId}/steps`, {
    method: 'POST',
    body: request,
  });
}

export function updateStepAdmin(stepId: number, request: UpsertStepRequest) {
  return apiFetch<StepAdminResponse>(`/admin/steps/${stepId}`, { method: 'PUT', body: request });
}

export function deleteStepAdmin(stepId: number) {
  return apiFetch<void>(`/admin/steps/${stepId}`, { method: 'DELETE' });
}

export function addTransitionAdmin(fromStepId: number, request: UpsertTransitionRequest) {
  return apiFetch<TransitionAdminResponse>(`/admin/steps/${fromStepId}/transitions`, { method: 'POST', body: request });
}

export function updateTransitionAdmin(transitionId: number, request: UpsertTransitionRequest) {
  return apiFetch<TransitionAdminResponse>(`/admin/transitions/${transitionId}`, { method: 'PUT', body: request });
}

export function deleteTransitionAdmin(transitionId: number) {
  return apiFetch<void>(`/admin/transitions/${transitionId}`, { method: 'DELETE' });
}
