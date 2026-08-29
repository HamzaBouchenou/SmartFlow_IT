import { useEffect, useRef, useState } from 'react';
import type { FormEvent } from 'react';
import * as attachmentsApi from '../api/attachments';
import type { AttachmentResponse } from '../api/types';
import { ErrorBanner } from './ErrorBanner';
import { formatBytes, formatDate } from '../lib/format';

// RG-09/§13 - le téléchargement est un lien direct vers la route authentifiée du
// back-end (attachmentDownloadPath), jamais une URL de fichier construite ou devinée ici :
// storedFilename/storagePath ne quittent jamais AttachmentResponse.

interface AttachmentListProps {
  requestId: number;
}

export function AttachmentList({ requestId }: AttachmentListProps) {
  const [attachments, setAttachments] = useState<AttachmentResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [uploading, setUploading] = useState(false);
  const fileInputRef = useRef<HTMLInputElement>(null);

  useEffect(() => {
    void load();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [requestId]);

  async function load() {
    setLoading(true);
    try {
      const result = await attachmentsApi.listAttachments(requestId);
      setAttachments(result);
      setError(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }

  async function handleUpload(event: FormEvent) {
    event.preventDefault();
    const file = fileInputRef.current?.files?.[0];
    if (!file) {
      return;
    }
    setUploading(true);
    setError(null);
    try {
      await attachmentsApi.uploadAttachment(requestId, file);
      if (fileInputRef.current) {
        fileInputRef.current.value = '';
      }
      await load();
    } catch (uploadError) {
      setError(uploadError);
    } finally {
      setUploading(false);
    }
  }

  return (
    <div className="attachment-list">
      <h2>Pièces jointes</h2>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      <ul>
        {attachments.map((attachment) => (
          <li key={attachment.id} className="attachment-item">
            <a href={attachmentsApi.attachmentDownloadPath(requestId, attachment.id)}>{attachment.originalFilename}</a>
            <span className="attachment-meta">
              {' '}
              · {formatBytes(attachment.sizeBytes)} · {attachment.uploadedByName} · {formatDate(attachment.uploadedAt)}
            </span>
          </li>
        ))}
        {!loading && attachments.length === 0 && <li className="attachment-empty">Aucune pièce jointe.</li>}
      </ul>

      <form className="attachment-form" onSubmit={handleUpload}>
        <label htmlFor="attachment-file">Ajouter une pièce jointe</label>
        {/* Pas de `required` : jsdom ne valide pas de façon fiable un input file
            programmatiquement rempli (userEvent.upload), et handleUpload garde déjà
            l'absence de fichier ci-dessus - la même garantie sans dépendre de la
            validation HTML5 native du navigateur. */}
        <input id="attachment-file" type="file" ref={fileInputRef} />
        <button type="submit" disabled={uploading}>
          Déposer
        </button>
      </form>
    </div>
  );
}
