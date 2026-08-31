import { useEffect, useState } from 'react';
import * as adminApi from '../api/admin';
import type { DiagnosticsResponse, DiagnosticsStatus } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate } from '../lib/format';

const STATUS_LABELS: Record<DiagnosticsStatus, string> = {
  UP: 'Opérationnel',
  DOWN: 'Indisponible',
  DISABLED: 'Désactivé',
};

/** §6.10/§15.3 - "Page de diagnostic affichant l'état des services techniques sans
 * exposer de secrets" : chaque ligne est UP/DOWN/DISABLED, jamais un hôte ou un port. */
export function AdminDiagnosticsPage() {
  const [diagnostics, setDiagnostics] = useState<DiagnosticsResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  async function load() {
    setLoading(true);
    try {
      const result = await adminApi.checkDiagnostics();
      setDiagnostics(result);
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    let cancelled = false;
    async function initialLoad() {
      setLoading(true);
      try {
        const result = await adminApi.checkDiagnostics();
        if (!cancelled) {
          setDiagnostics(result);
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
    void initialLoad();
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <section>
      <h1>Diagnostic</h1>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {diagnostics && (
        <>
          <ul className="parameter-list">
            <DiagnosticRow label="Back-end" status={diagnostics.backendStatus} />
            <DiagnosticRow label="Base de données" status={diagnostics.databaseStatus} />
            <DiagnosticRow label="Service IA" status={diagnostics.aiServiceStatus} />
            <DiagnosticRow label="Envoi d'e-mails" status={diagnostics.mailStatus} />
          </ul>
          <p className="request-reference">Vérifié le {formatDate(diagnostics.checkedAt)}</p>
          <button type="button" onClick={() => void load()} disabled={loading}>
            Revérifier
          </button>
        </>
      )}
    </section>
  );
}

function DiagnosticRow({ label, status }: { label: string; status: DiagnosticsStatus }) {
  return (
    <li className="parameter-item">
      <div className="parameter-meta">
        <strong>{label}</strong> <span className={`sla-badge diag-${status.toLowerCase()}`}>{STATUS_LABELS[status]}</span>
      </div>
    </li>
  );
}
