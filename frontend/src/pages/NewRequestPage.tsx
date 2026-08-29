import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import * as catalogApi from '../api/catalog';
import * as requestsApi from '../api/requests';
import type { FormDefinitionResponse, RequestTypeResponse } from '../api/types';
import { DynamicFormField } from '../components/DynamicFormField';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.4 - création, sauvegarde en brouillon puis soumission d'une demande, à partir du
 * formulaire configuré pour ce type (§6.3). Les valeurs saisies restent en mémoire tant
 * que le brouillon n'existe pas côté serveur ; le premier "Enregistrer le brouillon" crée
 * la ligne Request (createDraft), les suivants la mettent à jour (updateDraft) - la même
 * distinction que RequestService.createDraft/updateDraft côté back-end. */
export function NewRequestPage() {
  const { requestTypeId } = useParams<{ requestTypeId: string }>();
  const navigate = useNavigate();

  const [requestType, setRequestType] = useState<RequestTypeResponse | null>(null);
  const [form, setForm] = useState<FormDefinitionResponse | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [values, setValues] = useState<Record<string, string>>({});
  const [draftId, setDraftId] = useState<number | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!requestTypeId) {
      return;
    }
    let cancelled = false;
    Promise.all([catalogApi.getRequestType(Number(requestTypeId)), catalogApi.getRequestTypeForm(Number(requestTypeId))])
      .then(([typeResult, formResult]) => {
        if (!cancelled) {
          setRequestType(typeResult);
          setForm(formResult);
        }
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError);
        }
      })
      .finally(() => {
        if (!cancelled) {
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [requestTypeId]);

  function isVisible(fieldCode: string | null, expectedValue: string | null): boolean {
    if (!fieldCode) {
      return true;
    }
    return values[fieldCode] === expectedValue;
  }

  function handleFieldChange(code: string, value: string) {
    setValues((current) => ({ ...current, [code]: value }));
  }

  async function saveDraft(): Promise<number> {
    if (draftId) {
      await requestsApi.updateDraft(draftId, { title, description, fieldValues: values });
      return draftId;
    }
    const created = await requestsApi.createDraft({
      requestTypeId: Number(requestTypeId),
      title,
      description,
      fieldValues: values,
    });
    setDraftId(created.id);
    return created.id;
  }

  async function handleSaveDraft(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSaving(true);
    try {
      await saveDraft();
    } catch (saveError) {
      setError(saveError);
    } finally {
      setSaving(false);
    }
  }

  async function handleSubmit() {
    setError(null);
    setSaving(true);
    try {
      const id = await saveDraft();
      await requestsApi.submitRequest(id);
      navigate(`/demandes/${id}`);
    } catch (submitError) {
      setError(submitError);
    } finally {
      setSaving(false);
    }
  }

  if (loading) {
    return <p className="page-loading">Chargement…</p>;
  }
  if (!form || !requestType) {
    return <ErrorBanner error={error} />;
  }

  return (
    <section>
      <h1>{requestType.name}</h1>
      <p>{requestType.description}</p>

      <form className="request-form" onSubmit={handleSaveDraft}>
        <div className="form-field">
          <label htmlFor="request-title">
            Titre <span className="required-mark">*</span>
          </label>
          <input
            id="request-title"
            type="text"
            required
            value={title}
            onChange={(event) => setTitle(event.target.value)}
          />
        </div>

        <div className="form-field">
          <label htmlFor="request-description">Description</label>
          <textarea
            id="request-description"
            value={description}
            onChange={(event) => setDescription(event.target.value)}
          />
        </div>

        {form.fields
          .slice()
          .sort((a, b) => a.displayOrder - b.displayOrder)
          .filter((field) => isVisible(field.visibleWhenFieldCode, field.visibleWhenValue))
          .map((field) => (
            <DynamicFormField
              key={field.code}
              field={field}
              value={values[field.code] ?? ''}
              onChange={handleFieldChange}
            />
          ))}

        <ErrorBanner error={error} />

        <div className="request-form-buttons">
          <button type="submit" disabled={saving}>
            Enregistrer le brouillon
          </button>
          <button type="button" disabled={saving} onClick={() => void handleSubmit()}>
            Soumettre
          </button>
        </div>
        {draftId && (
          <p className="draft-hint">
            Brouillon enregistré. <Link to={`/demandes/${draftId}`}>Voir le dossier</Link> pour ajouter un commentaire
            ou une pièce jointe avant de soumettre.
          </p>
        )}
      </form>
    </section>
  );
}
