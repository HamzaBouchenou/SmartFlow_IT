import { useEffect, useState } from 'react';
import * as adminApi from '../api/admin';
import type { AuditLogResponse, PageResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate } from '../lib/format';

/** §6.10/§13.1 - "Journal d'audit consultable avec filtres par utilisateur, action, objet
 * et période", lecture seule (RG-11's writer n'a lui-même aucune UI). Réservé par le
 * serveur à FUNCTIONAL_ADMIN/TECHNICAL_ADMIN/AUDITOR (canViewAuditLog) - un autre rôle
 * reçoit un 404, affiché ici comme n'importe quelle autre erreur (§11.1 - "le masquage
 * dans l'interface ne suffit pas", ce lien de navigation n'est donc jamais masqué par le
 * rôle non plus). */
export function AdminAuditLogPage() {
  const [action, setAction] = useState('');
  const [objectType, setObjectType] = useState('');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<AuditLogResponse> | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const response = await adminApi.searchAuditLog({
          action: action || undefined,
          objectType: objectType || undefined,
          page,
          size: 20,
        });
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
  }, [action, objectType, page]);

  return (
    <section>

      <div className="task-filters">
        <input
          placeholder="Action (ex. SUBMIT, REOPEN, UPDATE_PARAMETER)"
          value={action}
          onChange={(event) => {
            setAction(event.target.value);
            setPage(0);
          }}
        />
        <input
          placeholder="Type d'objet (ex. Request, SystemParameter)"
          value={objectType}
          onChange={(event) => {
            setObjectType(event.target.value);
            setPage(0);
          }}
        />
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {result && (
        <>
          <div className="table-scroll">
            <table className="task-table">
              <thead>
                <tr>
                  <th>Date</th>
                  <th>Acteur</th>
                  <th>Action</th>
                  <th>Objet</th>
                  <th>Résultat</th>
                  <th>Résumé</th>
                </tr>
              </thead>
              <tbody>
                {result.content.map((entry) => (
                  <tr key={entry.id}>
                    <td>{formatDate(entry.occurredAt)}</td>
                    <td>{entry.actorName ?? 'Système'}</td>
                    <td>{entry.action}</td>
                    <td>
                      {entry.objectType}
                      {entry.objectId ? ` #${entry.objectId}` : ''}
                    </td>
                    <td>{entry.result}</td>
                    <td>{entry.summary ?? '—'}</td>
                  </tr>
                ))}
                {result.content.length === 0 && (
                  <tr>
                    <td colSpan={6}>Aucune entrée pour ces filtres.</td>
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
