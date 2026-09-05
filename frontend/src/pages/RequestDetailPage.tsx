import { Fragment, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import * as requestsApi from '../api/requests';
import type { ExecuteTransitionRequest, Priority, RequestDetailResponse } from '../api/types';
import { ActionBar } from '../components/ActionBar';
import { AiAssistPanel } from '../components/AiAssistPanel';
import { AttachmentList } from '../components/AttachmentList';
import { CommentThread } from '../components/CommentThread';
import { ErrorBanner } from '../components/ErrorBanner';
import { QualifyPanel } from '../components/QualifyPanel';
import { ReopenButton } from '../components/ReopenButton';
import { RequestTimeline } from '../components/RequestTimeline';
import { SlaBadge } from '../components/SlaBadge';
import { formatDate, formatDeadlineLong, priorityLabel, statusLabel } from '../lib/format';

/** §6.4/§9.4 - "Données, statut, actions, commentaires, historique, SLA et pièces
 * jointes" pour un dossier. Maquette 05. */

/** Les valeurs du formulaire arrivent indexées par *code* de champ (`fieldValues`), pas par
 * libellé : `RequestDetailResponse` ne porte pas la définition de formulaire. On rend donc
 * le code lisible sans prétendre connaître le libellé configuré (§6.3). */
function readableFieldCode(code: string): string {
  const spaced = code.replace(/[_-]+/g, ' ').trim();
  return spaced.charAt(0).toUpperCase() + spaced.slice(1);
}

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

  /** §5/RG-07 - jamais posté sur /transitions : voir QualifyPanel/RequestService.qualify's own javadoc. */
  async function handleQualify(priority: Priority) {
    if (!request) {
      return;
    }
    setError(null);
    try {
      const updated = await requestsApi.qualifyRequest(request.id, { priority });
      setRequest(updated);
    } catch (qualifyError) {
      setError(qualifyError);
      throw qualifyError;
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
  const isDraft = request.status === 'DRAFT';
  const hasActions = isDraft || request.canQualify || transitionActions.length > 0 || canReopen;
  const hasClosure =
    request.closureReason !== null ||
    request.closureSolution !== null ||
    request.satisfactionRating !== null ||
    request.reopenDeadline !== null;

  return (
    <section className="request-detail">
      <nav className="breadcrumb">
        <button type="button" className="link-button" onClick={() => navigate(-1)}>
          ← Retour
        </button>
      </nav>

      <ErrorBanner error={error} />

      <div className="detail-layout">
        <div className="detail-col">
          <section className="panel detail-summary">
            <span className="reference">{request.reference}</span>
            <div className="detail-title-row">
              <h2>{request.title}</h2>
              <div className="badge-row">
                <span className={`status-badge status-${request.status.toLowerCase()}`}>
                  {statusLabel(request.status)}
                </span>
                {request.priority && (
                  <span className={`status-badge priority-${request.priority.toLowerCase()}`}>
                    Priorité {priorityLabel(request.priority).toLowerCase()}
                  </span>
                )}
              </div>
            </div>
            <p className="detail-submeta">
              Soumise le {formatDate(request.submittedAt)} · Affectée à{' '}
              {request.assignedUserName ?? request.assignedTeamName ?? 'personne'}
            </p>

            {request.description && <p className="detail-description">{request.description}</p>}

            {/* §6.7 - deux horloges distinctes, jamais une seule barre. Les deux échéances
             * viennent du modèle SLA matérialisé (RG-07) ; le statut, lui, n'existe que pour
             * la résolution - `SlaSweepScheduler` ne calcule `slaStatus` que sur
             * `resolutionMinutes`. Aucun statut n'est donc déduit ici pour la prise en
             * charge : le recalculer côté client serait exactement ce que RG-07 interdit. */}
            <div className="sla-clocks">
              <div className="sla-clock">
                <span className="sla-clock-label">Prise en charge</span>
                <span className="sla-clock-value">
                  {request.slaDueAtFirstResponse ? (
                    <>
                      Échéance {formatDate(request.slaDueAtFirstResponse)}
                      <span className="sla-clock-relative">{formatDeadlineLong(request.slaDueAtFirstResponse)}</span>
                    </>
                  ) : (
                    'Aucune échéance calculée'
                  )}
                </span>
              </div>
              <div className="sla-clock">
                <span className="sla-clock-label">Résolution</span>
                <span className="sla-clock-value">
                  <SlaBadge status={request.slaStatus} />
                  {request.slaDueAtResolution && (
                    <>
                      Échéance {formatDate(request.slaDueAtResolution)}
                      <span className="sla-clock-relative">{formatDeadlineLong(request.slaDueAtResolution)}</span>
                    </>
                  )}
                </span>
              </div>
            </div>
          </section>

          {Object.keys(request.fieldValues).length > 0 && (
            <section className="panel">
              <header className="panel-head">
                <h2>Données du formulaire</h2>
              </header>
              <dl className="request-detail-meta detail-values">
                {Object.entries(request.fieldValues).map(([code, value]) => (
                  <Fragment key={code}>
                    <dt>{readableFieldCode(code)}</dt>
                    <dd>{value}</dd>
                  </Fragment>
                ))}
              </dl>
            </section>
          )}

          {hasClosure && (
            <section className="panel">
              <header className="panel-head">
                <h2>Clôture</h2>
              </header>
              <dl className="request-detail-meta detail-values">
                {request.closureReason && (
                  <>
                    <dt>Motif</dt>
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
                    <dt>Satisfaction</dt>
                    <dd>{request.satisfactionRating} / 5</dd>
                  </>
                )}
                {request.reopenDeadline && (
                  <>
                    <dt>Réouverture possible jusqu'au</dt>
                    <dd>{formatDate(request.reopenDeadline)}</dd>
                  </>
                )}
              </dl>
            </section>
          )}

          <CommentThread requestId={request.id} />
          <AttachmentList requestId={request.id} />
        </div>

        <aside className="detail-col detail-rail">
          {/* Règle 1 du README des maquettes : ces boutons se rendent depuis
              `availableActions[]`/`canQualify`, calculés par le serveur - jamais depuis le
              rôle ou le statut côté React (§11.1). La légende "calculées par le serveur" de
              la maquette est une note de maquette, pas du texte à afficher. */}
          {hasActions && (
            <section className="panel actions-panel">
              <header className="panel-head">
                <h2>Actions disponibles</h2>
              </header>

              {isDraft && (
                <div className="action-bar-buttons">
                  <button type="button" disabled={acting} onClick={() => void handleSubmit()}>
                    Soumettre
                  </button>
                  <button
                    type="button"
                    className="button-secondary"
                    disabled={acting}
                    onClick={() => void handleCancel()}
                  >
                    Annuler la demande
                  </button>
                </div>
              )}

              {request.canQualify && <QualifyPanel currentPriority={request.priority} onQualify={handleQualify} />}

              {transitionActions.length > 0 && <ActionBar actions={transitionActions} onExecute={handleExecute} />}
              {canReopen && <ReopenButton onReopen={handleReopen} />}
            </section>
          )}

          <AiAssistPanel requestId={request.id} />
          <RequestTimeline requestId={request.id} />
        </aside>
      </div>
    </section>
  );
}
