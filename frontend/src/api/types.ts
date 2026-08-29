// Formes de réponse/requête de l'API back-end, recopiées champ à champ depuis
// backend/src/main/java/com/smartflow/backend/api/dto/**. Ce fichier ne doit jamais
// diverger silencieusement du DTO Java qu'il représente : toute évolution d'un DTO côté
// back-end doit être reportée ici dans le même changement.

// --- domain/enums (§10) -----------------------------------------------------------

export type FieldType =
  | 'TEXT'
  | 'NUMBER'
  | 'DATE'
  | 'LIST'
  | 'CHECKBOX'
  | 'USER'
  | 'DEPARTMENT'
  | 'FILE';

// REOPEN (RG-08/ADR-14) is never offered through availableActions[] the same way as the
// other five: it is never resolved from a Step Transition, so it never appears together
// with them (a request only ever has REOPEN available while CLOSED, when none of the
// others can be). ActionBar never receives it - see ReopenButton.
export type WorkflowAction = 'VALIDATE' | 'REJECT' | 'RETURN' | 'ASSIGN' | 'REQUEST_INFO' | 'CLOSE' | 'REOPEN';

export type RequestStatus = 'DRAFT' | 'SUBMITTED' | 'CLOSED' | 'CANCELLED' | 'ARCHIVED';

// Corrigé pour correspondre à domain/enums/Priority.java (LOW/MEDIUM/HIGH/CRITICAL) : ce
// fichier portait par erreur NORMAL/URGENT, deux valeurs qui n'existent pas côté back-end.
export type Priority = 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';

export type NotificationType =
  | 'SUBMISSION'
  | 'ASSIGNMENT'
  | 'INFO_REQUESTED'
  | 'DECISION'
  | 'SLA_WARNING'
  | 'SLA_BREACH'
  | 'CLOSURE';

// §12.1 - seules CLASSIFICATION et SUMMARY sont branchées (P1) ; DOCUMENT_SEARCH et
// REPLY_SUGGESTION restent dans l'énumération back-end (P2/Option, ADR-16) mais aucune
// route ne les accepte encore - AiAssistPanel n'en propose donc pas le déclenchement.
export type AiAnalysisType = 'CLASSIFICATION' | 'SUMMARY' | 'DOCUMENT_SEARCH' | 'REPLY_SUGGESTION';

// --- crosscutting/error/ErrorResponse -----------------------------------------------

export interface FieldErrorDetail {
  field: string;
  message: string;
}

export interface ErrorResponse {
  code: string;
  message: string;
  traceId: string;
  fieldErrors: FieldErrorDetail[];
}

// --- api/dto/response/LoginResponse (AuthController) --------------------------------

export interface LoginResponse {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  roles: string[];
}

// --- api/dto/response/ServiceCatalogResponse (§6.2) ----------------------------------

export interface ServiceCatalogResponse {
  id: number;
  name: string;
  description: string;
  category: string;
  departmentId: number;
  displayOrder: number;
}

// --- api/dto/response/RequestTypeResponse (§6.2) -------------------------------------

export interface RequestTypeResponse {
  id: number;
  serviceCatalogId: number;
  name: string;
  description: string;
  targetDelayDescription: string;
  requiredDocuments: string;
  contactInfo: string;
  displayOrder: number;
}

// --- api/dto/response/FieldOptionResponse / FormFieldResponse / FormDefinitionResponse (§6.3) --

export interface FieldOptionResponse {
  value: string;
  label: string;
  displayOrder: number;
}

export interface FormFieldResponse {
  id: number;
  code: string;
  label: string;
  fieldType: FieldType;
  required: boolean;
  displayOrder: number;
  helpText: string | null;
  visibleWhenFieldCode: string | null;
  visibleWhenValue: string | null;
  options: FieldOptionResponse[];
}

export interface FormDefinitionResponse {
  id: number;
  requestTypeId: number;
  version: number;
  fields: FormFieldResponse[];
}

// --- api/dto/response/RequestDetailResponse (§6.4) -----------------------------------
// availableActions est la seule source légitime pour décider quels boutons afficher
// (CLAUDE.md - "Rendre un bouton d'action depuis le rôle côté React plutôt que depuis
// availableActions[]" est explicitement listé comme ce qu'il ne faut jamais faire).
// §6.7 - slaStatus/slaDueAtFirstResponse/slaDueAtResolution sont le modèle SLA
// matérialisé (RG-07), jamais recalculés côté client. reopenDeadline (RG-08/ADR-14) n'a
// de sens que pour une demande CLOSED dont le type autorise la réouverture.

