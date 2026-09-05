import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import * as adminApi from '../api/admin';
import type { SystemParameterResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.10 - "Paramètres généraux : formats acceptés, taille maximale des fichiers... et
 * seuils d'alerte", catalogue fermé (SystemParameterAdminService - un administrateur
 * fonctionnel ajuste une clé que le code consomme réellement, il n'en crée jamais une
 * nouvelle depuis cet écran). Réservé côté serveur à FUNCTIONAL_ADMIN
 * (canManageSystemParameters) - un autre rôle reçoit un 404, affiché comme n'importe
 * quelle autre erreur. */
export function AdminSystemParametersPage() {
  const [parameters, setParameters] = useState<SystemParameterResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const result = await adminApi.listSystemParameters();
        if (!cancelled) {
          setParameters(result);
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

  function handleUpdated(updated: SystemParameterResponse) {
    setParameters((current) => current.map((parameter) => (parameter.key === updated.key ? updated : parameter)));
  }

  return (
    <section>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && !error && (
        <ul className="parameter-list">
          {parameters.map((parameter) => (
            <SystemParameterRow key={parameter.key} parameter={parameter} onUpdated={handleUpdated} />
          ))}
        </ul>
      )}
    </section>
  );
}

function SystemParameterRow({
  parameter,
  onUpdated,
}: {
  parameter: SystemParameterResponse;
  onUpdated: (updated: SystemParameterResponse) => void;
}) {
  // Initialisé une fois depuis parameter.value ; après un enregistrement réussi,
  // onUpdated propage exactement la valeur qui vient d'être soumise, donc draft et
  // parameter.value restent déjà cohérents sans effet de synchronisation supplémentaire.
  const [draft, setDraft] = useState(parameter.value);
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const updated = await adminApi.updateSystemParameter(parameter.key, { value: draft });
      onUpdated(updated);
    } catch (submitError) {
      setError(submitError);
    } finally {
      setSaving(false);
    }
  }

  const dirty = draft !== parameter.value;

  return (
    <li className="parameter-item">
      <div className="parameter-meta">
        <strong>{parameter.label}</strong>{' '}
        <span className={parameter.overridden ? 'parameter-badge overridden' : 'parameter-badge'}>
          {parameter.overridden ? 'Personnalisée' : 'Valeur par défaut'}
        </span>
      </div>
      <p className="parameter-description">{parameter.description}</p>

      <form className="parameter-form" onSubmit={handleSubmit}>
        {parameter.type === 'BOOLEAN' ? (
          <select value={draft} onChange={(event) => setDraft(event.target.value)}>
            <option value="true">Activé</option>
            <option value="false">Désactivé</option>
          </select>
        ) : (
          <input
            type={parameter.type === 'INTEGER' || parameter.type === 'LONG' ? 'number' : 'text'}
            value={draft}
            onChange={(event) => setDraft(event.target.value)}
          />
        )}
        <button type="submit" disabled={saving || !dirty || draft.trim() === ''}>
          Enregistrer
        </button>
      </form>

      <ErrorBanner error={error} />
    </li>
  );
}
