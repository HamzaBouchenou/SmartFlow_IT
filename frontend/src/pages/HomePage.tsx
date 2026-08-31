import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as dashboardsApi from '../api/dashboards';
import type { HomeDashboardResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { useAuth } from '../auth/AuthContext';
import { actionLabel, formatDate, statusLabel } from '../lib/format';

/** §9.4 - écran Accueil : "Raccourcis, demandes récentes, tâches à traiter et indicateurs
 * adaptés au rôle." §6.9's "vue demandeur"/"vue agent" sont les indicateurs adaptés au
 * rôle de cet écran (la "vue responsable" reste le tableau de bord de service séparé,
 * /tableau-de-bord) - voir HomeDashboardResponse côté back-end pour cette correspondance. */
export function HomePage() {
  const { user } = useAuth();
  const [home, setHome] = useState<HomeDashboardResponse | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const result = await dashboardsApi.getHome();
        if (!cancelled) {
          setHome(result);
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

  return (
    <section className="home-page">
      <h1>Bonjour {user?.firstName ?? ''}</h1>

      <div className="home-shortcuts">
        <Link to="/catalogue" className="home-shortcut">
          Nouvelle demande
        </Link>
        <Link to="/mes-demandes" className="home-shortcut">
          Mes demandes
        </Link>
        <Link to="/mes-taches" className="home-shortcut">
          Mes tâches
        </Link>
        <Link to="/tableau-de-bord" className="home-shortcut">
          Tableau de bord
        </Link>
      </div>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {home && (
        <div className="home-sections">
          <section className="home-section">
            <h2>Mes demandes</h2>
            <p>
              <strong>{home.requester.inProgressCount}</strong> demande(s) en cours.
            </p>
            {home.requester.requests.length > 0 ? (
              <table className="task-table">
                <thead>
                  <tr>
                    <th>Référence</th>
                    <th>Titre</th>
                    <th>Statut</th>
                    <th>SLA</th>
                    <th>Échéance de résolution</th>
                  </tr>
                </thead>
                <tbody>
                  {home.requester.requests.map((item) => (
                    <tr key={item.id}>
                      <td>
                        <Link to={`/demandes/${item.id}`}>{item.reference}</Link>
                      </td>
                      <td>{item.title}</td>
                      <td>{statusLabel(item.status)}</td>
                      <td>{item.slaStatus ?? '—'}</td>
                      <td>{formatDate(item.slaDueAtResolution)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <p>Aucune demande en cours.</p>
            )}

            <h3>Dernières décisions</h3>
            {home.requester.recentDecisions.length > 0 ? (
              <ul className="home-decision-list">
                {home.requester.recentDecisions.map((decision, index) => (
                  <li key={`${decision.requestId}-${index}`}>
                    <Link to={`/demandes/${decision.requestId}`}>{decision.reference}</Link> —{' '}
                    {actionLabel(decision.action)} le {formatDate(decision.occurredAt)}
                  </li>
                ))}
              </ul>
            ) : (
              <p>Aucune décision récente.</p>
            )}
          </section>

          {home.agent && (
            <section className="home-section">
              <h2>Mes tâches à traiter</h2>
              <p>
                Charge actuelle : <strong>{home.agent.currentLoad}</strong> demande(s).
              </p>

              <h3>En retard</h3>
              {home.agent.overdue.length > 0 ? (
                <ul className="home-decision-list">
                  {home.agent.overdue.map((item) => (
                    <li key={item.id}>
                      <Link to={`/demandes/${item.id}`}>{item.reference}</Link> — {item.title}
                    </li>
                  ))}
                </ul>
              ) : (
                <p>Aucun dossier en retard.</p>
              )}

              <h3>Priorités hautes</h3>
              {home.agent.highPriority.length > 0 ? (
                <ul className="home-decision-list">
                  {home.agent.highPriority.map((item) => (
                    <li key={item.id}>
                      <Link to={`/demandes/${item.id}`}>{item.reference}</Link> — {item.title} ({item.priority})
                    </li>
                  ))}
                </ul>
              ) : (
                <p>Aucun dossier prioritaire.</p>
              )}
            </section>
          )}
        </div>
      )}
    </section>
  );
}