export interface RequestDetailResponse {
  id: number;
  reference: string;
  requestTypeId: number;
  status: RequestStatus;
  priority: Priority | null;
  title: string;
  description: string | null;
  currentStepId: number | null;
  submittedAt: string | null;
  fieldValues: Record<string, string>;
  availableActions: WorkflowAction[];
  assignedUserId: number | null;
  assignedUserName: string | null;
  assignedTeamId: number | null;
  assignedTeamName: string | null;
  slaStatus: string | null;
  slaDueAtFirstResponse: string | null;
  slaDueAtResolution: string | null;
  reopenDeadline: string | null;
}

// --- api/dto/response/RequestHistoryResponse (§6.4 - frise d'avancement) -------------

export interface RequestHistoryResponse {
  id: number;
  action: string;
  fromStepName: string | null;
  toStepName: string | null;
  actorId: number;
  actorName: string;
  comment: string | null;
  occurredAt: string;
}

// --- api/dto/response/RequestSummaryResponse (§6.6) ----------------------------------

export interface RequestSummaryResponse {
  id: number;
  reference: string;
  title: string;
  status: RequestStatus;
  priority: Priority | null;
  category: string | null;
  requesterName: string;
  currentStepName: string | null;
  submittedAt: string | null;
  slaStatus: string | null;
}

// --- api/dto/response/PageResponse<T> (§11.1) ----------------------------------------

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// --- api/dto/request/* ---------------------------------------------------------------

export interface CreateRequestRequest {
  requestTypeId: number;
  title: string;
  description?: string | null;
  fieldValues?: Record<string, string>;
}

export interface UpdateRequestRequest {
  title: string;
  description?: string | null;
  fieldValues?: Record<string, string>;
}

