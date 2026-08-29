import { apiFetch } from './client';
import type { CommentResponse, CreateCommentRequest } from './types';

// §6.4 - "Ajout de commentaires... autorisés" (ADR-11, docs/DECISIONS.md).

export function listComments(requestId: number) {
  return apiFetch<CommentResponse[]>(`/requests/${requestId}/comments`);
}

export function addComment(requestId: number, request: CreateCommentRequest) {
  return apiFetch<CommentResponse>(`/requests/${requestId}/comments`, { method: 'POST', body: request });
}
