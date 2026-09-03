import type { NotificationType, Priority, RequestStatus, WorkflowAction } from '../api/types';

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

const ROLE_LABELS: Record<string, string> = {
  REQUESTER: 'Demandeur',
  MANAGER: 'Responsable hiérarchique',
  AGENT: 'Agent de traitement',
  SERVICE_MANAGER: 'Responsable de service',
  FUNCTIONAL_ADMIN: 'Administrateur fonctionnel',
  TECHNICAL_ADMIN: 'Administrateur technique',
  AUDITOR: 'Auditeur',
};

/** §5 - libellé d'un rôle. Purement décoratif : aucun écran ne décide d'un droit sur cette
 * valeur (§11.1 - une action se lit dans `availableActions[]`, jamais dans un rôle). */
export function roleLabel(role: string): string {
  return ROLE_LABELS[role] ?? role;
}

/** Initiales de l'avatar (barre latérale et barre supérieure, maquettes 01 et 02). */
export function initials(firstName?: string | null, lastName?: string | null): string {
  const first = (firstName ?? '').trim().charAt(0);
  const last = (lastName ?? '').trim().charAt(0);
  return `${first}${last}`.toUpperCase() || '?';
}

const PRIORITY_LABELS: Record<Priority, string> = {
  LOW: 'Basse',
  MEDIUM: 'Moyenne',
  HIGH: 'Haute',
  CRITICAL: 'Critique',
};

/** §6.5 - priorité qualifiée (RequestService.qualify) ; `null` = pas encore qualifiée. */
export function priorityLabel(priority: Priority | null | undefined): string {
  if (!priority) {
    return 'Non qualifiée';
  }
  return PRIORITY_LABELS[priority] ?? priority;
}

const SLA_STATUS_LABELS: Record<string, string> = {
  ON_TRACK: 'Dans le délai',
  AT_RISK: 'À risque',
  OVERDUE: 'En retard',
  SUSPENDED: 'Suspendu',
};

/** §6.7 - les trois états visuels du SLA. La valeur vient du modèle matérialisé côté
 * serveur (RG-07) : cette fonction ne fait que la traduire, elle ne la recalcule jamais. */
export function slaStatusLabel(status: string | null | undefined): string {
  if (!status) {
    return '—';
  }
  return SLA_STATUS_LABELS[status] ?? status;
}

/** Échéance relative lisible ("dans 4 h", "+ 6 h") - §6.7, à côté de la pastille SLA. */
export function formatDeadline(value: string | null | undefined): string {
  if (!value) {
    return '—';
  }
  const diffMinutes = (new Date(value).getTime() - Date.now()) / 60000;
  const late = diffMinutes < 0;
  const absolute = Math.abs(diffMinutes);
  let amount: string;
  if (absolute < 60) {
    amount = `${Math.round(absolute)} min`;
  } else if (absolute < 60 * 24) {
    amount = `${Math.round(absolute / 60)} h`;
  } else {
    amount = `${Math.round(absolute / (60 * 24))} j`;
  }
  return late ? `+ ${amount}` : `dans ${amount}`;
}

/** §6.7 - même échéance que `formatDeadline`, formulée en toutes lettres pour les deux
 * horloges du détail, où "+ 22 h" seul se lirait aussi bien comme "dans 22 h". */
export function formatDeadlineLong(value: string | null | undefined): string {
  const relative = formatDeadline(value);
  if (relative === '—') {
    return relative;
  }
  return relative.startsWith('+ ') ? `dépassée de ${relative.slice(2)}` : relative;
}