export interface ExecuteTransitionRequest {
  action: WorkflowAction;
  comment?: string | null;
  closureReason?: string | null;
  closureSolution?: string | null;
  assignedUserId?: number | null;
  assignedTeamId?: number | null;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface TaskQueueFilterParams {
  status?: RequestStatus;
  priority?: Priority;
  requesterId?: number;
  category?: string;
  overdue?: boolean;
  submittedFrom?: string;
  submittedTo?: string;
  page?: number;
  size?: number;
}

// --- api/dto/response/CommentResponse / dto/request/CreateCommentRequest (§6.4) -------

export interface CommentResponse {
  id: number;
  authorId: number;
  authorName: string;
  body: string;
  createdAt: string;
}

export interface CreateCommentRequest {
  body: string;
}

// --- api/dto/response/AttachmentResponse (§6.4, RG-09) ---------------------------------
// Ne porte jamais d'URL de fichier devinable (RG-09/§13) : le téléchargement passe
// toujours par GET /requests/{id}/attachments/{attachmentId}, jamais un champ ici.

export interface AttachmentResponse {
  id: number;
  originalFilename: string;
  contentType: string;
  sizeBytes: number;
  uploadedById: number;
  uploadedByName: string;
  uploadedAt: string;
}

// --- api/dto/response/NotificationResponse (§6.8) --------------------------------------

export interface NotificationResponse {
  id: number;
  type: NotificationType;
  requestId: number | null;
  requestReference: string | null;
  title: string;
  body: string | null;
  readAt: string | null;
  createdAt: string;
}

// --- api/dto/response/DashboardResponse (§6.9) ------------------------------------------
// reopenRatePercent reste toujours 0.0 tant que RG-08 n'a pas de statistique dédiée (voir
// DashboardResponse.java) ; average*Minutes/slaComplianceRatePercent peuvent être `null`
// (aucune donnée sur la période) - jamais lus comme un 0 caché.

export interface DashboardResponse {
  serviceId: number;
  serviceName: string;
  from: string | null;
  to: string | null;
  volumesByStatus: Record<string, number>;
  volumesByCategory: Record<string, number>;
  volumesByAgent: Record<string, number>;
  averageFirstResponseMinutes: number | null;
  averageResolutionMinutes: number | null;
  slaComplianceRatePercent: number | null;
  reopenRatePercent: number | null;
}

// --- api/dto/response/AiAnalysisResponse / dto/request/AnalyzeRequest / ValidateAiAnalysisRequest (§12, RG-10) --

export interface AiAnalysisResponse {
  id: number;
  requestId: number;
  analysisType: AiAnalysisType;
  rawResult: string | null;
  confidenceScore: number | null;
  suggestedValue: string | null;
  acceptedValue: string | null;
  validatedById: number | null;
  validatedByName: string | null;
  validatedAt: string | null;
  createdAt: string;
}

export interface AnalyzeRequest {
  analysisType: AiAnalysisType;
}

export interface ValidateAiAnalysisRequest {
  acceptedValue: string;
}

// --- api/dto/response/AuditLogResponse (§6.10/§13.1 - journal d'audit) -----------------
// actorId/actorName sont `null` pour une action système (le balayage d'archivage RG-12,
// par exemple), jamais pour masquer un acteur réel.

export interface AuditLogResponse {
  id: number;
  actorId: number | null;
  actorName: string | null;
  action: string;
  objectType: string;
  objectId: string | null;
  result: string;
  summary: string | null;
  traceId: string | null;
  occurredAt: string;
}

export interface AuditLogFilterParams {
  actorId?: number;
  action?: string;
  objectType?: string;
  occurredFrom?: string;
  occurredTo?: string;
  page?: number;
  size?: number;
}

// --- api/dto/response/SystemParameterResponse / dto/request/UpdateSystemParameterRequest
// (§6.10 - "Paramètres généraux") ------------------------------------------------------

export type SystemParameterType = 'INTEGER' | 'LONG' | 'BOOLEAN' | 'TEXT';

export interface SystemParameterResponse {
  key: string;
  label: string;
  description: string;
  type: SystemParameterType;
  value: string;
  overridden: boolean;
}

export interface UpdateSystemParameterRequest {
  value: string;
}

// --- api/dto/response/DepartmentResponse (§4.1/§6.10) ----------------------------------

export interface DepartmentResponse {
  id: number;
  name: string;
  parentId: number | null;
  parentName: string | null;
  leadId: number | null;
  leadName: string | null;
  active: boolean;
}

export interface CreateDepartmentRequest {
  name: string;
  parentId?: number | null;
  leadId?: number | null;
}

export interface UpdateDepartmentRequest {
  name: string;
  parentId?: number | null;
  leadId?: number | null;
}

// --- api/dto/response/TeamResponse (§4.1/§6.10/§6.6) -----------------------------------

export interface TeamResponse {
  id: number;
  name: string;
  departmentId: number;
  departmentName: string;
  leadId: number | null;
  leadName: string | null;
  active: boolean;
}

export interface CreateTeamRequest {
  name: string;
  departmentId: number;
  leadId?: number | null;
}

export interface UpdateTeamRequest {
  name: string;
  departmentId: number;
  leadId?: number | null;
}

// --- api/dto/response/EmailTemplateResponse (§6.8/§6.10) -------------------------------
// code est toujours un NotificationType (catalogue fermé) - jamais une clé arbitraire.

export interface EmailTemplateResponse {
  code: NotificationType;
  subject: string | null;
  bodyHtml: string | null;
  updatedAt: string | null;
  configured: boolean;
}

export interface UpdateEmailTemplateRequest {
  subject: string;
  bodyHtml: string;
}

// --- api/dto/response/SlaResponse (§6.7/§6.10) ------------------------------------------

export interface SlaResponse {
  id: number;
  requestTypeId: number;
  priority: Priority;
  firstResponseMinutes: number;
  resolutionMinutes: number;
  useBusinessCalendar: boolean;
}

export interface UpsertSlaRequest {
  firstResponseMinutes: number;
  resolutionMinutes: number;
}

// --- api/dto/response/DiagnosticsResponse (§6.10/§15.3) --------------------------------
// Chaque statut est "UP" | "DOWN" | "DISABLED" (service IA seulement, §12.2) - jamais un
// hôte, un port ou une chaîne de connexion (§6.10 - "sans exposer de secrets").

export type DiagnosticsStatus = 'UP' | 'DOWN' | 'DISABLED';

export interface DiagnosticsResponse {
  backendStatus: DiagnosticsStatus;
  databaseStatus: DiagnosticsStatus;
  aiServiceStatus: DiagnosticsStatus;
  mailStatus: DiagnosticsStatus;
  checkedAt: string;
}

// --- api/dto/response/UserResponse (§6.1/§6.10) ----------------------------------------
// Ne porte jamais passwordHash (§13).

export type UserRole = 'REQUESTER' | 'MANAGER' | 'AGENT' | 'SERVICE_MANAGER' | 'FUNCTIONAL_ADMIN' | 'TECHNICAL_ADMIN' | 'AUDITOR';

export type ScopeType = 'OWN' | 'TEAM' | 'DEPARTMENT' | 'DIRECTION' | 'GLOBAL';

export interface UserResponse {
  id: number;
  firstName: string;
  lastName: string;
  email: string;
  departmentId: number | null;
  departmentName: string | null;
  managerId: number | null;
  managerName: string | null;
  active: boolean;
  locked: boolean;
}

export interface CreateUserRequest {
  firstName: string;
  lastName: string;
  email: string;
  password: string;
  departmentId?: number | null;
  managerId?: number | null;
}

export interface UpdateUserRequest {
  firstName: string;
  lastName: string;
  email: string;
  departmentId?: number | null;
  managerId?: number | null;
}

export interface ResetPasswordRequest {
  newPassword: string;
}

// --- api/dto/response/UserRoleAssignmentResponse (§5.1/§6.10) --------------------------

export interface UserRoleAssignmentResponse {
  id: number;
  userId: number;
  role: UserRole;
  scopeType: ScopeType;
  scopeId: number | null;
  scopeName: string | null;
}

export interface CreateRoleAssignmentRequest {
  role: UserRole;
  scopeType: ScopeType;
  scopeId?: number | null;
}

// --- api/dto/response/ServiceCatalogAdminResponse / RequestTypeAdminResponse (§6.2/§6.10)
// Vue administration : inclut les fiches/types désactivés, contrairement à
// ServiceCatalogResponse/RequestTypeResponse (catalogue public). RG-02/RG-12 - jamais de
// suppression physique, seulement activate/deactivate.

export interface ServiceCatalogAdminResponse {
  id: number;
  name: string;
  description: string | null;
  category: string | null;
  departmentId: number;
  departmentName: string;
  displayOrder: number;
  active: boolean;
}

export interface UpsertServiceCatalogRequest {
  name: string;
  description?: string | null;
  category?: string | null;
  departmentId: number;
  displayOrder: number;
}

export interface RequestTypeAdminResponse {
  id: number;
  serviceCatalogId: number;
  name: string;
  description: string | null;
  targetDelayDescription: string | null;
  requiredDocuments: string | null;
  contactInfo: string | null;
  reopenAllowed: boolean;
  displayOrder: number;
  active: boolean;
}

export interface UpsertRequestTypeRequest {
  serviceCatalogId: number;
  name: string;
  description?: string | null;
  targetDelayDescription?: string | null;
  requiredDocuments?: string | null;
  contactInfo?: string | null;
  reopenAllowed: boolean;
  displayOrder: number;
}

// --- api/dto/response/FormDefinitionAdminResponse / FormFieldAdminResponse /
// FieldOptionAdminResponse (§6.3/§10.1/§6.10 - ADR-17, docs/DECISIONS.md) --------------
// PublicationStatus : "DRAFT" (modifiable) | "PUBLISHED" | "ARCHIVED" (immuables).

export type PublicationStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED';

export interface FieldOptionAdminResponse {
  id: number;
  value: string;
  label: string;
  displayOrder: number;
}

export interface UpsertFieldOptionRequest {
  value: string;
  label: string;
  displayOrder: number;
}

export interface FormFieldAdminResponse {
  id: number;
  code: string;
  label: string;
  fieldType: FieldType;
  required: boolean;
  displayOrder: number;
  helpText: string | null;
  visibleWhenFieldCode: string | null;
  visibleWhenValue: string | null;
  options: FieldOptionAdminResponse[];
}

export interface UpsertFormFieldRequest {
  code: string;
  label: string;
  fieldType: FieldType;
  required: boolean;
  displayOrder: number;
  helpText?: string | null;
  visibleWhenFieldCode?: string | null;
  visibleWhenValue?: string | null;
}

export interface FormDefinitionAdminResponse {
  id: number;
  requestTypeId: number;
  version: number;
  status: PublicationStatus;
  publishedAt: string | null;
  createdAt: string;
  fields: FormFieldAdminResponse[] | null;
}

// --- api/dto/response/StepAdminResponse / TransitionAdminResponse /
// WorkflowDefinitionAdminResponse (§6.5/§10.1/§6.10 - ADR-17) --------------------------

export interface StepAdminResponse {
  id: number;
  code: string;
  name: string;
  displayOrder: number;
  responsibleRole: UserRole | null;
  responsibleTeamId: number | null;
  responsibleTeamName: string | null;
  suspendSla: boolean;
}

export interface UpsertStepRequest {
  code: string;
  name: string;
  displayOrder: number;
  responsibleRole?: UserRole | null;
  responsibleTeamId?: number | null;
  suspendSla: boolean;
}

export interface TransitionAdminResponse {
  id: number;
  fromStepId: number;
  action: WorkflowAction;
  toStepId: number | null;
  conditionPriority: Priority | null;
  conditionDepartmentId: number | null;
  conditionDepartmentName: string | null;
  conditionFieldCode: string | null;
  conditionFieldValue: string | null;
}

export interface UpsertTransitionRequest {
  action: WorkflowAction;
  toStepId?: number | null;
  conditionPriority?: Priority | null;
  conditionDepartmentId?: number | null;
  conditionFieldCode?: string | null;
  conditionFieldValue?: string | null;
}

export interface WorkflowDefinitionAdminResponse {
  id: number;
  requestTypeId: number;
  version: number;
  status: PublicationStatus;
  publishedAt: string | null;
  createdAt: string;
  steps: StepAdminResponse[] | null;
  transitions: TransitionAdminResponse[] | null;
}
