import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import * as notificationsApi from '../api/notifications';

// §6.8 - "Centre de notifications... avec statut lu/non lu" : un simple lien avec un
// badge de compte non-lu dans la barre latérale, la liste complète vivant sur
// /notifications. `NavLink` plutôt que `Link` pour que l'entrée porte le même état actif
// que ses voisines une fois cette page ouverte (maquette 02).
const POLL_INTERVAL_MS = 30_000;

export function NotificationBell() {
  const [count, setCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function refresh() {
      try {
        const result = await notificationsApi.unreadCount();
        if (!cancelled) {
          setCount(result.count);
        }
      } catch {
        // Un échec de rafraîchissement du badge n'est pas une erreur à interrompre la
        // navigation pour - la page /notifications elle-même affiche l'ErrorBanner utile.
      }
    }
    void refresh();
    const interval = setInterval(() => void refresh(), POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      clearInterval(interval);
    };
  }, []);

  return (
    <NavLink to="/notifications" className="notification-bell">
      Notifications
      {count > 0 && <span className="notification-badge">{count}</span>}
    </NavLink>
  );
}
