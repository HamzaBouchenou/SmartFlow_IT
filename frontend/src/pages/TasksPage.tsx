import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as tasksApi from '../api/tasks';
import type { BulkActionResultResponse, PageResponse, Priority, RequestStatus, RequestSummaryResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate, statusLabel } from '../lib/format';

type Queue = 'mine' | 'team';

const STATUS_OPTIONS: RequestStatus[] = ['DRAFT', 'SUBMITTED', 'CLOSED', 'CANCELLED', 'ARCHIVED'];
const PRIORITY_OPTIONS: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** §6.6 - "File personnelle « Mes tâches » et file d'équipe", avec les filtres (statut,
 * priorité, catégorie, retard) et la pagination normalisée (§11.1, PageResponse) que le
 * back-end expose déjà. */
export function TasksPage() {
  const [queue, setQueue] = useState<Queue>('mine');
  const [status, setStatus] = useState<RequestStatus | ''>('');
  const [priority, setPriority] = useState<Priority | ''>('');
  const [overdue, setOverdue] = useState(false);
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<RequestSummaryResponse> | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  // §6.6 - "Actions en masse limitées aux changements ne présentant pas de risque
  // fonctionnel" : sélection multi-lignes, uniquement pour ré-affecter (jamais valider/
  // rejeter/clôturer en masse - BulkAssignmentService.java n'expose que ASSIGN).
  const [selected, setSelected] = useState<Set<number>>(new Set());
  const [bulkAssignedUserId, setBulkAssignedUserId] = useState('');
  const [bulkAssignedTeamId, setBulkAssignedTeamId] = useState('');
  const [bulkAutoAssign, setBulkAutoAssign] = useState(false);
  const [bulkSubmitting, setBulkSubmitting] = useState(false);
  const [bulkResult, setBulkResult] = useState<BulkActionResultResponse | null>(null);
  const [bulkError, setBulkError] = useState<unknown>(null);
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      const filter = {
        status: status || undefined,
        priority: priority || undefined,
        overdue: overdue || undefined,
        page,
        size: 20,
      };
      try {
        const response = await (queue === 'mine' ? tasksApi.myTasks(filter) : tasksApi.teamTasks(filter));
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
  }, [queue, status, priority, overdue, page, reloadToken]);

  function switchQueue(next: Queue) {
    setQueue(next);
    setPage(0);
    setSelected(new Set());
    setBulkResult(null);
  }

  function toggleSelected(id: number) {
    setSelected((current) => {
      const next = new Set(current);
      if (next.has(id)) {
        next.delete(id);
      } else {
        next.add(id);
      }
      return next;
    });
  }

  function toggleSelectAll() {
    if (!result) {
      return;
    }
    setSelected((current) =>
      current.size === result.content.length ? new Set() : new Set(result.content.map((item) => item.id)),
    );
  }

  async function submitBulkAssign(event: React.FormEvent) {
    event.preventDefault();
    setBulkSubmitting(true);
    setBulkError(null);
    setBulkResult(null);
    try {
      const response = await tasksApi.bulkAssign({
        requestIds: Array.from(selected),
        assignedUserId: !bulkAutoAssign && bulkAssignedUserId ? Number(bulkAssignedUserId) : null,
        assignedTeamId: bulkAssignedTeamId ? Number(bulkAssignedTeamId) : null,
        autoAssign: bulkAutoAssign,
      });
      setBulkResult(response);
      setSelected(new Set());
      setReloadToken((current) => current + 1);
    } catch (bulkSubmitError) {
      setBulkError(bulkSubmitError);
    } finally {
      setBulkSubmitting(false);
    }
  }

  return (
    <section>
      <h1>Mes tâches</h1>

      <div className="tabs">
        <button type="button" className={queue === 'mine' ? 'tab active' : 'tab'} onClick={() => switchQueue('mine')}>
          File personnelle
        </button>
        <button type="button" className={queue === 'team' ? 'tab active' : 'tab'} onClick={() => switchQueue('team')}>
          File d'équipe
        </button>
      </div>

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
        <select
          value={priority}
          onChange={(event) => {
            setPriority(event.target.value as Priority | '');
            setPage(0);
          }}
        >
          <option value="">Toutes priorités</option>
          {PRIORITY_OPTIONS.map((option) => (
            <option key={option} value={option}>
              {option}
            </option>
          ))}
        </select>
        <label className="checkbox-label">
          <input
            type="checkbox"
            checked={overdue}
            onChange={(event) => {
              setOverdue(event.target.checked);
              setPage(0);
            }}
          />
          En retard uniquement
        </label>
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {selected.size > 0 && (
        <form className="bulk-actions-bar" onSubmit={(event) => void submitBulkAssign(event)}>
          <strong>{selected.size} sélectionnée(s)</strong>
          {!bulkAutoAssign && (
            <input
              type="number"
              placeholder="Id agent"
              aria-label="Identifiant de l'agent"
              value={bulkAssignedUserId}
              onChange={(event) => setBulkAssignedUserId(event.target.value)}
            />
          )}
          <input
            type="number"
            placeholder="Id équipe"
            aria-label="Identifiant de l'équipe"
            value={bulkAssignedTeamId}
            onChange={(event) => setBulkAssignedTeamId(event.target.value)}
          />
          <label className="checkbox-label">
            <input
              type="checkbox"
              checked={bulkAutoAssign}
              onChange={(event) => setBulkAutoAssign(event.target.checked)}
            />
            Affectation automatique
          </label>
          <button type="submit" disabled={bulkSubmitting}>
            Affecter la sélection
          </button>
        </form>
      )}
      {bulkError !== null && <ErrorBanner error={bulkError} />}
      {bulkResult && (
        <p className="field-help">
          {bulkResult.results.filter((item) => item.success).length} affectation(s) réussie(s) sur{' '}
          {bulkResult.results.length}
          {bulkResult.results.some((item) => !item.success) && (
            <>
              {' '}
              — échecs :{' '}
              {bulkResult.results
                .filter((item) => !item.success)
                .map((item) => `#${item.requestId} (${item.errorCode})`)
                .join(', ')}
            </>
          )}
        </p>
      )}

      {result && (
        <>
          <table className="task-table">
            <thead>
              <tr>
                <th>
                  <input
                    type="checkbox"
                    aria-label="Tout sélectionner"
                    checked={result.content.length > 0 && selected.size === result.content.length}
                    onChange={toggleSelectAll}
                  />
                </th>
                <th>Référence</th>
                <th>Titre</th>
                <th>Statut</th>
                <th>Priorité</th>
                <th>Demandeur</th>
                <th>Étape</th>
                <th>Soumise le</th>
                <th>SLA</th>
              </tr>
            </thead>
            <tbody>
              {result.content.map((item) => (
                <tr key={item.id}>
                  <td>
                    <input
                      type="checkbox"
                      aria-label={`Sélectionner ${item.reference}`}
                      checked={selected.has(item.id)}
                      onChange={() => toggleSelected(item.id)}
                    />
                  </td>
                  <td>
                    <Link to={`/demandes/${item.id}`}>{item.reference}</Link>
                  </td>
                  <td>{item.title}</td>
                  <td>{statusLabel(item.status)}</td>
                  <td>{item.priority ?? '—'}</td>
                  <td>{item.requesterName}</td>
                  <td>{item.currentStepName ?? '—'}</td>
                  <td>{formatDate(item.submittedAt)}</td>
                  <td>{item.slaStatus ?? '—'}</td>
                </tr>
              ))}
              {result.content.length === 0 && (
                <tr>
                  <td colSpan={9}>Aucune tâche dans cette file.</td>
                </tr>
              )}
            </tbody>
          </table>

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
