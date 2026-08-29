import { useEffect, useState } from 'react';
import * as catalogApi from '../api/catalog';
import * as dashboardsApi from '../api/dashboards';
import type { DashboardResponse, ServiceCatalogResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatMinutes, formatPercent } from '../lib/format';

/** §6.9 - "Vue responsable : volumes par statut, catégorie, agent et période" + les
 * quatre indicateurs + export CSV. Accès gouverné côté serveur par canViewDashboard
 * (DEPARTMENT/DIRECTION/GLOBAL) - un rôle sans ce périmètre reçoit un 404 (ADR-16
 * raisonnement identique), affiché ici comme n'importe quelle autre ErrorBanner. */
export function DashboardPage() {
  const [services, setServices] = useState<ServiceCatalogResponse[]>([]);
  const [serviceId, setServiceId] = useState<number | null>(null);
  const [from, setFrom] = useState('');
  const [to, setTo] = useState('');
  const [dashboard, setDashboard] = useState<DashboardResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    let cancelled = false;
    catalogApi
      .searchServices()
      .then((result) => {
        if (!cancelled) {
          setServices(result);
          if (result.length > 0) {
            setServiceId(result[0].id);
          }
        }
      })
      .catch((loadError) => {
        if (!cancelled) {
          setError(loadError);
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
    async function load(id: number) {
      setLoading(true);
      try {
        const result = await dashboardsApi.getServiceDashboard({ serviceId: id, from: toIsoStart(from), to: toIsoEnd(to) });
        if (!cancelled) {
          setDashboard(result);
          setError(null);
        }
      } catch (fetchError) {
        if (!cancelled) {
          setError(fetchError);
        }
      } finally {
        if (!cancelled) {
          setLoading(false);
        }
      }
    }
    void load(serviceId);
    return () => {
      cancelled = true;
    };
  }, [serviceId, from, to]);

  return (
    <section>
      <h1>Tableau de bord</h1>

      <div className="dashboard-filters">
        <select value={serviceId ?? ''} onChange={(event) => setServiceId(Number(event.target.value))}>
          {services.map((service) => (
            <option key={service.id} value={service.id}>
              {service.name}
            </option>
          ))}
        </select>
        <label className="dashboard-date-label">
          Du
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label className="dashboard-date-label">
          Au
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
        {serviceId !== null && (
          <a
            className="button-link"
            href={dashboardsApi.exportPath({ serviceId, from: toIsoStart(from), to: toIsoEnd(to) })}
          >
            Exporter en CSV
          </a>
        )}
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {dashboard && (
        <>
          <div className="kpi-grid">
            <KpiCard label="Délai moyen de prise en charge" value={formatMinutes(dashboard.averageFirstResponseMinutes)} />
            <KpiCard label="Délai moyen de résolution" value={formatMinutes(dashboard.averageResolutionMinutes)} />
            <KpiCard label="Taux de respect des SLA" value={formatPercent(dashboard.slaComplianceRatePercent)} />
            <KpiCard label="Taux de réouverture" value={formatPercent(dashboard.reopenRatePercent)} />
          </div>

          <div className="dashboard-breakdowns">
            <BreakdownTable title="Volumes par statut" data={dashboard.volumesByStatus} />
            <BreakdownTable title="Volumes par catégorie" data={dashboard.volumesByCategory} />
            <BreakdownTable title="Volumes par agent" data={dashboard.volumesByAgent} />
          </div>
        </>
      )}
    </section>
  );
}

function KpiCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="kpi-card">
      <span className="kpi-value">{value}</span>
      <span className="kpi-label">{label}</span>
    </div>
  );
}

function BreakdownTable({ title, data }: { title: string; data: Record<string, number> }) {
  const entries = Object.entries(data);
  return (
    <div className="breakdown-table">
      <h2>{title}</h2>
      {entries.length === 0 ? (
        <p className="page-loading">Aucune donnée sur la période.</p>
      ) : (
        <table>
          <tbody>
            {entries.map(([key, value]) => (
              <tr key={key}>
                <td>{key}</td>
                <td>{value}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </div>
  );
}

function toIsoStart(dateOnly: string): string | undefined {
  return dateOnly ? `${dateOnly}T00:00:00Z` : undefined;
}

function toIsoEnd(dateOnly: string): string | undefined {
  return dateOnly ? `${dateOnly}T23:59:59Z` : undefined;
}
