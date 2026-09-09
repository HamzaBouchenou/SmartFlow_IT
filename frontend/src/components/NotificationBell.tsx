import { useEffect, useState } from 'react';
import { NavLink } from 'react-router-dom';
import * as notificationsApi from '../api/notifications';
import { ApiError } from '../api/client';

// §6.8 - "Centre de notifications... avec statut lu/non lu" : un simple lien avec un
// badge de compte non-lu dans la barre latérale, la liste complète vivant sur
// /notifications. `NavLink` plutôt que `Link` pour que l'entrée porte le même état actif
// que ses voisines une fois cette page ouverte (maquette 02).
const POLL_INTERVAL_MS = 30_000;

export function NotificationBell() {
  const [count, setCount] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function refresh(background: boolean) {
      try {
        const result = await notificationsApi.unreadCount(background);
        if (!cancelled) {
          setCount(result.count);
        }
      } catch (error) {
        // Un échec de rafraîchissement du badge n'est pas une erreur à interrompre la
        // navigation pour - la page /notifications elle-même affiche l'ErrorBanner utile.
        // Une session expirée (ADR-22) en est une : le minuteur s'arrête, sans quoi il
        // interrogerait une route protégée toutes les 30 s jusqu'à la fermeture de
        // l'onglet, et l'écran continuerait d'afficher un badge que plus rien ne met à
        // jour.
        if (error instanceof ApiError && error.status === 401) {
          cancelled = true;
          setCount(0);
        }
      }
    }
    // Le premier appel accompagne l'affichage de l'écran demandé par l'utilisateur ; les
    // suivants sont l'oeuvre du minuteur seul, donc marqués comme tels (ADR-22).
    void refresh(false);
    const interval = setInterval(() => void refresh(true), POLL_INTERVAL_MS);
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
