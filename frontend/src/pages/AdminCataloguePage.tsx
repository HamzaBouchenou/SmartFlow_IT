import { useEffect, useState } from 'react';
import type { FormEvent } from 'react';
import * as adminApi from '../api/admin';
import type { DepartmentResponse, RequestTypeAdminResponse, ServiceCatalogAdminResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.2/§6.10 - fiches de catalogue et types de demande (ADR-17 - non versionnés,
 * contrairement aux formulaires/workflows qu'ils portent). RG-02/RG-12 - jamais de
 * suppression physique, seulement activate/deactivate. */
export function AdminCataloguePage() {
  const [departments, setDepartments] = useState<DepartmentResponse[]>([]);
  const [services, setServices] = useState<ServiceCatalogAdminResponse[]>([]);
  const [selectedServiceId, setSelectedServiceId] = useState<number | null>(null);
  const [requestTypes, setRequestTypes] = useState<RequestTypeAdminResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const [departmentList, serviceList] = await Promise.all([adminApi.listDepartments(), adminApi.listServicesAdmin()]);
        if (!cancelled) {
          setDepartments(departmentList);
          setServices(serviceList);
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

  useEffect(() => {
    let cancelled = false;
    function load() {
      if (selectedServiceId === null) {
        setRequestTypes([]);
        return;
      }
      adminApi
        .listRequestTypesAdmin(selectedServiceId)
        .then((result) => {
          if (!cancelled) {
            setRequestTypes(result);
          }
        })
        .catch((loadError) => {
          if (!cancelled) {
            setError(loadError);
          }
        });
    }
    load();
    return () => {
      cancelled = true;
    };
  }, [selectedServiceId]);

  function upsertService(updated: ServiceCatalogAdminResponse) {
    setServices((current) => {
      const exists = current.some((service) => service.id === updated.id);
      return exists ? current.map((service) => (service.id === updated.id ? updated : service)) : [...current, updated];
    });
  }

  function upsertRequestType(updated: RequestTypeAdminResponse) {
    setRequestTypes((current) => {
      const exists = current.some((rt) => rt.id === updated.id);
      return exists ? current.map((rt) => (rt.id === updated.id ? updated : rt)) : [...current, updated];
    });
  }

  return (
    <section>
      <h1>Catalogue</h1>
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {!loading && !error && (
        <>
          <h2>Services</h2>
          <table className="task-table">
            <thead>
              <tr>
                <th>Nom</th>
                <th>Département</th>
                <th>Statut</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {services.map((service) => (
                <tr key={service.id} className={service.id === selectedServiceId ? 'status-submitted' : undefined}>
                  <td>
                    <button type="button" className="link-button" onClick={() => setSelectedServiceId(service.id)}>
                      {service.name}
                    </button>
                  </td>
                  <td>{service.departmentName}</td>
                  <td>{service.active ? 'Actif' : 'Désactivé'}</td>
                  <td>
                    <ToggleServiceButton service={service} onChanged={upsertService} />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <CreateServiceForm departments={departments} onCreated={upsertService} />

          {selectedServiceId !== null && (
            <>
              <h2>Types de demande</h2>
              <table className="task-table">
                <thead>
                  <tr>
                    <th>Nom</th>
                    <th>Réouverture (RG-08)</th>
                    <th>Statut</th>
                    <th></th>
                  </tr>
                </thead>
                <tbody>
                  {requestTypes.map((requestType) => (
                    <tr key={requestType.id}>
                      <td>{requestType.name}</td>
                      <td>{requestType.reopenAllowed ? 'Autorisée' : 'Non autorisée'}</td>
                      <td>{requestType.active ? 'Actif' : 'Désactivé'}</td>
                      <td>
                        <ToggleRequestTypeButton requestType={requestType} onChanged={upsertRequestType} />
                      </td>
                    </tr>
                  ))}
                  {requestTypes.length === 0 && (
                    <tr>
                      <td colSpan={4}>Aucun type de demande pour ce service.</td>
                    </tr>
                  )}
                </tbody>
              </table>
              <CreateRequestTypeForm serviceCatalogId={selectedServiceId} onCreated={upsertRequestType} />
            </>
          )}
        </>
      )}
    </section>
  );
}

function ToggleServiceButton({
  service,
  onChanged,
}: {
  service: ServiceCatalogAdminResponse;
  onChanged: (updated: ServiceCatalogAdminResponse) => void;
}) {
  async function toggle() {
    const updated = service.active
      ? await adminApi.deactivateServiceAdmin(service.id)
      : await adminApi.activateServiceAdmin(service.id);
    onChanged(updated);
  }
  return (
    <button type="button" onClick={() => void toggle()}>
      {service.active ? 'Désactiver' : 'Activer'}
    </button>
  );
}

function ToggleRequestTypeButton({
  requestType,
  onChanged,
}: {
  requestType: RequestTypeAdminResponse;
  onChanged: (updated: RequestTypeAdminResponse) => void;
}) {
  async function toggle() {
    const updated = requestType.active
      ? await adminApi.deactivateRequestTypeAdmin(requestType.id)
      : await adminApi.activateRequestTypeAdmin(requestType.id);
    onChanged(updated);
  }
  return (
    <button type="button" onClick={() => void toggle()}>
      {requestType.active ? 'Désactiver' : 'Activer'}
    </button>
  );
}

function CreateServiceForm({
  departments,
  onCreated,
}: {
  departments: DepartmentResponse[];
  onCreated: (created: ServiceCatalogAdminResponse) => void;
}) {
  const [name, setName] = useState('');
  const [departmentId, setDepartmentId] = useState('');
  const [error, setError] = useState<unknown>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!departmentId) {
      return;
    }
    setError(null);
    try {
      const created = await adminApi.createServiceAdmin({ name, departmentId: Number(departmentId), displayOrder: 1 });
      onCreated(created);
      setName('');
    } catch (submitError) {
      setError(submitError);
    }
  }

  return (
    <form className="parameter-form" onSubmit={handleSubmit}>
      <input placeholder="Nom du service" value={name} onChange={(event) => setName(event.target.value)} />
      <select value={departmentId} onChange={(event) => setDepartmentId(event.target.value)}>
        <option value="">— Département —</option>
        {departments.map((department) => (
          <option key={department.id} value={department.id}>
            {department.name}
          </option>
        ))}
      </select>
      <button type="submit" disabled={!name.trim() || !departmentId}>
        Ajouter un service
      </button>
      <ErrorBanner error={error} />
    </form>
  );
}

function CreateRequestTypeForm({
  serviceCatalogId,
  onCreated,
}: {
  serviceCatalogId: number;
  onCreated: (created: RequestTypeAdminResponse) => void;
}) {
  const [name, setName] = useState('');
  const [reopenAllowed, setReopenAllowed] = useState(true);
  const [error, setError] = useState<unknown>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    try {
      const created = await adminApi.createRequestTypeAdmin({
        serviceCatalogId,
        name,
        reopenAllowed,
        displayOrder: 1,
      });
      onCreated(created);
      setName('');
    } catch (submitError) {
      setError(submitError);
    }
  }

  return (
    <form className="parameter-form" onSubmit={handleSubmit}>
      <input placeholder="Nom du type de demande" value={name} onChange={(event) => setName(event.target.value)} />
      <label>
        <input type="checkbox" checked={reopenAllowed} onChange={(event) => setReopenAllowed(event.target.checked)} />
        Réouverture autorisée (RG-08)
      </label>
      <button type="submit" disabled={!name.trim()}>
        Ajouter un type de demande
      </button>
      <ErrorBanner error={error} />
    </form>
  );
}
