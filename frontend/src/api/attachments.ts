import { apiFetch, apiUpload } from './client';
import type { AttachmentResponse } from './types';

// RG-09/§13 - jamais d'URL de fichier devinable : le téléchargement est un lien direct
// vers cette même route authentifiée (voir AttachmentList), pas un champ d'URL séparé.

export function listAttachments(requestId: number) {
  return apiFetch<AttachmentResponse[]>(`/requests/${requestId}/attachments`);
}

export function uploadAttachment(requestId: number, file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return apiUpload<AttachmentResponse>(`/requests/${requestId}/attachments`, formData);
}

/** Chemin relatif à l'API (préfixé /api/v1 côté serveur) - à consommer via un lien `<a>`, jamais fetch/XHR. */
export function attachmentDownloadPath(requestId: number, attachmentId: number): string {
  return `/api/v1/requests/${requestId}/attachments/${attachmentId}`;
}
