import { useState } from 'react';
import type { FormEvent } from 'react';
import { Navigate, useLocation, useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.1 - "Connexion par identifiant et mot de passe" (maquette 01).
 *
 * §13/ADR-13 : l'échec est toujours rendu par le message unique du serveur, quel qu'en soit
 * le motif. Cet écran ne distingue jamais un compte inconnu d'un mot de passe erroné, et
 * n'annonce pas non plus un verrouillage - ce serait rendre les comptes énumérables. */
export function LoginPage() {
  const { user, login, sessionExpired } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<unknown>(null);
  const [submitting, setSubmitting] = useState(false);

  if (user) {
    const from = (location.state as { from?: { pathname?: string } } | null)?.from?.pathname ?? '/catalogue';
    return <Navigate to={from} replace />;
  }

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(email, password);
      navigate('/catalogue', { replace: true });
    } catch (submitError) {
      setError(submitError);
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="login-page">
      <aside className="login-aside">
        <span className="sidebar-brand">
          <span className="brand-mark" aria-hidden="true">
            SF
          </span>
          <span className="brand-name">SmartFlow IT</span>
        </span>

        <div className="login-pitch">
          <h1>
            Vos demandes internes,
            <br />
            au même endroit.
          </h1>
          <p>Un portail unique, des circuits de validation transparents et des délais mesurés.</p>

          <ul className="login-features">
            <li>
              <strong>Portail unique</strong>
              catalogue de services
            </li>
            <li>
              <strong>SLA mesurés</strong>
              alertes et escalades
            </li>
            <li>
              <strong>Traçabilité</strong>
              historique complet
            </li>
          </ul>
        </div>
      </aside>

      <main className="login-main">
        <form className="login-form" onSubmit={handleSubmit}>
          <h1>Connexion</h1>
          <p className="login-subtitle">Accédez à votre espace SmartFlow IT.</p>

          {/* §6.1 "Expiration de session"/ADR-22 - dire pourquoi l'utilisateur se retrouve
              ici, sans quoi une session expirée est indiscernable d'une déconnexion. Ce
              message ne révèle rien : son destinataire est celui-là même qui possédait la
              session. `role="status"` plutôt qu'une alerte - c'est une information sur l'état
              de l'écran, pas l'échec de la tentative en cours (celui-là reste l'ErrorBanner,
              et reste unique quel qu'en soit le motif - §13). */}
          {sessionExpired && (
            <p className="login-notice" role="status">
              Votre session a expiré après une période d’inactivité. Veuillez vous reconnecter.
            </p>
          )}

          <label htmlFor="login-email">Identifiant</label>
          <input
            id="login-email"
            type="email"
            autoComplete="username"
            placeholder="prenom.nom@neonovia.ma"
            required
            value={email}
            onChange={(event) => setEmail(event.target.value)}
          />

          <label htmlFor="login-password">Mot de passe</label>
          <input
            id="login-password"
            type="password"
            autoComplete="current-password"
            required
            value={password}
            onChange={(event) => setPassword(event.target.value)}
          />

          <ErrorBanner error={error} />

          <button type="submit" disabled={submitting}>
            {submitting ? 'Connexion…' : 'Se connecter'}
          </button>

          {/* §4.2/§6.10 : aucun flux de réinitialisation en libre-service n'existe - un
              compte est toujours provisionné et réinitialisé par un administrateur
              fonctionnel. Un lien "Mot de passe oublié" ne mènerait donc nulle part. */}
          <p className="login-hint">
            Mot de passe oublié ? Contactez votre administrateur fonctionnel : lui seul peut le réinitialiser.
          </p>

          <p className="login-legal">Session sécurisée — déconnexion automatique après inactivité.</p>
        </form>
      </main>
    </div>
  );
}
