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
 * distinction que RequestService.createDraft/updateDraft côté back-end.
 *
 * Maquette 04. Le panneau "Étapes du circuit" de la maquette n'est pas repris : aucun
 * endpoint accessible à un demandeur n'expose le graphe d'un workflow (`/request-types/{id}`
 * ne porte que la fiche, `/{id}/form` que les champs ; le graphe ne vit que derrière
 * l'API d'administration). Le dessiner aurait voulu dire inventer des étapes. */
export function NewRequestPage() {
  const { requestTypeId } = useParams<{ requestTypeId: string }>();
  const navigate = useNavigate();

  const [requestType, setRequestType] = useState<RequestTypeResponse | null>(null);
  const [form, setForm] = useState<FormDefinitionResponse | null>(null);
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [values, setValues] = useState<Record<string, string>>({});
  const [draftId, setDraftId] = useState<number | null>(null);
  const [savedAt, setSavedAt] = useState<Date | null>(null);
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
      setSavedAt(new Date());
      return draftId;
    }
    const created = await requestsApi.createDraft({
      requestTypeId: Number(requestTypeId),
      title,
      description,
      fieldValues: values,
    });
    setDraftId(created.id);
    setSavedAt(new Date());
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

  const visibleFields = form.fields
    .slice()
    .sort((a, b) => a.displayOrder - b.displayOrder)
    .filter((field) => isVisible(field.visibleWhenFieldCode, field.visibleWhenValue));

  return (
    <section>
      <nav className="breadcrumb">
        <Link to={`/catalogue/${requestType.serviceCatalogId}`}>← Retour aux types de demande</Link>
      </nav>

      <div className="form-layout">
        <form className="request-form" onSubmit={handleSaveDraft}>
          <header className="panel-head">
            <h2>{requestType.name}</h2>
            {savedAt && (
              <span className="panel-badge">
                Brouillon enregistré {savedAt.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' })}
              </span>
            )}
          </header>

          <div className="form-field">
            <label htmlFor="request-title">
              Objet de la demande <span className="required-mark">*</span>
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

          {/* Les champs configurés se répartissent sur deux colonnes (maquette 04) ; un
              champ conditionnel reprend toute la largeur, son liseré devant rester lisible
              comme un bloc à part entière. */}
          <div className="form-grid">
            {visibleFields.map((field) => (
              <DynamicFormField
                key={field.code}
                field={field}
                value={values[field.code] ?? ''}
                onChange={handleFieldChange}
                conditional={Boolean(field.visibleWhenFieldCode)}
              />
            ))}
          </div>

          <ErrorBanner error={error} />

          <div className="request-form-buttons">
            <button type="button" disabled={saving} onClick={() => void handleSubmit()}>
              Soumettre
            </button>
            {/* `type="submit"` porte volontairement l'enregistrement du brouillon, et non la
                soumission : la touche Entrée dans un champ déclenche alors l'action
                réversible, jamais celle qui engage la demande (RG-03 - le workflow est figé
                à la soumission). */}
            <button type="submit" className="button-secondary" disabled={saving}>
              Enregistrer le brouillon
            </button>
            <button
              type="button"
              className="button-secondary"
              disabled={saving}
              onClick={() => navigate(`/catalogue/${requestType.serviceCatalogId}`)}
            >
              Annuler
            </button>
          </div>

          {draftId && (
            <p className="draft-hint">
              Brouillon enregistré. <Link to={`/demandes/${draftId}`}>Voir le dossier</Link> pour ajouter un commentaire
              ou une pièce jointe avant de soumettre.
            </p>
          )}
        </form>

        <aside className="form-aside">
          <section className="panel">
            <header className="panel-head">
              <h2>Ce que vous devez savoir</h2>
            </header>
            <dl className="aside-meta">
              <dt>Délai cible</dt>
              <dd>{requestType.targetDelayDescription || '—'}</dd>
              <dt>Pièces requises</dt>
              <dd>{requestType.requiredDocuments || 'Aucune'}</dd>
              <dt>Contact</dt>
              <dd>{requestType.contactInfo || '—'}</dd>
            </dl>
            <p className="aside-note">{requestType.description}</p>
          </section>
        </aside>
      </div>
    </section>
  );
}
