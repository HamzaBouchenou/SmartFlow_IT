import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as dashboardsApi from '../api/dashboards';
import type { AgentTaskItem, HomeDashboardResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { EmptyState } from '../components/EmptyState';
import { SlaBadge } from '../components/SlaBadge';
import { StatCard } from '../components/StatCard';
import { actionLabel, formatDate, formatDeadline, statusLabel } from '../lib/format';

/** §9.4 - écran Accueil : "Raccourcis, demandes récentes, tâches à traiter et indicateurs
 * adaptés au rôle." §6.9's "vue demandeur"/"vue agent" sont les indicateurs adaptés au
 * rôle de cet écran (la "vue responsable" reste le tableau de bord de service séparé,
 * /tableau-de-bord) - voir HomeDashboardResponse côté back-end pour cette correspondance.
 *
 * Maquette 02. Deux blocs de la maquette n'ont volontairement pas été repris tels quels :
 * l'histogramme "Respect des SLA - 30 derniers jours" et le fil "Activité récente" ne
 * correspondent à aucune donnée renvoyée par `GET /dashboards/home` (le taux de respect
 * est un indicateur *de service*, borné par canViewDashboard, et il vit sur
 * /tableau-de-bord). Les remplir ici aurait demandé d'inventer des chiffres ; leur place
 * est prise par "Dernières décisions", qui est précisément ce que §6.9 attend de la vue
 * demandeur. */
export function HomePage() {
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

  // "À traiter en priorité" : les deux files que le serveur a déjà calculées, fusionnées
  // sans doublon (un dossier en retard peut aussi être en priorité haute), les retards
  // d'abord - ce sont eux qui appellent une action immédiate.
  const priorityTasks: AgentTaskItem[] = home?.agent
    ? [...home.agent.overdue, ...home.agent.highPriority.filter((task) => !home.agent!.overdue.some((late) => late.id === task.id))]
    : [];

  return (
    <section className="home-page">
      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {home && (
        <>
          <div className="kpi-grid">
            {home.agent && (
              <>
                <StatCard
                  label="Mes tâches ouvertes"
                  value={home.agent.currentLoad}
                  caption="demandes qui vous sont affectées"
                />
                <StatCard
                  label="En retard"
                  value={home.agent.overdue.length}
                  caption="action requise"
                  tone="danger"
                />
                <StatCard
                  label="Priorité haute"
                  value={home.agent.highPriority.length}
                  caption="à traiter en priorité"
                  tone="warning"
                />
              </>
            )}
            <StatCard
              label="Mes demandes en cours"
              value={home.requester.inProgressCount}
              caption="demandes que vous avez soumises"
              tone="success"
            />
          </div>

          <div className="home-grid">
            <div className="home-col">
              {home.agent ? (
                <section className="panel">
                  <header className="panel-head">
                    <h2>À traiter en priorité</h2>
                    <Link to="/mes-taches">Tout voir</Link>
                  </header>
                  {priorityTasks.length > 0 ? (
                    <ul className="row-list">
                      {priorityTasks.map((task) => (
                        <li key={task.id}>
                          <Link to={`/demandes/${task.id}`} className="row-main">
                            <span className="reference">{task.reference}</span>
                            <span className="row-title">{task.title}</span>
                          </Link>
                          <SlaBadge status={task.slaStatus} />
                          <span className="row-deadline">{formatDeadline(task.slaDueAtResolution)}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <EmptyState message="Aucun dossier en retard ni prioritaire. Votre file est à jour." />
                  )}
                </section>
              ) : (
                <section className="panel">
                  <header className="panel-head">
                    <h2>Mes demandes en cours</h2>
                    <Link to="/mes-demandes">Tout voir</Link>
                  </header>
                  {home.requester.requests.length > 0 ? (
                    <ul className="row-list">
                      {home.requester.requests.map((item) => (
                        <li key={item.id}>
                          <Link to={`/demandes/${item.id}`} className="row-main">
                            <span className="reference">{item.reference}</span>
                            <span className="row-title">{item.title}</span>
                          </Link>
                          <SlaBadge status={item.slaStatus} />
                          <span className="row-deadline">{formatDeadline(item.slaDueAtResolution)}</span>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <EmptyState message="Aucune demande en cours. Parcourez le catalogue pour en créer une." />
                  )}
                </section>
              )}

              <section className="panel">
                <header className="panel-head">
                  <h2>Dernières décisions</h2>
                </header>
                {home.requester.recentDecisions.length > 0 ? (
                  <ul className="activity-list">
                    {home.requester.recentDecisions.map((decision, index) => (
                      <li key={`${decision.requestId}-${index}`}>
                        <span className={`activity-dot dot-${decision.action.toLowerCase()}`} aria-hidden="true" />
                        <div>
                          <p className="activity-title">{actionLabel(decision.action)}</p>
                          <p className="activity-meta">
                            <Link to={`/demandes/${decision.requestId}`} className="reference">
                              {decision.reference}
                            </Link>{' '}
                            · {formatDate(decision.occurredAt)}
                          </p>
                          {decision.comment && <p className="activity-comment">{decision.comment}</p>}
                        </div>
                      </li>
                    ))}
                  </ul>
                ) : (
                  <EmptyState message="Aucune décision récente sur vos demandes." />
                )}
              </section>
            </div>

            <div className="home-col">
              {home.agent && (
                <section className="panel">
                  <header className="panel-head">
                    <h2>Mes demandes récentes</h2>
                    <Link to="/mes-demandes">Tout voir</Link>
                  </header>
                  {home.requester.requests.length > 0 ? (
                    <ul className="activity-list">
                      {home.requester.requests.map((item) => (
                        <li key={item.id}>
                          <span className={`activity-dot status-dot-${item.status.toLowerCase()}`} aria-hidden="true" />
                          <div>
                            <Link to={`/demandes/${item.id}`} className="reference">
                              {item.reference}
                            </Link>
                            <p className="activity-title">{statusLabel(item.status)}</p>
                          </div>
                        </li>
                      ))}
                    </ul>
                  ) : (
                    <EmptyState message="Vous n'avez soumis aucune demande." />
                  )}
                </section>
              )}

              <section className="panel">
                <header className="panel-head">
                  <h2>Démarrer</h2>
                </header>
                {/* Une nouvelle demande passe toujours par le choix d'un type dans le
                    catalogue (§6.2) : il n'existe pas de formulaire "générique". */}
                <div className="panel-actions">
                  <Link to="/catalogue" className="button-link">
                    Nouvelle demande
                  </Link>
                  <Link to="/mes-demandes" className="button-link button-link-secondary">
                    Suivre mes demandes
                  </Link>
                </div>
              </section>
            </div>
          </div>
        </>
      )}
    </section>
  );
}
