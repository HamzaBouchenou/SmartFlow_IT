import { useEffect, useState } from 'react';
import * as catalogApi from '../api/catalog';
import * as dashboardsApi from '../api/dashboards';
import type { DashboardResponse, RequestStatus, ServiceCatalogResponse } from '../api/types';
import { EmptyState } from '../components/EmptyState';
import { ErrorBanner } from '../components/ErrorBanner';
import { StatCard } from '../components/StatCard';
import { formatMinutes, formatPercent, initials, statusLabel } from '../lib/format';

/** §6.9 - "Vue responsable : volumes par statut, catégorie, agent et période" + les
 * quatre indicateurs + export CSV. Accès gouverné côté serveur par canViewDashboard
 * (DEPARTMENT/DIRECTION/GLOBAL) - un rôle sans ce périmètre reçoit un 404 (ADR-16
 * raisonnement identique), affiché ici comme n'importe quelle autre ErrorBanner.
 *
 * Maquette 07. Deux éléments de la maquette n'ont pas d'équivalent dans
 * `DashboardResponse` et ne sont donc pas rendus : les comparaisons "vs mois précédent"
 * sous chaque indicateur (le serveur ne renvoie qu'une période, pas la précédente) et la
 * liste "Demandes en retard" (le tableau de bord agrège, il ne renvoie aucune demande
 * nommément ; cette liste vit dans "Mes tâches", filtre "En retard uniquement"). Le
 * "Taux de respect des SLA" est affiché sans objectif : aucun seuil cible n'existe en
 * configuration, celui de la maquette serait inventé. */
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

  const totalRequests = dashboard ? Object.values(dashboard.volumesByStatus).reduce((sum, count) => sum + count, 0) : 0;

  return (
    <section>
      <div className="filter-bar dashboard-bar">
        <label>
          Service
          <select value={serviceId ?? ''} onChange={(event) => setServiceId(Number(event.target.value))}>
            {services.map((service) => (
              <option key={service.id} value={service.id}>
                {service.name}
              </option>
            ))}
          </select>
        </label>
        <label>
          Du
          <input type="date" value={from} onChange={(event) => setFrom(event.target.value)} />
        </label>
        <label>
          Au
          <input type="date" value={to} onChange={(event) => setTo(event.target.value)} />
        </label>
        {serviceId !== null && (
          /* Export en lien direct (GET), jamais en fetch/XHR : c'est un téléchargement
             gouverné par la même session, pas une donnée à charger dans la page. */
          <a
            className="button-link button-link-secondary export-link"
            href={dashboardsApi.exportPath({ serviceId, from: toIsoStart(from), to: toIsoEnd(to) })}
          >
            Export CSV
          </a>
        )}
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {dashboard && (
        <>
          <div className="kpi-grid">
            <StatCard
              label="Délai moyen de prise en charge"
              value={formatMinutes(dashboard.averageFirstResponseMinutes)}
              empty={dashboard.averageFirstResponseMinutes === null}
            />
            <StatCard
              label="Délai moyen de résolution"
              value={formatMinutes(dashboard.averageResolutionMinutes)}
              empty={dashboard.averageResolutionMinutes === null}
              tone="warning"
            />
            <StatCard
              label="Taux de respect des SLA"
              value={formatPercent(dashboard.slaComplianceRatePercent)}
              empty={dashboard.slaComplianceRatePercent === null}
              tone="success"
            />
            <StatCard
              label="Taux de réouverture"
              value={formatPercent(dashboard.reopenRatePercent)}
              empty={dashboard.reopenRatePercent === null}
              tone="danger"
            />
          </div>

          <div className="home-grid dashboard-grid">
            <BarPanel
              title="Volumes par statut"
              caption={`${totalRequests} demande(s)`}
              data={dashboard.volumesByStatus}
              /* Les clés viennent du serveur en `Record<string, number>` : `statusLabel`
                 retombe sur la valeur brute si une clé inconnue apparaissait. */
              label={(key) => statusLabel(key as RequestStatus)}
              toneFor={statusTone}
            />
            <BarPanel
              title="Volumes par catégorie"
              data={dashboard.volumesByCategory}
              label={(key) => key}
              toneFor={() => 'primary'}
            />
          </div>

          <section className="panel workload-panel">
            <header className="panel-head">
              <h2>Charge par agent</h2>
            </header>
            <AgentWorkload data={dashboard.volumesByAgent} />
          </section>
        </>
      )}
    </section>
  );
}

/** Les barres sont proportionnelles au plus grand volume de *ce* panneau : elles comparent
 * des lignes entre elles, elles ne prétendent pas mesurer un pourcentage d'un total. */
function BarPanel({
  title,
  caption,
  data,
  label,
  toneFor,
}: {
  title: string;
  caption?: string;
  data: Record<string, number>;
  label: (key: string) => string;
  toneFor: (key: string) => string;
}) {
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1]);
  const max = entries.reduce((highest, [, value]) => Math.max(highest, value), 0);

  return (
    <section className="panel">
      <header className="panel-head">
        <h2>{title}</h2>
        {caption && <span className="panel-caption">{caption}</span>}
      </header>
      {entries.length === 0 ? (
        <EmptyState message="Aucune donnée sur la période." />
      ) : (
        <ul className="bar-list">
          {entries.map(([key, value]) => (
            <li key={key}>
              <span className="bar-label">{label(key)}</span>
              <span className="bar-track">
                <span
                  className={`bar-fill bar-${toneFor(key)}`}
                  style={{ width: max > 0 ? `${Math.max((value / max) * 100, 3)}%` : '3%' }}
                />
              </span>
              <span className="bar-value">{value}</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}

function AgentWorkload({ data }: { data: Record<string, number> }) {
  const entries = Object.entries(data).sort((a, b) => b[1] - a[1]);
  if (entries.length === 0) {
    return <EmptyState message="Aucune demande affectée sur la période." />;
  }
  const max = entries.reduce((highest, [, value]) => Math.max(highest, value), 0);

  return (
    <ul className="workload-list">
      {entries.map(([name, count]) => {
        const [firstName, ...rest] = name.split(' ');
        return (
          <li key={name}>
            <div className="workload-head">
              <span className="avatar" aria-hidden="true">
                {initials(firstName, rest.join(' '))}
              </span>
              <div>
                <span className="workload-name">{name}</span>
                <span className="workload-count">{count} en cours</span>
              </div>
            </div>
            <span className="bar-track">
              <span
                className="bar-fill bar-primary"
                style={{ width: max > 0 ? `${Math.max((count / max) * 100, 4)}%` : '4%' }}
              />
            </span>
          </li>
        );
      })}
    </ul>
  );
}

function statusTone(status: string): string {
  switch (status) {
    case 'SUBMITTED':
      return 'violet';
    case 'CLOSED':
      return 'success';
    case 'DRAFT':
      return 'faint';
    default:
      return 'muted';
  }
}

function toIsoStart(dateOnly: string): string | undefined {
  return dateOnly ? `${dateOnly}T00:00:00Z` : undefined;
}

function toIsoEnd(dateOnly: string): string | undefined {
  return dateOnly ? `${dateOnly}T23:59:59Z` : undefined;
}
