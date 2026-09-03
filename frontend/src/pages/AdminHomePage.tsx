import { Link } from 'react-router-dom';

/** §9.4 - écran "Administration" : "Référentiels, formulaires, workflows, SLA,
 * utilisateurs, notifications et audit." Un seul point d'entrée dans la navigation plutôt
 * qu'un lien par écran (§6.10 en couvre déjà six) - chaque lien ci-dessous reste, lui,
 * gouverné côté serveur par son propre contrôle d'accès (isFunctionalAdmin/
 * canViewAuditLog) : cette page ne masque rien selon le rôle non plus (§11.1). */
export function AdminHomePage() {
  return (
    <section>
      <div className="admin-menu">
        <div className="admin-menu-card">
          <Link to="/administration/utilisateurs">Utilisateurs et rôles</Link>
          <p>§6.1/§5.1 - comptes, activation/désactivation, mot de passe et affectations de rôle.</p>
        </div>
        <div className="admin-menu-card">
          <Link to="/administration/journal-audit">Journal d'audit</Link>
          <p>§13.1 - historique des changements de rôle, statut, affectation et configuration.</p>
        </div>
        <div className="admin-menu-card">
          <Link to="/administration/parametres">Paramètres généraux</Link>
          <p>§6.10 - seuils de sécurité, pièces jointes, fenêtres RG-08/RG-12.</p>
        </div>
        <div className="admin-menu-card">
          <Link to="/administration/organisation">Directions, services et équipes</Link>
          <p>§4.1 - organisation, avec activation/désactivation logique (RG-02/RG-12).</p>
        </div>
        <div className="admin-menu-card">
          <Link to="/administration/catalogue">Catalogue</Link>
          <p>§6.2 - services et types de demande (non versionnés, ADR-17).</p>
        </div>
        <div className="admin-menu-card">
          <Link to="/administration/formulaires">Formulaires</Link>
          <p>§6.3/§10.1 - champs configurables par type de demande, versionnés (ADR-17).</p>
        </div>
        <div className="admin-menu-card">
          <Link to="/administration/workflows">Workflows</Link>
          <p>§6.5/§10.1 - étapes et transitions par type de demande, versionnées (ADR-17, RG-03).</p>
        </div>
        <div className="admin-menu-card">
          <Link to="/administration/modeles-email">Modèles d'e-mail</Link>
          <p>§6.8 - sujet et contenu des sept notifications déclenchées par l'application.</p>
        </div>
        <div className="admin-menu-card">
          <Link to="/administration/sla">SLA par type de demande</Link>
          <p>§6.7 - délais de prise en charge et de résolution, par priorité.</p>
        </div>
        <div className="admin-menu-card">
          <Link to="/administration/diagnostic">Diagnostic</Link>
          <p>§15.3 - état du back-end, de la base, du service IA et de l'envoi d'e-mails.</p>
        </div>
      </div>
    </section>
  );
}
