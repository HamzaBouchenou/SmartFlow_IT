import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as tasksApi from '../api/tasks';
import type { BulkActionResultResponse, PageResponse, Priority, RequestStatus, RequestSummaryResponse } from '../api/types';
import { EmptyState } from '../components/EmptyState';
import { ErrorBanner } from '../components/ErrorBanner';
import { SlaBadge } from '../components/SlaBadge';
import { formatDate, priorityLabel, statusLabel } from '../lib/format';

type Queue = 'mine' | 'team';

const STATUS_OPTIONS: RequestStatus[] = ['DRAFT', 'SUBMITTED', 'CLOSED', 'CANCELLED', 'ARCHIVED'];
const PRIORITY_OPTIONS: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** `TaskQueueFilterParams.submittedFrom/submittedTo` sont des `Instant` côté serveur, alors
 * qu'un `<input type="date">` ne donne qu'une date nue : l'envoyer telle quelle ferait
 * échouer la conversion Spring. La borne basse est donc le début du jour choisi, la borne
 * haute sa fin - une période saisie "du 1er au 1er" doit contenir le 1er en entier. */
function startOfDay(value: string): string | undefined {
  return value ? new Date(`${value}T00:00:00`).toISOString() : undefined;
}

function endOfDay(value: string): string | undefined {
  return value ? new Date(`${value}T23:59:59.999`).toISOString() : undefined;
}

/** §6.6 - "File personnelle « Mes tâches » et file d'équipe", avec les filtres (statut,
 * priorité, catégorie, demandeur, période, retard) et la pagination normalisée (§11.1,
 * PageResponse) que le back-end expose déjà. Maquette 06. */
