import { apiFetch } from './client';
import type { AiAnalysisResponse, AnalyzeRequest, ValidateAiAnalysisRequest } from './types';

// §12/RG-10/ADR-16 (docs/DECISIONS.md) - une aide, jamais une écriture directe sur la
// demande. validate() ne fait que poser acceptedValue sur l'analyse elle-même.

export function analyze(requestId: number, request: AnalyzeRequest) {
  return apiFetch<AiAnalysisResponse>(`/ai/requests/${requestId}/analyze`, { method: 'POST', body: request });
}

export function listAnalyses(requestId: number) {
  return apiFetch<AiAnalysisResponse[]>(`/ai/requests/${requestId}/analyses`);
}

export function validateAnalysis(analysisId: number, request: ValidateAiAnalysisRequest) {
  return apiFetch<AiAnalysisResponse>(`/ai/analyses/${analysisId}/validate`, { method: 'POST', body: request });
}
