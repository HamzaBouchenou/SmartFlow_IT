import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import * as commentsApi from '../api/comments';
import type { CommentResponse } from '../api/types';
import { ErrorBanner } from './ErrorBanner';
import { formatDate } from '../lib/format';

// §6.4 - "Ajout de commentaires... autorisés". Lecture/écriture suivent respectivement
// canView/canAnnotate côté serveur (ADR-11, docs/DECISIONS.md) : ce composant ne devine
// rien, il affiche toujours le formulaire et laisse une éventuelle erreur d'autorisation
// remonter via ErrorBanner, exactement comme le reste de cette application le fait déjà
// pour toute action non pilotée par availableActions[].

interface CommentThreadProps {
  requestId: number;
}

export function CommentThread({ requestId }: CommentThreadProps) {
  const [comments, setComments] = useState<CommentResponse[]>([]);
  const [body, setBody] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestId]);

  async function load() {
    setLoading(true);
    try {
      const result = await commentsApi.listComments(requestId);
      setComments(result);
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!body.trim()) {
      return;
    }
    setSubmitting(true);
    setError(null);
    try {
      await commentsApi.addComment(requestId, { body });
      setBody('');
      await load();
    } catch (submitError) {
      setError(submitError);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="comment-thread">
      <h2>Commentaires</h2>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      <ul className="comment-list">
        {comments.map((comment) => (
          <li key={comment.id} className="comment-item">
            <div className="comment-meta">
              <strong>{comment.authorName}</strong> · {formatDate(comment.createdAt)}
            </div>
            <p>{comment.body}</p>
            {comment.mentions.length > 0 && (
              <p className="comment-mentions">
                {`Mentionne\u00a0: ${comment.mentions.map((mention) => mention.name).join(', ')}`}
              </p>
            )}
          </li>
        ))}
        {!loading && comments.length === 0 && <li className="comment-empty">Aucun commentaire pour l'instant.</li>}
      </ul>

      <form className="comment-form" onSubmit={handleSubmit}>
        <label htmlFor="comment-body">Ajouter un commentaire</label>
        <textarea
          id="comment-body"
          value={body}
          onChange={(event) => setBody(event.target.value)}
          aria-describedby="comment-mention-hint"
        />
        {/* ADR-24 - le serveur résout lui-même les mentions depuis le texte enregistré ; cet
            indice rappelle la forme attendue, il ne pré-résout rien côté écran. */}
        <p id="comment-mention-hint" className="comment-hint">
          Mentionnez quelqu'un avec @ suivi de son adresse e-mail. Seules les personnes qui ont déjà accès à cette
          demande sont notifiées.
        </p>
        <button type="submit" disabled={submitting || !body.trim()}>
          Publier
        </button>
      </form>
    </div>
  );
}
