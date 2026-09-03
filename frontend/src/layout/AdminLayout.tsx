import { NavLink, Outlet } from 'react-router-dom';

/** §6.10/§9.4 - ossature commune des écrans d'administration (maquette 08) : un rail de
 * sous-navigation à gauche du contenu, pour passer d'un référentiel à l'autre sans
 * repasser par la page d'accueil de l'administration.
 *
 * Comme la navigation principale, ce rail ne conditionne aucun lien sur le rôle : chaque
 * écran cible reste gouverné côté serveur par son propre contrôle (`isFunctionalAdmin`,
 * `isTechnicalAdmin`, `canViewAuditLog`). Un lien qui mène à un 404 est acceptable ; un
 * lien masqué ne serait qu'une fausse sécurité (§11.1). */
const FUNCTIONAL_LINKS: { to: string; label: string }[] = [
  { to: '/administration/utilisateurs', label: 'Utilisateurs et rôles' },
  { to: '/administration/organisation', label: 'Organisation' },
  { to: '/administration/catalogue', label: 'Catalogue et catégories' },
  { to: '/administration/workflows', label: 'Workflows' },
  { to: '/administration/formulaires', label: 'Formulaires' },
  { to: '/administration/sla', label: 'SLA' },
  { to: '/administration/modeles-email', label: "Modèles d'e-mail" },
  { to: '/administration/parametres', label: 'Paramètres généraux' },
];

const TECHNICAL_LINKS: { to: string; label: string }[] = [
  { to: '/administration/journal-audit', label: "Journal d'audit" },
  { to: '/administration/diagnostic', label: 'Diagnostic' },
];

export function AdminLayout() {
  return (
    <div className="admin-layout">
      <nav className="admin-nav" aria-label="Navigation de l'administration">
        <NavLink to="/administration" end>
          Vue d'ensemble
        </NavLink>
        {FUNCTIONAL_LINKS.map((link) => (
          <NavLink key={link.to} to={link.to}>
            {link.label}
          </NavLink>
        ))}
        <span className="admin-nav-heading">Administration technique</span>
        {TECHNICAL_LINKS.map((link) => (
          <NavLink key={link.to} to={link.to}>
            {link.label}
          </NavLink>
        ))}
      </nav>

      <div className="admin-content">
        <Outlet />
      </div>
    </div>
  );
}
