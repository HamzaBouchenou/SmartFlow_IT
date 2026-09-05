import { useEffect, useState } from 'react';
import { Link } from 'react-router-dom';
import * as notificationsApi from '../api/notifications';
import type { NotificationResponse, PageResponse } from '../api/types';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatDate, notificationTypeLabel } from '../lib/format';

/** §6.8 - centre de notifications complet (l'en-tête, NotificationBell, ne porte que le
 * badge non-lu et un lien ici). */
export function NotificationsPage() {
  const [result, setResult] = useState<PageResponse<NotificationResponse> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<unknown>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setLoading(true);
      try {
        const response = await notificationsApi.listNotifications(page);
        if (!cancelled) {
          setResult(response);
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
  }, [page]);

  async function handleMarkRead(id: number) {
    setError(null);
    try {
      await notificationsApi.markRead(id);
      // Un rechargement local plutôt qu'un nouvel appel liste : la page affichée ne
      // change pas de contenu, seul le statut lu/non lu de cette ligne évolue.
      setResult((current) =>
        current
          ? { ...current, content: current.content.map((n) => (n.id === id ? { ...n, readAt: new Date().toISOString() } : n)) }
          : current,
      );
    } catch (markError) {
      setError(markError);
    }
  }

  return (
    <section>

      <ErrorBanner error={error} />
      {loading && <p className="page-loading">Chargement…</p>}

      {result && (
        <>
          <ul className="notification-list">
            {result.content.map((notification) => (
              <li key={notification.id} className={notification.readAt ? 'notification-item' : 'notification-item unread'}>
                <div>
                  <span className="notification-type">{notificationTypeLabel(notification.type)}</span>
                  <p>{notification.title}</p>
                  {notification.requestId && (
                    <Link to={`/demandes/${notification.requestId}`}>{notification.requestReference}</Link>
                  )}
                  <div className="notification-date">{formatDate(notification.createdAt)}</div>
                </div>
                {!notification.readAt && (
                  <button type="button" onClick={() => void handleMarkRead(notification.id)}>
                    Marquer comme lu
                  </button>
                )}
              </li>
            ))}
            {result.content.length === 0 && <li className="notification-empty">Aucune notification.</li>}
          </ul>

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
        </>
      )}
    </section>
  );
}
