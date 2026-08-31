import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import * as adminApi from '../api/admin';
import type { EmailTemplateResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { notificationTypeLabel } from '../lib/format';

/** §6.8/§6.10 - "Gestion... des modèles d'e-mail", un par type de notification (§6.8 -
 * "soumission, affectation, demande de complément, décision, retard et clôture"),
 * catalogue fermé - jamais un code arbitraire. */
export function AdminEmailTemplatesPage() {
  const [templates, setTemplates] = useState<EmailTemplateResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const result = await adminApi.listEmailTemplates();
        if (!cancelled) {
          setTemplates(result);
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
  }, []);

  function handleUpdated(updated: EmailTemplateResponse) {
    setTemplates((current) => current.map((template) => (template.code === updated.code ? updated : template)));
  }

  return (
    <section>
      <h1>Modèles d'e-mail</h1>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && !error && (
        <ul className="parameter-list">
          {templates.map((template) => (
            <EmailTemplateRow key={template.code} template={template} onUpdated={handleUpdated} />
          ))}
        </ul>
      )}
    </section>
  );
}

function EmailTemplateRow({
  template,
  onUpdated,
}: {
  template: EmailTemplateResponse;
  onUpdated: (updated: EmailTemplateResponse) => void;
}) {
  const [subject, setSubject] = useState(template.subject ?? '');
  const [bodyHtml, setBodyHtml] = useState(template.bodyHtml ?? '');
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const updated = await adminApi.updateEmailTemplate(template.code, { subject, bodyHtml });
      onUpdated(updated);
    } catch (submitError) {
      setError(submitError);
    } finally {
      setSaving(false);
    }
  }

  const dirty = subject !== (template.subject ?? '') || bodyHtml !== (template.bodyHtml ?? '');

  return (
    <li className="parameter-item">
      <div className="parameter-meta">
        <strong>{notificationTypeLabel(template.code)}</strong>{' '}
        <span className={template.configured ? 'parameter-badge overridden' : 'parameter-badge'}>
          {template.configured ? 'Configuré' : 'Non configuré'}
        </span>
      </div>

      <form className="comment-form" onSubmit={handleSubmit}>
        <label htmlFor={`subject-${template.code}`}>Sujet</label>
        <input id={`subject-${template.code}`} value={subject} onChange={(event) => setSubject(event.target.value)} />

        <label htmlFor={`body-${template.code}`}>Contenu (HTML)</label>
        <textarea id={`body-${template.code}`} value={bodyHtml} onChange={(event) => setBodyHtml(event.target.value)} rows={4} />

        <button type="submit" disabled={saving || !dirty || !subject.trim() || !bodyHtml.trim()}>
          Enregistrer
        </button>
      </form>

      <ErrorBanner error={error} />
    </li>
  );
}
