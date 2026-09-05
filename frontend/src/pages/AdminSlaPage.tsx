import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import * as adminApi from '../api/admin';
import * as catalogApi from '../api/catalog';
import type { Priority, RequestTypeResponse, ServiceCatalogResponse, SlaResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatMinutes } from '../lib/format';

const PRIORITIES: Priority[] = ['LOW', 'MEDIUM', 'HIGH', 'CRITICAL'];

/** §6.7/§6.10 - "Définition d'un délai de prise en charge et d'un délai de résolution par
 * type de demande et priorité", une cible par (type de demande, priorité). */
export function AdminSlaPage() {
  const [services, setServices] = useState<ServiceCatalogResponse[]>([]);
  const [serviceId, setServiceId] = useState<number | null>(null);
  const [requestTypes, setRequestTypes] = useState<RequestTypeResponse[]>([]);
  const [requestTypeId, setRequestTypeId] = useState<number | null>(null);
  const [targets, setTargets] = useState<SlaResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    catalogApi
      .searchServices()
      .then((result) => {
        if (!cancelled) {
          setServices(result);
          if (result.length > 0) {
            setServiceId(result[0].id);
          } else {
            setLoading(false);
          }
        }
      })
      .catch((loadError) => {
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
    catalogApi
      .listRequestTypes(serviceId)
      .then((result) => {
        if (!cancelled) {
          setRequestTypes(result);
          setRequestTypeId(result.length > 0 ? result[0].id : null);
          if (result.length === 0) {
            setLoading(false);
          }
        }
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError);
          setLoading(false);
        }
      });
    return () => {
      cancelled = true;
    };
  }, [serviceId]);

  useEffect(() => {
    if (requestTypeId === null) {
      return;
    }
    let cancelled = false;
    async function load(id: number) {
      setLoading(true);
      try {
        const result = await adminApi.listSla(id);
        if (!cancelled) {
          setTargets(result);
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
    void load(requestTypeId);
    return () => {
      cancelled = true;
    };
  }, [requestTypeId]);

  function handleUpserted(updated: SlaResponse) {
    setTargets((current) => {
      const exists = current.some((target) => target.priority === updated.priority);
      return exists ? current.map((target) => (target.priority === updated.priority ? updated : target)) : [...current, updated];
    });
  }

  return (
    <section>

      <div className="task-filters">
        <select
          value={serviceId ?? ''}
          onChange={(event) => {
            setServiceId(event.target.value ? Number(event.target.value) : null);
          }}
        >
          {services.map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </select>
        <select
          value={requestTypeId ?? ''}
          onChange={(event) => {
            setRequestTypeId(event.target.value ? Number(event.target.value) : null);
          }}
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
        <ul className="parameter-list">
          {PRIORITIES.map((priority) => (
            <SlaRow
              key={priority}
              requestTypeId={requestTypeId}
              priority={priority}
              current={targets.find((target) => target.priority === priority) ?? null}
              onUpserted={handleUpserted}
            />
          ))}
        </ul>
      )}
    </section>
  );
}

function SlaRow({
  requestTypeId,
  priority,
  current,
  onUpserted,
}: {
  requestTypeId: number;
  priority: Priority;
  current: SlaResponse | null;
  onUpserted: (updated: SlaResponse) => void;
}) {
  const [firstResponseMinutes, setFirstResponseMinutes] = useState(String(current?.firstResponseMinutes ?? ''));
  const [resolutionMinutes, setResolutionMinutes] = useState(String(current?.resolutionMinutes ?? ''));
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setSaving(true);
    setError(null);
    try {
      const updated = await adminApi.upsertSla(requestTypeId, priority, {
        firstResponseMinutes: Number(firstResponseMinutes),
        resolutionMinutes: Number(resolutionMinutes),
      });
      onUpserted(updated);
    } catch (submitError) {
      setError(submitError);
    } finally {
      setSaving(false);
    }
  }

  return (
    <li className="parameter-item">
      <div className="parameter-meta">
        <strong>{priority}</strong>{' '}
        <span className={current ? 'parameter-badge overridden' : 'parameter-badge'}>
          {current ? 'Configurée' : 'Non configurée'}
        </span>
        {current && (
          <span className="parameter-description">
            {' '}
            (actuellement {formatMinutes(current.firstResponseMinutes)} / {formatMinutes(current.resolutionMinutes)})
          </span>
        )}
      </div>

      <form className="parameter-form" onSubmit={handleSubmit}>
        <label>
          Prise en charge (min)
          <input
            type="number"
            min={1}
            value={firstResponseMinutes}
            onChange={(event) => setFirstResponseMinutes(event.target.value)}
          />
        </label>
        <label>
          Résolution (min)
          <input type="number" min={1} value={resolutionMinutes} onChange={(event) => setResolutionMinutes(event.target.value)} />
        </label>
        <button type="submit" disabled={saving || !firstResponseMinutes || !resolutionMinutes}>
          Enregistrer
        </button>
      </form>

      <ErrorBanner error={error} />
    </li>
  );
}
