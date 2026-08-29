import type { NotificationType, RequestStatus, WorkflowAction } from '../api/types';

// Libellés d'affichage en français (l'utilisateur final est francophone, §1 du CDC) pour
// des valeurs dont le code reste en anglais côté API (CLAUDE.md - "vocabulaire métier en
// anglais dans le code").

const STATUS_LABELS: Record<RequestStatus, string> = {
  DRAFT: 'Brouillon',
  SUBMITTED: 'En cours',
  CLOSED: 'Clôturée',
  CANCELLED: 'Annulée',
  ARCHIVED: 'Archivée',
};

const ACTION_LABELS: Record<WorkflowAction, string> = {
  VALIDATE: 'Valider',
  REJECT: 'Rejeter',
  RETURN: 'Retourner',
  ASSIGN: 'Affecter',
  REQUEST_INFO: 'Demander un complément',
  CLOSE: 'Clôturer',
  REOPEN: 'Rouvrir',
};

const NOTIFICATION_LABELS: Record<NotificationType, string> = {
  SUBMISSION: 'Demande soumise',
  ASSIGNMENT: 'Demande affectée',
  INFO_REQUESTED: 'Complément demandé',
  DECISION: 'Décision prise',
  SLA_WARNING: 'Échéance proche',
  SLA_BREACH: 'Échéance dépassée',
  CLOSURE: 'Demande clôturée',
};

export function statusLabel(status: RequestStatus): string {
  return STATUS_LABELS[status] ?? status;
}

export function actionLabel(action: WorkflowAction): string {
  return ACTION_LABELS[action] ?? action;
}

export function notificationTypeLabel(type: NotificationType): string {
  return NOTIFICATION_LABELS[type] ?? type;
}

export function formatDate(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  return new Date(value).toLocaleString('fr-FR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  });
}

/** RG-09 - taille d'une pièce jointe, lisible (octets bruts jusqu'à Mo). */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes} o`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} Ko`;
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)} Mo`;
}

/** §6.9 - délais moyens de prise en charge/résolution ; `null` = aucune donnée sur la période. */
export function formatMinutes(minutes: number | null | undefined): string {
  if (minutes === null || minutes === undefined) {
    return '—';
  }
  if (minutes < 60) {
    return `${Math.round(minutes)} min`;
  }
  const hours = minutes / 60;
  if (hours < 24) {
    return `${hours.toFixed(1)} h`;
  }
  return `${(hours / 24).toFixed(1)} j`;
}

/** §6.9 - taux de respect des SLA / de réouverture ; `null` = aucune donnée sur la période. */
export function formatPercent(value: number | null | undefined): string {
  if (value === null || value === undefined) {
    return '—';
  }
  return `${value.toFixed(1)} %`;
}
