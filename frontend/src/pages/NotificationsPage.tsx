import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as notificationsApi from '../api/notifications';
import type { NotificationResponse, PageResponse } from '../api/types';
import { EmptyState } from '../components/EmptyState';
import { ErrorBanner } from '../components/ErrorBanner';
import { NotificationPreferences } from '../components/NotificationPreferences';
import { formatNotificationDate, notificationGlyph, notificationTypeLabel } from '../lib/format';

/**
 * §6.8 - centre de notifications complet (l'entrée de la barre latérale, NotificationBell,
 * ne porte que le badge non-lu et un lien ici). Maquette 09.
 *
 * Les deux onglets sont servis par le serveur (`?unread=true`) et non par un filtre posé
 * sur la page déjà chargée : sur une liste paginée, filtrer côté écran donnerait un
 * compteur faux et une page à moitié vide dès la deuxième page.
 *
 * Trois marqueurs distinguent une non-lue - fond, liseré et pastille -, jamais la seule
 * couleur (§8). Chaque ligne renvoie à la demande concernée, ce que le §6.8 demande
 * explicitement ("Liens directs vers la demande concernée").
 */
type Tab = 'all' | 'unread';

export function NotificationsPage() {
  const [tab, setTab] = useState<Tab>('all');
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<PageResponse<NotificationResponse> | null>(null);
  const [totalCount, setTotalCount] = useState<number | null>(null);
  const [unreadTotal, setUnreadTotal] = useState<number | null>(null);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);
  const [markingAll, setMarkingAll] = useState(false);
  // Rechargement demandé par une action de la page ("tout marquer comme lu") : un jeton
  // plutôt qu'une fonction de chargement hissée hors de l'effet, qui obligerait à appeler
  // setState depuis le corps de celui-ci.
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const [listed, unread] = await Promise.all([
          notificationsApi.listNotifications(page, 20, tab === 'unread'),
          notificationsApi.unreadCount(),
        ]);
        if (!cancelled) {
          setResult(listed);
          setUnreadTotal(unread.count);
          // Le total « Toutes » ne se lit que sur la requête non filtrée ; marquer des
          // lignes comme lues ne le change pas, il reste donc valable sur l'autre onglet.
          if (tab === 'all') {
            setTotalCount(listed.totalElements);
          }
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
  }, [page, tab, reloadToken]);

  function selectTab(next: Tab) {
    setTab(next);
    setPage(0);
  }

  /** Marque une ligne lue sans quitter la page (la pastille), ou en la quittant (le lien
   * vers la demande) : dans les deux cas c'est la même écriture serveur. */
  async function handleMarkRead(id: number) {
    setError(null);
    try {
      await notificationsApi.markRead(id);
      setResult((current) =>
        current
          ? {
              ...current,
              // Sur l'onglet « Non lues », la ligne quitte la liste ; sur « Toutes », elle
              // y reste et change seulement d'état.
              content:
                tab === 'unread'
                  ? current.content.filter((notification) => notification.id !== id)
                  : current.content.map((notification) =>
                      notification.id === id
                        ? { ...notification, readAt: new Date().toISOString() }
                        : notification,
                    ),
            }
          : current,
      );
      setUnreadTotal((current) => (current === null ? current : Math.max(current - 1, 0)));
    } catch (markError) {
      setError(markError);
    }
  }

  async function handleMarkAllRead() {
    setMarkingAll(true);
    setError(null);
    try {
      await notificationsApi.markAllRead();
      setReloadToken((current) => current + 1);
    } catch (markError) {
      setError(markError);
    } finally {
      setMarkingAll(false);
    }
  }

  return (
    <section className="notifications-page">
      <div className="notifications-layout">
        <div className="notifications-col">
          <div className="notifications-bar">
            <div className="tabs">
              <button
                type="button"
                className={tab === 'all' ? 'tab active' : 'tab'}
                aria-pressed={tab === 'all'}
                onClick={() => selectTab('all')}
              >
                Toutes
                {totalCount !== null && <span className="tab-count">{totalCount}</span>}
              </button>
              <button
                type="button"
                className={tab === 'unread' ? 'tab active' : 'tab'}
                aria-pressed={tab === 'unread'}
                onClick={() => selectTab('unread')}
              >
                Non lues
                {unreadTotal !== null && <span className="tab-count">{unreadTotal}</span>}
              </button>
            </div>
            <button
              type="button"
              className="link-button"
              disabled={markingAll || unreadTotal === 0}
              onClick={() => void handleMarkAllRead()}
            >
              Tout marquer comme lu
            </button>
          </div>

          <ErrorBanner error={error} />

          <section className="panel notification-panel">
            {loading && <p className="page-loading">Chargement…</p>}

            {result && result.content.length > 0 && (
              <ul className="notification-list">
                {result.content.map((notification) => {
                  const { glyph, tone } = notificationGlyph(notification.type);
                  const unread = !notification.readAt;
                  const body = (
                    <>
                      <span className={`notification-type ${tone}`}>
                        {notificationTypeLabel(notification.type)}
                      </span>
                      <span className="notification-line">
                        <span className="notification-title">{notification.title}</span>
                        {notification.requestReference && (
                          <span className="reference">{notification.requestReference}</span>
                        )}
                      </span>
                    </>
                  );

                  return (
                    <li key={notification.id} className={unread ? 'notification-item unread' : 'notification-item'}>
                      <span className={`notification-glyph ${tone}`} aria-hidden="true">
                        {glyph}
                      </span>

                      {notification.requestId ? (
                        <Link
                          to={`/demandes/${notification.requestId}`}
                          className="notification-body"
                          onClick={() => {
                            if (unread) {
                              void handleMarkRead(notification.id);
                            }
                          }}
                        >
                          {body}
                        </Link>
                      ) : (
                        <span className="notification-body">{body}</span>
                      )}

                      <span className="notification-side">
                        <span className="notification-date">{formatNotificationDate(notification.createdAt)}</span>
                        {unread ? (
                          <button
                            type="button"
                            className="notification-dot"
                            title="Marquer comme lu"
                            aria-label={`Marquer « ${notification.title} » comme lu`}
                            onClick={() => void handleMarkRead(notification.id)}
                          />
                        ) : (
                          <span className="notification-dot-placeholder" aria-hidden="true" />
                        )}
                      </span>
                    </li>
                  );
                })}
              </ul>
            )}

            {result && result.content.length === 0 && !loading && (
              <EmptyState
                message={
                  tab === 'unread'
                    ? 'Aucune notification non lue. Tout est à jour.'
                    : 'Aucune notification pour le moment.'
                }
              />
            )}

            {result && result.totalPages > 1 && (
              <div className="pagination">
                <button type="button" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>
                  Précédent
                </button>
                <span>
                  Page {result.page + 1} / {Math.max(result.totalPages, 1)}
                </span>
                <button
                  type="button"
                  disabled={page + 1 >= result.totalPages}
                  onClick={() => setPage((current) => current + 1)}
                >
                  Suivant
                </button>
              </div>
            )}
          </section>

          <p className="notifications-footnote">
            Chaque notification renvoie directement vers la demande concernée.
          </p>
        </div>

        <aside className="notifications-col">
          <NotificationPreferences />

          {/* §6.8 - deux canaux, mais un seul est réglable : la notification applicative est
              toujours écrite (ADR-12), seul l'e-mail suit les préférences ci-dessus. La carte
              l'affiche comme un état, pas comme une bascule, plutôt que d'offrir un
              interrupteur qui n'aurait rien à commander. */}
          <section className="panel channels-panel">
            <header className="panel-head">
              <h2>Canaux</h2>
            </header>
            <ul className="channel-list">
              <li>
                <span className="channel-identity">
                  <strong>Dans l’application</strong>
                  <span className="channel-caption">centre de notifications</span>
                </span>
                <span className="channel-state channel-state-always">Toujours actif</span>
              </li>
              <li>
                <span className="channel-identity">
                  <strong>E-mail</strong>
                  <span className="channel-caption">évènements importants</span>
                </span>
                <span className="channel-state">Selon les préférences</span>
              </li>
            </ul>
            <p className="panel-footnote">Envoi asynchrone · §6.8</p>
          </section>
        </aside>
      </div>
    </section>
  );
}
