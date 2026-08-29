import { useEffect, useState } from 'react';
import * as aiApi from '../api/ai';
import type { AiAnalysisResponse, AiAnalysisType } from '../api/types';
import { ErrorBanner } from './ErrorBanner';
import { formatDate } from '../lib/format';

// §12/RG-10/ADR-16 (docs/DECISIONS.md) - "une aide, jamais une écriture directe sur une
// demande". Ce composant ne pose jamais Request.priority ni aucune valeur métier : valider
// une analyse (aiApi.validateAnalysis) ne fait que poser acceptedValue sur la ligne
// AiAnalysis elle-même. Appliquer une suggestion validée à la demande reste un geste humain
// séparé, via le formulaire de la demande - volontairement absent d'ici.

interface AiAssistPanelProps {
  requestId: number;
}

export function AiAssistPanel({ requestId }: AiAssistPanelProps) {
  const [analyses, setAnalyses] = useState<AiAnalysisResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [running, setRunning] = useState<AiAnalysisType | null>(null);

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestId]);

  async function load() {
    setLoading(true);
    try {
      const result = await aiApi.listAnalyses(requestId);
      setAnalyses(result);
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }

  async function runAnalysis(type: AiAnalysisType) {
    setRunning(type);
    setError(null);
    try {
      await aiApi.analyze(requestId, { analysisType: type });
      await load();
    } catch (analyzeError) {
      setError(analyzeError);
    } finally {
      setRunning(null);
    }
  }

  async function handleValidate(analysisId: number, acceptedValue: string) {
    setError(null);
    try {
      await aiApi.validateAnalysis(analysisId, { acceptedValue });
      await load();
    } catch (validateError) {
      setError(validateError);
    }
  }

  return (
    <div className="ai-assist-panel">
      <h2>
        Aide IA <span className="ai-disclaimer">— une suggestion, à valider (RG-10)</span>
      </h2>
      <ErrorBanner error={error} />

      <div className="ai-actions">
        <button type="button" disabled={running !== null} onClick={() => void runAnalysis('CLASSIFICATION')}>
          {running === 'CLASSIFICATION' ? 'Analyse en cours…' : 'Suggérer catégorie et priorité'}
        </button>
        <button type="button" disabled={running !== null} onClick={() => void runAnalysis('SUMMARY')}>
          {running === 'SUMMARY' ? 'Résumé en cours…' : 'Résumer la demande'}
        </button>
      </div>

      {loading && <p className="page-loading">Chargement…</p>}

      <ul className="ai-analysis-list">
        {analyses.map((analysis) => (
          <AiAnalysisItem key={analysis.id} analysis={analysis} onValidate={handleValidate} />
        ))}
        {!loading && analyses.length === 0 && <li className="ai-analysis-empty">Aucune analyse IA pour l'instant.</li>}
      </ul>
    </div>
  );
}

interface AiAnalysisItemProps {
  analysis: AiAnalysisResponse;
  onValidate: (analysisId: number, acceptedValue: string) => Promise<void>;
}

function AiAnalysisItem({ analysis, onValidate }: AiAnalysisItemProps) {
  const [acceptedValue, setAcceptedValue] = useState(analysis.acceptedValue ?? analysis.suggestedValue ?? '');
  const [submitting, setSubmitting] = useState(false);

  async function handleClick() {
    setSubmitting(true);
    try {
      await onValidate(analysis.id, acceptedValue);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <li className="ai-analysis-item">
      <div className="ai-analysis-meta">
        <span className="ai-analysis-type">{typeLabel(analysis.analysisType)}</span>
        {analysis.confidenceScore !== null && (
          <span className="ai-confidence">confiance {(analysis.confidenceScore * 100).toFixed(0)} %</span>
        )}
        <span className="ai-analysis-date">{formatDate(analysis.createdAt)}</span>
      </div>

      <p className="ai-suggestion">{describeSuggestion(analysis)}</p>

      {analysis.validatedAt ? (
        <p className="ai-validated">
          Validée par {analysis.validatedByName} le {formatDate(analysis.validatedAt)} : {analysis.acceptedValue}
        </p>
      ) : (
        <div className="ai-validate-form">
          <label htmlFor={`ai-accept-${analysis.id}`}>Valeur acceptée</label>
          <textarea
            id={`ai-accept-${analysis.id}`}
            value={acceptedValue}
            onChange={(event) => setAcceptedValue(event.target.value)}
          />
          <button type="button" disabled={submitting || !acceptedValue.trim()} onClick={() => void handleClick()}>
            Valider cette suggestion
          </button>
        </div>
      )}
    </li>
  );
}

function typeLabel(type: AiAnalysisType): string {
  switch (type) {
    case 'CLASSIFICATION':
      return 'Catégorie et priorité suggérées';
    case 'SUMMARY':
      return 'Résumé';
    case 'DOCUMENT_SEARCH':
      return 'Recherche documentaire';
    case 'REPLY_SUGGESTION':
      return 'Suggestion de réponse';
  }
}

/** CLASSIFICATION porte un petit JSON ({"category","priority"} - ADR-16) ; les autres types, du texte brut. */
function describeSuggestion(analysis: AiAnalysisResponse): string {
  if (!analysis.suggestedValue) {
    return '—';
  }
  if (analysis.analysisType !== 'CLASSIFICATION') {
    return analysis.suggestedValue;
  }
  try {
    const parsed = JSON.parse(analysis.suggestedValue) as { category?: string; priority?: string };
    return `Catégorie : ${parsed.category ?? '—'} · Priorité : ${parsed.priority ?? '—'}`;
  } catch {
    return analysis.suggestedValue;
  }
}
