import { Fragment, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as requestsApi from '../api/requests';
import type { ExecuteTransitionRequest, RequestDetailResponse } from '../api/types';
import { ActionBar } from '../components/ActionBar';
import { AiAssistPanel } from '../components/AiAssistPanel';
import { AttachmentList } from '../components/AttachmentList';
import { CommentThread } from '../components/CommentThread';
import { ErrorBanner } from '../components/ErrorBanner';
import { ReopenButton } from '../components/ReopenButton';
import { RequestTimeline } from '../components/RequestTimeline';
import { formatDate, statusLabel } from '../lib/format';

/** §6.4/§9.4 - "Données, statut, actions, commentaires, historique, SLA et pièces
 * jointes" pour un dossier. */
export function RequestDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [request, setRequest] = useState<RequestDetailResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [acting, setActing] = useState(false);

  useEffect(() => {
    if (!id) {
      return;
    }
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const result = await requestsApi.getRequest(Number(id));
        if (!cancelled) {
          setRequest(result);
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
  }, [id]);

  async function handleExecute(payload: ExecuteTransitionRequest) {
    if (!request) {
      return;
    }
    setError(null);
    try {
      const updated = await requestsApi.executeTransition(request.id, payload);
      setRequest(updated);
    } catch (executeError) {
      setError(executeError);
      throw executeError;
    }
  }

  /** RG-08/ADR-14 - jamais posté sur /transitions : voir ReopenButton/WorkflowAction.REOPEN's own javadoc. */
  async function handleReopen() {
    if (!request) {
      return;
    }
    setError(null);
    try {
      const updated = await requestsApi.reopenRequest(request.id);
      setRequest(updated);
    } catch (reopenError) {
      setError(reopenError);
      throw reopenError;
    }
  }

  async function handleSubmit() {
    if (!request) {
      return;
    }
    setActing(true);
    setError(null);
    try {
      const updated = await requestsApi.submitRequest(request.id);
      setRequest(updated);
    } catch (submitError) {
      setError(submitError);
    } finally {
      setActing(false);
    }
  }

  async function handleCancel() {
    if (!request) {
      return;
    }
    setActing(true);
    setError(null);
    try {
      const updated = await requestsApi.cancelRequest(request.id);
      setRequest(updated);
    } catch (cancelError) {
      setError(cancelError);
    } finally {
      setActing(false);
    }
  }

  if (loading) {
    return <p className="page-loading">Chargement…</p>;
  }
  if (!request) {
    return <ErrorBanner error={error} />;
  }

  // CLAUDE.md - "jamais un bouton depuis le rôle... plutôt que depuis availableActions[]" :
  // REOPEN n'est jamais posée sur /transitions (WorkflowAction.REOPEN's own javadoc côté
  // back-end), donc ActionBar ne doit jamais la recevoir - seule sa présence dans
  // availableActions[] décide si ReopenButton apparaît, jamais request.status directement.
  const transitionActions = request.availableActions.filter((action) => action !== 'REOPEN');
  const canReopen = request.availableActions.includes('REOPEN');

  return (
    <section className="request-detail">
      <p>
        <button type="button" className="link-button" onClick={() => navigate(-1)}>
          ← Retour
        </button>
      </p>

      <header className="request-detail-header">
        <h1>{request.title}</h1>
        <span className={`status-badge status-${request.status.toLowerCase()}`}>{statusLabel(request.status)}</span>
        {/* §6.7 - "indicateur visuel : dans le délai, à risque, en retard", lu tel quel
         * depuis le modèle SLA matérialisé (RG-07) - jamais recalculé ici. */}
        {request.slaStatus && (
          <span className={`sla-badge sla-${request.slaStatus.toLowerCase()}`}>{request.slaStatus}</span>
        )}
      </header>
      <p className="request-reference">Référence : {request.reference}</p>

      {request.description && <p>{request.description}</p>}

      <dl className="request-detail-meta">
        <dt>Priorité</dt>
        <dd>{request.priority ?? '—'}</dd>
        <dt>Soumise le</dt>
        <dd>{formatDate(request.submittedAt)}</dd>
        <dt>Affectée à</dt>
        <dd>{request.assignedUserName ?? request.assignedTeamName ?? '—'}</dd>
        <dt>Échéance de prise en charge</dt>
        <dd>{formatDate(request.slaDueAtFirstResponse)}</dd>
        <dt>Échéance de résolution</dt>
        <dd>{formatDate(request.slaDueAtResolution)}</dd>
        {request.reopenDeadline && (
          <>
            <dt>Réouverture possible jusqu'au</dt>
            <dd>{formatDate(request.reopenDeadline)}</dd>
          </>
        )}
        {request.closureReason && (
          <>
            <dt>Motif de clôture</dt>
            <dd>{request.closureReason}</dd>
          </>
        )}
        {request.closureSolution && (
          <>
            <dt>Solution apportée</dt>
            <dd>{request.closureSolution}</dd>
          </>
        )}
        {request.satisfactionRating !== null && (
          <>
            <dt>Niveau de satisfaction</dt>
            <dd>{request.satisfactionRating} / 5</dd>
          </>
        )}
      </dl>

      {Object.keys(request.fieldValues).length > 0 && (
        <>
          <h2>Détails du formulaire</h2>
          <dl className="request-detail-meta">
            {Object.entries(request.fieldValues).map(([code, value]) => (
              <Fragment key={code}>
                <dt>{code}</dt>
                <dd>{value}</dd>
              </Fragment>
            ))}
          </dl>
        </>
      )}

      <ErrorBanner error={error} />

      {request.status === 'DRAFT' && (
        <div className="request-form-buttons">
          <button type="button" disabled={acting} onClick={() => void handleSubmit()}>
            Soumettre
          </button>
          <button type="button" disabled={acting} onClick={() => void handleCancel()}>
            Annuler la demande
          </button>
        </div>
      )}

      {request.status !== 'DRAFT' && (transitionActions.length > 0 || canReopen) && (
        <>
          <h2>Actions disponibles</h2>
          {transitionActions.length > 0 && <ActionBar actions={transitionActions} onExecute={handleExecute} />}
          {canReopen && <ReopenButton onReopen={handleReopen} />}
        </>
      )}

      <RequestTimeline requestId={request.id} />
      <CommentThread requestId={request.id} />
      <AttachmentList requestId={request.id} />
      <AiAssistPanel requestId={request.id} />
    </section>
  );
}
