import { NavLink, Outlet } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { NotificationBell } from '../components/NotificationBell';

/** Ossature commune des écrans authentifiés (§9.4 - écrans principaux). La navigation ne
 * conditionne aucun lien sur le rôle : chaque page cible reste, elle, protégée par ce que
 * le serveur autorise réellement (RG-06, canAct) - un lien visible mais renvoyant un 404 ou
 * une liste vide est acceptable, un lien absent ne serait qu'une fausse sécurité côté
 * client (§11.1 - "le masquage dans l'interface ne suffit pas"). */
export function AppLayout() {
  const { user, logout } = useAuth();

  return (
    <div className="app-shell">
      <header className="app-header">
        <span className="app-title">SmartFlow IT</span>
        <nav>
          <NavLink to="/" end>
            Accueil
          </NavLink>
          <NavLink to="/catalogue">Catalogue</NavLink>
          <NavLink to="/mes-demandes">Mes demandes</NavLink>
          <NavLink to="/mes-taches">Mes tâches</NavLink>
          <NavLink to="/tableau-de-bord">Tableau de bord</NavLink>
          <NavLink to="/administration">Administration</NavLink>
        </nav>
        <div className="app-user">
          <NotificationBell />
          <NavLink to="/profil">
            {user?.firstName} {user?.lastName}
          </NavLink>
          <button type="button" onClick={() => void logout()}>
            Déconnexion
          </button>
        </div>
      </header>
      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
