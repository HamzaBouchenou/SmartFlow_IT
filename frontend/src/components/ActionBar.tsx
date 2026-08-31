import { useState } from 'react';
import type { WorkflowAction, ExecuteTransitionRequest } from '../api/types';
import { actionLabel } from '../lib/format';

// §6.5 / CLAUDE.md - "Rendre un bouton d'action depuis le rôle côté React plutôt que
// depuis availableActions[]" est explicitement listé comme ce qu'il ne faut jamais faire :
// ce composant ne connaît ni le rôle ni le statut de la demande, uniquement la liste
// `actions` que le serveur a déjà filtrée via AuthorizationService.canAct
// (RequestDetailResponse.availableActions). Il ne fait par lui-même aucune hypothèse sur
// qui peut voir quoi.

const COMMENT_REQUIRED: WorkflowAction[] = ['REJECT']; // RG-05

interface ActionBarProps {
  actions: WorkflowAction[];
  onExecute: (payload: ExecuteTransitionRequest) => Promise<void>;
}

export function ActionBar({ actions, onExecute }: ActionBarProps) {
  const [openAction, setOpenAction] = useState<WorkflowAction | null>(null);
  const [comment, setComment] = useState('');
  const [closureReason, setClosureReason] = useState('');
  const [closureSolution, setClosureSolution] = useState('');
  const [satisfactionRating, setSatisfactionRating] = useState('');
  const [assignedUserId, setAssignedUserId] = useState('');
  const [assignedTeamId, setAssignedTeamId] = useState('');
  const [autoAssign, setAutoAssign] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  if (actions.length === 0) {
    return <p className="no-action">Aucune action possible pour le moment.</p>;
  }

  function resetForm() {
    setOpenAction(null);
    setComment('');
    setClosureReason('');
    setClosureSolution('');
    setSatisfactionRating('');
    setAssignedUserId('');
    setAssignedTeamId('');
    setAutoAssign(false);
  }

  async function confirm(action: WorkflowAction) {
    setSubmitting(true);
    try {
      await onExecute({
        action,
        comment: comment || null,
        closureReason: action === 'CLOSE' ? closureReason : null,
        closureSolution: action === 'CLOSE' ? closureSolution : null,
        satisfactionRating: action === 'CLOSE' && satisfactionRating ? Number(satisfactionRating) : null,
        assignedUserId: action === 'ASSIGN' && !autoAssign && assignedUserId ? Number(assignedUserId) : null,
        assignedTeamId: action === 'ASSIGN' && assignedTeamId ? Number(assignedTeamId) : null,
        autoAssign: action === 'ASSIGN' ? autoAssign : false,
      });
      resetForm();
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="action-bar">
      <div className="action-bar-buttons">
        {actions.map((action) => (
          <button
            key={action}
            type="button"
            className={`action-button action-${action.toLowerCase()}`}
            onClick={() => setOpenAction(openAction === action ? null : action)}
          >
            {actionLabel(action)}
          </button>
        ))}
      </div>

      {openAction && (
        <form
          className="action-form"
          onSubmit={(event) => {
            event.preventDefault();
            void confirm(openAction);
          }}
        >
          <h3>{actionLabel(openAction)}</h3>

          {openAction === 'CLOSE' ? (
            <>
              <label htmlFor="closure-reason">
                Motif de clôture <span className="required-mark">*</span>
              </label>
              <textarea
                id="closure-reason"
                required
                value={closureReason}
                onChange={(event) => setClosureReason(event.target.value)}
              />
              <label htmlFor="closure-solution">Solution apportée</label>
              <textarea
                id="closure-solution"
                value={closureSolution}
                onChange={(event) => setClosureSolution(event.target.value)}
              />
              <label htmlFor="satisfaction-rating">Niveau de satisfaction (facultatif)</label>
              <select
                id="satisfaction-rating"
                value={satisfactionRating}
                onChange={(event) => setSatisfactionRating(event.target.value)}
              >
                <option value="">Non renseigné</option>
                <option value="1">1 - très insatisfait</option>
                <option value="2">2 - insatisfait</option>
                <option value="3">3 - neutre</option>
                <option value="4">4 - satisfait</option>
                <option value="5">5 - très satisfait</option>
              </select>
            </>
          ) : (
            <>
              <label htmlFor="action-comment">
                Commentaire
                {COMMENT_REQUIRED.includes(openAction) && <span className="required-mark"> *</span>}
              </label>
              <textarea
                id="action-comment"
                required={COMMENT_REQUIRED.includes(openAction)}
                value={comment}
                onChange={(event) => setComment(event.target.value)}
              />
            </>
          )}

          {openAction === 'ASSIGN' && (
            <>
              <p className="field-help">
                Laisser tout vide pour prendre la demande en charge soi-même, renseigner un agent précis, ou une
                équipe (avec ou sans affectation automatique au membre le moins chargé).
              </p>
              {!autoAssign && (
                <>
                  <label htmlFor="assigned-user-id">Identifiant de l'agent</label>
                  <input
                    id="assigned-user-id"
                    type="number"
                    value={assignedUserId}
                    onChange={(event) => setAssignedUserId(event.target.value)}
                  />
                </>
              )}
              <label htmlFor="assigned-team-id">Identifiant de l'équipe</label>
              <input
                id="assigned-team-id"
                type="number"
                value={assignedTeamId}
                onChange={(event) => setAssignedTeamId(event.target.value)}
              />
              <label className="checkbox-label">
                <input
                  type="checkbox"
                  checked={autoAssign}
                  onChange={(event) => setAutoAssign(event.target.checked)}
                />
                Affectation automatique au membre le moins chargé de l'équipe
              </label>
            </>
          )}

          <div className="action-form-buttons">
            <button type="submit" disabled={submitting}>
              Confirmer
            </button>
            <button type="button" onClick={resetForm} disabled={submitting}>
              Annuler
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
