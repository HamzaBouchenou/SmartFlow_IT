import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as requestsApi from '../api/requests';
import type { PageResponse, RequestStatus, RequestSummaryResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate, statusLabel } from '../lib/format';

const STATUS_OPTIONS: RequestStatus[] = ['DRAFT', 'SUBMITTED', 'CLOSED', 'CANCELLED', 'ARCHIVED'];

/** §6.9/§9.4 - "vue demandeur : demandes en cours, dernières décisions et délais
 * annoncés" (RG-06 - "un demandeur ne voit que ses dossiers"). À la différence de "Mes
 * tâches" (§6.6, TasksPage), cette liste porte aussi les brouillons du demandeur - c'est
 * l'écran où un brouillon abandonné peut être retrouvé et repris. */
export function MyRequestsPage() {
  const [status, setStatus] = useState<RequestStatus | ''>('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<RequestSummaryResponse> | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const response = await requestsApi.listMine(status || undefined, page, 20);
        if (!cancelled) {
          setResult(response);
          setError(null);
        }
      } catch (fetchError) {
        if (!cancelled) {
          setError(fetchError);
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
  }, [status, page]);

  return (
    <section>

      <div className="task-filters">
        <select
          value={status}
          onChange={(event) => {
            setStatus(event.target.value as RequestStatus | '');
            setPage(0);
          }}
        >
          <option value="">Tous les statuts</option>
          {STATUS_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {statusLabel(option)}
            </option>
          ))}
        </select>
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {result && (
        <>
          <div className="table-scroll">
            <table className="task-table">
              <thead>
                <tr>
                  <th>Référence</th>
                  <th>Titre</th>
                  <th>Statut</th>
                  <th>Étape</th>
                  <th>Soumise le</th>
                  <th>SLA</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((item) => (
                  <tr key={item.id}>
                    <td>
                      <Link to={`/demandes/${item.id}`}>{item.reference}</Link>
                    </td>
                    <td>{item.title}</td>
                    <td>{statusLabel(item.status)}</td>
                    <td>{item.currentStepName ?? '—'}</td>
                    <td>{formatDate(item.submittedAt)}</td>
                    <td>{item.slaStatus ?? '—'}</td>
                  </tr>
                ))}
                {result.content.length === 0 && (
                  <tr>
                    <td colSpan={6}>Aucune demande pour l'instant.</td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
              Précédent
            </button>
            <span>
              Page {result.page + 1} / {Math.max(result.totalPages, 1)}
            </span>
            <button
              type="button"
              disabled={page + 1 >= result.totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              Suivant
            </button>
          </div>
        </>
      )}
    </section>
  );
}
