import { useEffect, useState } from 'react';
import * as requestsApi from '../api/requests';
import type { RequestHistoryResponse, WorkflowAction } from '../api/types';
import { ErrorBanner } from './ErrorBanner';
import { actionLabel, formatDate } from '../lib/format';

// §6.4 - "Affichage d'une frise d'avancement et de l'historique complet." Lecture suivant
// la même surface que le dossier lui-même (RequestService.getHistory -> getViewable,
// ADR-10) : aucun droit distinct à recalculer ici, une éventuelle erreur d'autorisation
// remonte via ErrorBanner comme partout ailleurs dans cette application.

interface RequestTimelineProps {
  requestId: number;
}

export function RequestTimeline({ requestId }: RequestTimelineProps) {
  const [entries, setEntries] = useState<RequestHistoryResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const result = await requestsApi.getHistory(requestId);
        if (!cancelled) {
          setEntries(result);
          setError(null);
        }
      } catch (loadError) {
        if (!cancelled) {
          setError(loadError);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [requestId]);

  return (
    <div className="request-timeline">
      <h2>Historique</h2>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      <ol className="timeline-list">
        {entries.map((entry) => (
          <li key={entry.id} className="timeline-item">
            <div className="timeline-meta">
              <strong>{actionLabel(entry.action as WorkflowAction)}</strong> par {entry.actorName} ·{' '}
              {formatDate(entry.occurredAt)}
            </div>
            {/* Une ligne d'entrée (SUBMIT/ADR-23, REOPEN/ADR-14) n'a pas d'étape de départ :
                le dossier entre dans le circuit, il ne passe pas d'une étape à une autre. */}
            {entry.toStepName && (
              <p className="timeline-step">
                {entry.fromStepName ? `${entry.fromStepName} → ${entry.toStepName}` : `→ ${entry.toStepName}`}
              </p>
            )}
            {entry.comment && <p>{entry.comment}</p>}
          </li>
        ))}
        {!loading && entries.length === 0 && <li className="timeline-empty">Aucune étape enregistrée pour l'instant.</li>}
      </ol>
    </div>
  );
}
