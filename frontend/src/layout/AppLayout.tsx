import { useState } from 'react';
import { NavLink, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { NotificationBell } from '../components/NotificationBell';
import { initials, roleLabel } from '../lib/format';

// Barre latérale (248 px) + barre supérieure (68 px) : ossature des maquettes 02 à 08.

/** §5 - rôle le plus significatif d'un compte, pour l'affichage seul. REQUESTER passe en
 * dernier : il ne porte aucune permission (RolePermissionRule), donc l'annoncer alors que
 * le compte est aussi AGENT décrirait mal l'utilisateur. */
const ROLE_DISPLAY_ORDER = [
  'FUNCTIONAL_ADMIN',
  'TECHNICAL_ADMIN',
  'AUDITOR',
  'SERVICE_MANAGER',
  'MANAGER',
  'AGENT',
  'REQUESTER',
];

/** Titre et sous-titre de la barre supérieure. Le préfixe le plus spécifique gagne, d'où
 * l'ordre de ce tableau : `/demandes/nouvelle` doit être testé avant `/demandes`. */
const HEADINGS: { prefix: string; title: string; subtitle: string }[] = [
  { prefix: '/catalogue', title: 'Catalogue de services', subtitle: 'Choisissez un service, puis un type de demande.' },
  { prefix: '/mes-demandes', title: 'Mes demandes', subtitle: 'Vos dossiers et vos brouillons, du plus récent au plus ancien.' },
  { prefix: '/demandes/nouvelle', title: 'Nouvelle demande', subtitle: 'Renseignez le formulaire, enregistrez en brouillon ou soumettez.' },
  { prefix: '/demandes', title: 'Détail de la demande', subtitle: 'Suivi, échanges, pièces jointes et actions disponibles.' },
  { prefix: '/mes-taches', title: 'Mes tâches', subtitle: 'Les demandes sur lesquelles vous pouvez agir.' },
  { prefix: '/tableau-de-bord', title: 'Tableaux de bord', subtitle: 'Volumes, délais et respect des SLA par service.' },
  { prefix: '/notifications', title: 'Notifications', subtitle: 'Centre de notifications et préférences d’alerte.' },
  { prefix: '/profil', title: 'Mon profil', subtitle: 'Informations personnelles et sécurité du compte.' },
  { prefix: '/administration/journal-audit', title: "Journal d'audit", subtitle: 'Traçabilité des changements de rôle, de statut et de configuration.' },
  { prefix: '/administration/parametres', title: 'Paramètres généraux', subtitle: 'Catalogue fermé des paramètres système.' },
  { prefix: '/administration/organisation', title: 'Organisation', subtitle: 'Directions, services et équipes.' },
  { prefix: '/administration/modeles-email', title: "Modèles d'e-mail", subtitle: 'Un gabarit par type de notification.' },
  { prefix: '/administration/sla', title: 'Engagements de service', subtitle: 'Délais par type de demande et par priorité.' },
  { prefix: '/administration/diagnostic', title: 'Diagnostic', subtitle: 'État réel des services techniques, interrogés à la demande.' },
  { prefix: '/administration/utilisateurs', title: 'Utilisateurs et rôles', subtitle: 'Comptes, activation et habilitations.' },
  { prefix: '/administration/catalogue', title: 'Catalogue', subtitle: 'Services et types de demande.' },
  { prefix: '/administration/formulaires', title: 'Formulaires', subtitle: 'Champs dynamiques, versionnés par type de demande.' },
  { prefix: '/administration/workflows', title: 'Workflows', subtitle: 'Étapes et transitions, versionnées par type de demande.' },
  { prefix: '/administration', title: 'Administration', subtitle: 'Référentiels, configuration et supervision.' },
];

function resolveHeading(pathname: string): { title: string; subtitle: string } | null {
  return HEADINGS.find((heading) => pathname === heading.prefix || pathname.startsWith(`${heading.prefix}/`)) ?? null;
}

/** Ossature commune des écrans authentifiés (§9.4 - écrans principaux). La navigation ne
 * conditionne aucun lien sur le rôle : chaque page cible reste, elle, protégée par ce que
 * le serveur autorise réellement (RG-06, canAct) - un lien visible mais renvoyant un 404 ou
 * une liste vide est acceptable, un lien absent ne serait qu'une fausse sécurité côté
 * client (§11.1 - "le masquage dans l'interface ne suffit pas"). */
export function AppLayout() {
  const { user, logout } = useAuth();
  const location = useLocation();
  const [menuOpen, setMenuOpen] = useState(false);

  // Le tiroir mobile se referme sur toute navigation partie de la barre latérale : sans
  // cela il resterait ouvert par-dessus l'écran qu'on vient justement de demander. Posé
  // sur les conteneurs cliquables plutôt que dans un effet lié à `location` - un effet
  // qui n'appellerait que setState est précisément ce que React déconseille.
  const closeMenu = () => setMenuOpen(false);

  const displayedRole = ROLE_DISPLAY_ORDER.find((role) => user?.roles.includes(role)) ?? user?.roles[0];
  const heading = resolveHeading(location.pathname);
  const today = new Date().toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });

  return (
    <div className="app-shell" data-menu={menuOpen ? 'open' : 'closed'}>
      <button
        type="button"
        className="sidebar-backdrop"
        aria-label="Fermer le menu de navigation"
        tabIndex={menuOpen ? 0 : -1}
        onClick={() => setMenuOpen(false)}
      />

      <aside className="app-sidebar">
        <NavLink to="/" end className="sidebar-brand" onClick={closeMenu}>
          <span className="brand-mark" aria-hidden="true">
            SF
          </span>
          <span className="brand-name">
            SmartFlow
            <span className="brand-suffix">IT</span>
          </span>
        </NavLink>

        <nav className="sidebar-nav" aria-label="Navigation principale" onClick={closeMenu}>
          <NavLink to="/" end>
            Accueil
          </NavLink>
          <NavLink to="/catalogue">Catalogue</NavLink>
          <NavLink to="/mes-demandes">Mes demandes</NavLink>
          <NavLink to="/mes-taches">Mes tâches</NavLink>
          <NavLink to="/tableau-de-bord">Tableaux de bord</NavLink>
          <NotificationBell />
          <NavLink to="/administration">Administration</NavLink>
        </nav>

        <div className="sidebar-footer">
          <NavLink to="/profil" className="sidebar-user" onClick={closeMenu}>
            <span className="avatar" aria-hidden="true">
              {initials(user?.firstName, user?.lastName)}
            </span>
            <span className="sidebar-user-identity">
              <span className="sidebar-user-name">
                {user?.firstName} {user?.lastName}
              </span>
              <span className="sidebar-user-role">{displayedRole ? roleLabel(displayedRole) : '—'}</span>
            </span>
          </NavLink>
          <button type="button" className="sidebar-logout" onClick={() => void logout()}>
            Déconnexion
          </button>
        </div>
      </aside>

      <header className="app-topbar">
        <button
          type="button"
          className="sidebar-toggle"
          aria-label="Ouvrir le menu de navigation"
          aria-expanded={menuOpen}
          onClick={() => setMenuOpen((open) => !open)}
        >
          ☰
        </button>

        <div className="topbar-heading">
          <div className="topbar-title">{heading ? heading.title : `Bonjour ${user?.firstName ?? ''}`}</div>
          <div className="topbar-subtitle">
            {heading ? heading.subtitle : `${displayedRole ? roleLabel(displayedRole) : 'Utilisateur'} · ${today}`}
          </div>
        </div>

        <div className="app-user">
          <span className="topbar-locale" aria-label="Langue de l'interface">
            FR
          </span>
          <NavLink to="/profil" className="avatar" aria-label="Mon profil">
            {initials(user?.firstName, user?.lastName)}
          </NavLink>
        </div>
      </header>

      <main className="app-main">
        <Outlet />
      </main>
    </div>
  );
}
