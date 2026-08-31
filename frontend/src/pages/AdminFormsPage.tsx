import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import * as adminApi from '../api/admin';
import * as catalogApi from '../api/catalog';
import type {
  FieldOptionAdminResponse,
  FieldType,
  FormDefinitionAdminResponse,
  RequestTypeResponse,
  ServiceCatalogResponse,
} from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate } from '../lib/format';

const FIELD_TYPES: FieldType[] = ['TEXT', 'NUMBER', 'DATE', 'LIST', 'CHECKBOX', 'USER', 'DEPARTMENT', 'FILE'];

/** §6.3/§10.1/§6.10 - administration versionnée des formulaires (ADR-17, docs/DECISIONS.md).
 * Un DRAFT à la fois par type de demande ; publier archive l'ancien PUBLISHED (RG-12) -
 * une version PUBLISHED/ARCHIVED n'est plus jamais éditable ici. */
export function AdminFormsPage() {
  const [services, setServices] = useState<ServiceCatalogResponse[]>([]);
  const [serviceId, setServiceId] = useState<number | null>(null);
  const [requestTypes, setRequestTypes] = useState<RequestTypeResponse[]>([]);
  const [requestTypeId, setRequestTypeId] = useState<number | null>(null);
  const [versions, setVersions] = useState<FormDefinitionAdminResponse[]>([]);
  const [selectedVersionId, setSelectedVersionId] = useState<number | null>(null);
  const [detail, setDetail] = useState<FormDefinitionAdminResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    catalogApi.searchServices().then((result) => {
      if (!cancelled) {
        setServices(result);
        if (result.length > 0) {
          setServiceId(result[0].id);
        } else {
          setLoading(false);
        }
      }
    }).catch((loadError) => {
      if (!cancelled) {
        setError(loadError);
        setLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, []);

  useEffect(() => {
    if (serviceId === null) {
      return;
    }
    let cancelled = false;
    catalogApi.listRequestTypes(serviceId).then((result) => {
      if (!cancelled) {
        setRequestTypes(result);
        setRequestTypeId(result.length > 0 ? result[0].id : null);
        if (result.length === 0) {
          setLoading(false);
        }
      }
    }).catch((loadError) => {
      if (!cancelled) {
        setError(loadError);
        setLoading(false);
      }
    });
    return () => {
      cancelled = true;
    };
  }, [serviceId]);

  async function loadVersions(typeId: number) {
    setLoading(true);
    try {
      const result = await adminApi.listFormVersions(typeId);
      setVersions(result);
      setError(null);
      setSelectedVersionId(null);
      setDetail(null);
    } catch (loadError) {
      setError(loadError);
    } finally {
      setLoading(false);
    }
  }

  useEffect(() => {
    if (requestTypeId === null) {
      return;
    }
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const result = await adminApi.listFormVersions(requestTypeId as number);
        if (!cancelled) {
          setVersions(result);
          setError(null);
          setSelectedVersionId(null);
          setDetail(null);
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
  }, [requestTypeId]);

  async function selectVersion(id: number) {
    setSelectedVersionId(id);
    setError(null);
    try {
      setDetail(await adminApi.getFormVersion(id));
    } catch (loadError) {
      setError(loadError);
    }
  }

  async function handleCreateDraft() {
    if (requestTypeId === null) {
      return;
    }
    setError(null);
    try {
      const draft = await adminApi.createFormDraft(requestTypeId);
      await loadVersions(requestTypeId);
      await selectVersion(draft.id);
    } catch (createError) {
      setError(createError);
    }
  }

  async function handlePublish(id: number) {
    setError(null);
    try {
      await adminApi.publishFormVersion(id);
      if (requestTypeId !== null) {
        await loadVersions(requestTypeId);
      }
    } catch (publishError) {
      setError(publishError);
    }
  }

  async function handleDeleteDraft(id: number) {
    setError(null);
    try {
      await adminApi.deleteFormDraft(id);
      if (requestTypeId !== null) {
        await loadVersions(requestTypeId);
      }
    } catch (deleteError) {
      setError(deleteError);
    }
  }

  return (
    <section>
      <h1>Formulaires</h1>

      <div className="task-filters">
        <select value={serviceId ?? ''} onChange={(event) => setServiceId(event.target.value ? Number(event.target.value) : null)}>
          {services.map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </select>
        <select
          value={requestTypeId ?? ''}
          onChange={(event) => setRequestTypeId(event.target.value ? Number(event.target.value) : null)}
        >
          {requestTypes.map((requestType) => (
            <option key={requestType.id} value={requestType.id}>
              {requestType.name}
            </option>
          ))}
        </select>
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && !error && requestTypeId !== null && (
        <>
          <table className="task-table">
            <thead>
              <tr>
                <th>Version</th>
                <th>Statut</th>
                <th>Publiée le</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {versions.map((version) => (
                <tr key={version.id}>
                  <td>
                    <button type="button" className="link-button" onClick={() => void selectVersion(version.id)}>
                      v{version.version}
                    </button>
                  </td>
                  <td>{version.status}</td>
                  <td>{formatDate(version.publishedAt)}</td>
                  <td>
                    {version.status === 'DRAFT' && (
                      <>
                        <button type="button" onClick={() => void handlePublish(version.id)}>
                          Publier
                        </button>{' '}
                        <button type="button" onClick={() => void handleDeleteDraft(version.id)}>
                          Supprimer
                        </button>
                      </>
                    )}
                  </td>
                </tr>
              ))}
              {versions.length === 0 && (
                <tr>
                  <td colSpan={4}>Aucune version pour ce type de demande.</td>
                </tr>
              )}
            </tbody>
          </table>

          {!versions.some((version) => version.status === 'DRAFT') && (
            <button type="button" onClick={() => void handleCreateDraft()}>
              Créer un brouillon
            </button>
          )}

          {detail && selectedVersionId === detail.id && (
            <FormFieldsEditor
              form={detail}
              onChanged={(updated) => {
                setDetail(updated);
              }}
            />
          )}
        </>
      )}
    </section>
  );
}

function FormFieldsEditor({
  form,
  onChanged,
}: {
  form: FormDefinitionAdminResponse;
  onChanged: (updated: FormDefinitionAdminResponse) => void;
}) {
  const editable = form.status === 'DRAFT';
  const [error, setError] = useState<unknown>(null);

  async function refresh() {
    onChanged(await adminApi.getFormVersion(form.id));
  }

  async function handleDeleteField(fieldId: number) {
    setError(null);
    try {
      await adminApi.deleteFormField(fieldId);
      await refresh();
    } catch (deleteError) {
      setError(deleteError);
    }
  }

  return (
    <div className="parameter-item">
      <h2>
        Champs (v{form.version} - {form.status})
      </h2>
      <ErrorBanner error={error} />

      <ul className="parameter-list">
        {(form.fields ?? []).map((field) => (
          <li key={field.id} className="parameter-item">
            <div className="parameter-meta">
              <strong>{field.label}</strong> <span className="parameter-badge">{field.code}</span>{' '}
              <span className="parameter-badge">{field.fieldType}</span>
              {field.required && <span className="parameter-badge overridden">Obligatoire</span>}
            </div>
            {field.fieldType === 'LIST' && editable && (
              <FieldOptionsEditor fieldId={field.id} options={field.options} onChanged={refresh} />
            )}
            {editable && (
              <button type="button" onClick={() => void handleDeleteField(field.id)}>
                Supprimer le champ
              </button>
            )}
          </li>
        ))}
        {(form.fields ?? []).length === 0 && <li className="timeline-empty">Aucun champ pour l'instant.</li>}
      </ul>

      {editable && <AddFieldForm formDefinitionId={form.id} nextOrder={(form.fields ?? []).length + 1} onAdded={refresh} />}
    </div>
  );
}

function AddFieldForm({
  formDefinitionId,
  nextOrder,
  onAdded,
}: {
  formDefinitionId: number;
  nextOrder: number;
  onAdded: () => void;
}) {
  const [code, setCode] = useState('');
  const [label, setLabel] = useState('');
  const [fieldType, setFieldType] = useState<FieldType>('TEXT');
  const [required, setRequired] = useState(false);
  const [error, setError] = useState<unknown>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await adminApi.addFormField(formDefinitionId, { code, label, fieldType, required, displayOrder: nextOrder });
      setCode('');
      setLabel('');
      setRequired(false);
      onAdded();
    } catch (submitError) {
      setError(submitError);
    }
  }

  return (
    <form className="parameter-form" onSubmit={handleSubmit}>
      <input placeholder="Code" value={code} onChange={(event) => setCode(event.target.value)} />
      <input placeholder="Libellé" value={label} onChange={(event) => setLabel(event.target.value)} />
      <select value={fieldType} onChange={(event) => setFieldType(event.target.value as FieldType)}>
        {FIELD_TYPES.map((type) => (
          <option key={type} value={type}>
            {type}
          </option>
        ))}
      </select>
      <label>
        <input type="checkbox" checked={required} onChange={(event) => setRequired(event.target.checked)} />
        Obligatoire
      </label>
      <button type="submit" disabled={!code.trim() || !label.trim()}>
        Ajouter un champ
      </button>
      <ErrorBanner error={error} />
    </form>
  );
}

function FieldOptionsEditor({
  fieldId,
  options,
  onChanged,
}: {
  fieldId: number;
  options: FieldOptionAdminResponse[];
  onChanged: () => Promise<void>;
}) {
  const [value, setValue] = useState('');
  const [label, setLabel] = useState('');
  const [error, setError] = useState<unknown>(null);

  async function handleAdd(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      await adminApi.addFieldOption(fieldId, { value, label, displayOrder: options.length + 1 });
      setValue('');
      setLabel('');
      await onChanged();
    } catch (addError) {
      setError(addError);
    }
  }

  async function handleDelete(optionId: number) {
    setError(null);
    try {
      await adminApi.deleteFieldOption(optionId);
      await onChanged();
    } catch (deleteError) {
      setError(deleteError);
    }
  }

  return (
    <div>
      <ErrorBanner error={error} />
      <ul className="parameter-list">
        {options.map((option) => (
          <li key={option.id}>
            {option.label} ({option.value}){' '}
            <button type="button" onClick={() => void handleDelete(option.id)}>
              Retirer
            </button>
          </li>
        ))}
      </ul>
      <form className="parameter-form" onSubmit={handleAdd}>
        <input placeholder="Valeur" value={value} onChange={(event) => setValue(event.target.value)} />
        <input placeholder="Libellé" value={label} onChange={(event) => setLabel(event.target.value)} />
        <button type="submit" disabled={!value.trim() || !label.trim()}>
          Ajouter une valeur
        </button>
      </form>
    </div>
  );
}