export function TasksPage() {
  const [queue, setQueue] = useState<Queue>('mine');
  const [status, setStatus] = useState<RequestStatus | ''>('');
  const [priority, setPriority] = useState<Priority | ''>('');
  const [category, setCategory] = useState('');
  const [requesterId, setRequesterId] = useState('');
  const [submittedFrom, setSubmittedFrom] = useState('');
  const [submittedTo, setSubmittedTo] = useState('');
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
        category: category || undefined,
        requesterId: requesterId ? Number(requesterId) : undefined,
        submittedFrom: startOfDay(submittedFrom),
        submittedTo: endOfDay(submittedTo),
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
  }, [queue, status, priority, category, requesterId, submittedFrom, submittedTo, overdue, page, reloadToken]);

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

  function resetPage<T>(setter: (value: T) => void) {
    return (value: T) => {
      setter(value);
      setPage(0);
    };
  }

  return (
    <section>
      {/* Le compteur n'est porté que par la file affichée : celui de l'autre file
          demanderait un second appel pour une information qu'un clic donne déjà. */}
      <div className="tabs">
        <button type="button" className={queue === 'mine' ? 'tab active' : 'tab'} onClick={() => switchQueue('mine')}>
          File personnelle
          {queue === 'mine' && result && <span className="tab-count">{result.totalElements}</span>}
        </button>
        <button type="button" className={queue === 'team' ? 'tab active' : 'tab'} onClick={() => switchQueue('team')}>
          File d'équipe
          {queue === 'team' && result && <span className="tab-count">{result.totalElements}</span>}
        </button>
      </div>

      <div className="filter-bar">
        <label>
          Statut
          <select value={status} onChange={(event) => resetPage(setStatus)(event.target.value as RequestStatus | '')}>
            <option value="">Tous</option>
            {STATUS_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {statusLabel(option)}
              </option>
            ))}
          </select>
        </label>

        <label>
          Priorité
          <select value={priority} onChange={(event) => resetPage(setPriority)(event.target.value as Priority | '')}>
            <option value="">Toutes</option>
            {PRIORITY_OPTIONS.map((option) => (
              <option key={option} value={option}>
                {priorityLabel(option)}
              </option>
            ))}
          </select>
        </label>

        <label>
          Catégorie
          <input
            type="text"
            placeholder="Toutes"
            value={category}
            onChange={(event) => resetPage(setCategory)(event.target.value)}
          />
        </label>

        <label>
          Demandeur
          <input
            type="number"
            placeholder="Identifiant"
            value={requesterId}
            onChange={(event) => resetPage(setRequesterId)(event.target.value)}
          />
        </label>

        <label>
          Soumise depuis
          <input
            type="date"
            value={submittedFrom}
            onChange={(event) => resetPage(setSubmittedFrom)(event.target.value)}
          />
        </label>

        <label>
          Jusqu'au
          <input type="date" value={submittedTo} onChange={(event) => resetPage(setSubmittedTo)(event.target.value)} />
        </label>

        <label className="checkbox-label filter-toggle">
          <input
            type="checkbox"
            checked={overdue}
            onChange={(event) => resetPage(setOverdue)(event.target.checked)}
          />
          En retard uniquement
        </label>
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {result && (
        <>
          {result.content.length === 0 ? (
            <EmptyState message="Aucune tâche dans cette file avec ces filtres." />
          ) : (
            <div className="table-scroll">
              <table className="task-table">
                <thead>
                  <tr>
                    <th className="col-select">
                      <input
                        type="checkbox"
                        aria-label="Tout sélectionner"
                        checked={result.content.length > 0 && selected.size === result.content.length}
                        onChange={toggleSelectAll}
                      />
                    </th>
                    <th>Référence</th>
                    <th>Objet</th>
                    <th>Demandeur</th>
                    <th>Priorité</th>
                    <th>Étape</th>
                    <th>Soumise le</th>
                    <th>SLA</th>
                  </tr>
                </thead>
                <tbody>
                  {result.content.map((item) => (
                    <tr key={item.id} className={selected.has(item.id) ? 'row-selected' : undefined}>
                      <td className="col-select">
                        <input
                          type="checkbox"
                          aria-label={`Sélectionner ${item.reference}`}
                          checked={selected.has(item.id)}
                          onChange={() => toggleSelected(item.id)}
                        />
                      </td>
                      <td>
                        <Link to={`/demandes/${item.id}`} className="reference">
                          {item.reference}
                        </Link>
                      </td>
                      <td className="col-title">{item.title}</td>
                      <td>{item.requesterName}</td>
                      <td>
                        <span className={item.priority ? `priority-text priority-text-${item.priority.toLowerCase()}` : undefined}>
                          {priorityLabel(item.priority)}
                        </span>
                      </td>
                      <td>{item.currentStepName ?? '—'}</td>
                      <td>{formatDate(item.submittedAt)}</td>
                      <td>
                        <SlaBadge status={item.slaStatus} />
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}

          <div className="pagination">
            <button
              type="button"
              className="button-secondary"
              disabled={page === 0}
              onClick={() => setPage((current) => current - 1)}
            >
              Précédent
            </button>
            <span>
              Page {result.page + 1} / {Math.max(result.totalPages, 1)}
            </span>
            <button
              type="button"
              className="button-secondary"
              disabled={page + 1 >= result.totalPages}
              onClick={() => setPage((current) => current + 1)}
            >
              Suivant
            </button>
          </div>
        </>
      )}

      {/* §6.6 - "actions en masse limitées aux changements ne présentant pas de risque
          fonctionnel". La mention ci-dessous n'est pas décorative : elle dit pourquoi il
          n'y a ici ni "Valider" ni "Clôturer", plutôt que de laisser croire à un oubli. */}
      {selected.size > 0 && (
        <form className="bulk-actions-bar" onSubmit={(event) => void submitBulkAssign(event)}>
          <strong className="bulk-count">{selected.size} demande(s) sélectionnée(s)</strong>
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
          <span className="bulk-note">Validation et clôture en masse indisponibles</span>
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
    </section>
  );
}
