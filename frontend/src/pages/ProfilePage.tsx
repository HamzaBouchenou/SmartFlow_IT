import { useEffect, useState } from 'react';
import * as profileApi from '../api/profile';
import type { MyProfileResponse } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/ErrorBanner';
import { formatSessionCountdown, initials, roleLabel, scopeTypeLabel } from '../lib/format';

/** Le serveur exige 8 caractères (ChangePasswordRequest, `@Size(min = 8)`) : cet écran
 * affiche la règle réellement appliquée, jamais une politique plus stricte qu'il ne
 * saurait pas faire respecter. */
const MIN_PASSWORD_LENGTH = 8;

/**
 * §6.1 - libre-service sur son propre compte (maquette 10). Trois gestes seulement, parce
 * que le §6.1 n'en autorise pas d'autres au titulaire du compte : consulter, corriger son
 * identité (prénom/nom), changer son mot de passe.
 *
 * Tout ce qui est à droite est en lecture : l'e-mail est l'identité de connexion, et
 * rattachement comme habilitations relèvent de la ligne suivante du même §6.1 ("Gestion des
 * rôles, du service de rattachement et du responsable hiérarchique"), réservée à un
 * administrateur fonctionnel. Ces champs ne sont pas seulement désactivés côté écran : le
 * serveur ne les accepte pas non plus (UpdateProfileRequest ne porte que firstName et
 * lastName).
 *
 * Les préférences de notification, qui vivaient ici, sont passées sur l'écran Notifications
 * (maquette 09) - c'est là qu'elles se lisent avec les alertes qu'elles gouvernent.
 */
export function ProfilePage() {
  const { user, logout, refresh } = useAuth();

  const [profile, setProfile] = useState<MyProfileResponse | null>(null);
  const [loadError, setLoadError] = useState<unknown>(null);

  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [profileError, setProfileError] = useState<unknown>(null);
  const [profileSaved, setProfileSaved] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [confirmationError, setConfirmationError] = useState<string | null>(null);
  const [passwordError, setPasswordError] = useState<unknown>(null);
  const [passwordSaved, setPasswordSaved] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);

  // §6.1 "Expiration de session" - l'échéance vient du serveur ; seul le décompte est local,
  // d'où ce `tick` qui force un rendu par minute sans jamais recalculer l'échéance ici.
  const [, setTick] = useState(0);

  // Rechargement demandé après une mise à jour du profil : un jeton plutôt qu'une fonction
  // de chargement hissée hors de l'effet.
  const [reloadToken, setReloadToken] = useState(0);

  useEffect(() => {
    let cancelled = false;
    async function load(background: boolean) {
      try {
        const loaded = await profileApi.getMyProfile(background);
        if (!cancelled) {
          setProfile(loaded);
          setLoadError(null);
        }
      } catch (error) {
        if (!cancelled) {
          setLoadError(error);
        }
      }
    }
    void load(false);

    // Le décompte s'affiche à la minute ; l'échéance elle-même est relue régulièrement,
    // parce qu'un geste de l'utilisateur dans un autre onglet repousse l'expiration côté
    // serveur sans que cet écran-ci en sache rien. Cette relecture est marquée comme
    // requête d'arrière-plan (ADR-22) : sans cela elle repousserait elle-même l'échéance
    // qu'elle vient lire, et le compte à rebours ne descendrait jamais.
    const display = setInterval(() => setTick((value) => value + 1), 30_000);
    const reload = setInterval(() => void load(true), 60_000);
    return () => {
      cancelled = true;
      clearInterval(display);
      clearInterval(reload);
    };
  }, [reloadToken]);

  async function handleProfileSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSavingProfile(true);
    setProfileError(null);
    setProfileSaved(false);
    try {
      await profileApi.updateProfile({ firstName, lastName });
      setProfileSaved(true);
      // L'ossature (barre latérale, avatar) affiche l'identité renvoyée par /auth/me :
      // sans cette relecture elle garderait l'ancien nom jusqu'au prochain chargement.
      await refresh();
      setReloadToken((current) => current + 1);
    } catch (error) {
      setProfileError(error);
    } finally {
      setSavingProfile(false);
    }
  }

  async function handlePasswordSubmit(event: React.FormEvent) {
    event.preventDefault();
    setPasswordSaved(false);
    setPasswordError(null);

    // La confirmation ne concerne que la saisie : elle n'existe pas côté serveur, qui ne
    // reçoit jamais qu'un seul nouveau mot de passe.
    if (newPassword !== confirmation) {
      setConfirmationError('La confirmation ne correspond pas au nouveau mot de passe.');
      return;
    }
    setConfirmationError(null);

    setSavingPassword(true);
    try {
      await profileApi.changePassword({ currentPassword, newPassword });
      setPasswordSaved(true);
      setCurrentPassword('');
      setNewPassword('');
      setConfirmation('');
    } catch (error) {
      setPasswordError(error);
    } finally {
      setSavingPassword(false);
    }
  }

  return (
    <section className="profile-page">
      <ErrorBanner error={loadError} />

      <div className="profile-layout">
        <div className="profile-col">
          <section className="panel identity-card">
            <span className="identity-avatar" aria-hidden="true">
              {initials(user?.firstName, user?.lastName)}
            </span>
            <div className="identity-body">
              <h2>
                {user?.firstName} {user?.lastName}
              </h2>
              <p className="identity-email">{user?.email}</p>
              <div className="badge-row">
                {(user?.roles ?? []).map((role) => (
                  <span key={role} className="status-badge tone-primary">
                    {roleLabel(role)}
                  </span>
                ))}
                {profile?.departmentName && (
                  <span className="status-badge tone-violet">{profile.departmentName}</span>
                )}
              </div>
            </div>
            {profile && (
              <span className={profile.active ? 'account-state active' : 'account-state inactive'}>
                {profile.active ? 'Compte actif' : 'Compte désactivé'}
              </span>
            )}
          </section>

          <form className="panel profile-form" onSubmit={(event) => void handleProfileSubmit(event)}>
            <header className="panel-head">
              <div>
                <h2>Informations personnelles</h2>
                <p className="panel-caption">Seuls les champs autorisés sont modifiables (§6.1).</p>
              </div>
              <button type="submit" className="button-primary" disabled={savingProfile}>
                Enregistrer
              </button>
            </header>

            <div className="form-grid">
              <div className="form-field">
                <label htmlFor="profile-first-name">Prénom</label>
                <input
                  id="profile-first-name"
                  required
                  value={firstName}
                  onChange={(event) => setFirstName(event.target.value)}
                />
              </div>
              <div className="form-field">
                <label htmlFor="profile-last-name">Nom</label>
                <input
                  id="profile-last-name"
                  required
                  value={lastName}
                  onChange={(event) => setLastName(event.target.value)}
                />
              </div>
              <div className="form-field">
                <label htmlFor="profile-email">Identifiant</label>
                <input id="profile-email" type="email" value={user?.email ?? ''} readOnly disabled />
                <p className="field-help">Géré par l’administrateur</p>
              </div>
            </div>

            <ErrorBanner error={profileError} />
            {profileSaved && <p className="form-success">Profil mis à jour.</p>}
          </form>

          <form className="panel profile-form" onSubmit={(event) => void handlePasswordSubmit(event)}>
            <header className="panel-head">
              <div>
                <h2>Mot de passe</h2>
                <p className="panel-caption">Changement sécurisé — §6.1</p>
              </div>
            </header>

            <div className="form-field">
              <label htmlFor="current-password">Mot de passe actuel</label>
              <input
                id="current-password"
                type="password"
                autoComplete="current-password"
                required
                value={currentPassword}
                onChange={(event) => setCurrentPassword(event.target.value)}
              />
            </div>

            <div className="form-grid">
              <div className="form-field">
                <label htmlFor="new-password">Nouveau mot de passe</label>
                <input
                  id="new-password"
                  type="password"
                  autoComplete="new-password"
                  required
                  minLength={MIN_PASSWORD_LENGTH}
                  value={newPassword}
                  onChange={(event) => setNewPassword(event.target.value)}
                />
              </div>
              <div className="form-field">
                <label htmlFor="confirm-password">Confirmation</label>
                <input
                  id="confirm-password"
                  type="password"
                  autoComplete="new-password"
                  required
                  minLength={MIN_PASSWORD_LENGTH}
                  aria-describedby={confirmationError ? 'confirm-password-error' : undefined}
                  value={confirmation}
                  onChange={(event) => setConfirmation(event.target.value)}
                />
                {confirmationError && (
                  <p className="field-error" id="confirm-password-error">
                    {confirmationError}
                  </p>
                )}
              </div>
            </div>

            <p className="password-policy">{MIN_PASSWORD_LENGTH} caractères minimum.</p>

            <ErrorBanner error={passwordError} />
            {passwordSaved && <p className="form-success">Mot de passe changé.</p>}

            <div className="action-form-buttons">
              <button type="submit" disabled={savingPassword}>
                Modifier le mot de passe
              </button>
            </div>
          </form>
        </div>

        <aside className="profile-col">
          <section className="panel">
            <header className="panel-head">
              <div>
                <h2>Rattachement</h2>
                <p className="panel-caption">Lecture seule</p>
              </div>
            </header>
            <dl className="aside-meta">
              <dt>Direction</dt>
              <dd>{profile?.directionName ?? '—'}</dd>
              <dt>Service</dt>
              <dd>{profile?.departmentName ?? '—'}</dd>
              <dt>Responsable hiérarchique</dt>
              <dd>{profile?.managerName ?? '—'}</dd>
            </dl>
          </section>

          <section className="panel">
            <header className="panel-head">
              <h2>Rôles et périmètre</h2>
            </header>
            {profile && profile.roles.length > 0 ? (
              <ul className="role-list">
                {profile.roles.map((assignment) => (
                  <li key={`${assignment.role}-${assignment.scopeType}-${assignment.scopeLabel ?? ''}`}>
                    <span className="status-badge tone-primary">{roleLabel(assignment.role)}</span>
                    <span className="role-scope">
                      {scopeTypeLabel(assignment.scopeType)}
                      {assignment.scopeLabel ? ` · ${assignment.scopeLabel}` : ''}
                    </span>
                  </li>
                ))}
              </ul>
            ) : (
              <p className="panel-caption">Aucune habilitation particulière.</p>
            )}
            <p className="panel-footnote">Attribués par l’administrateur</p>
          </section>

          <section className="panel">
            <header className="panel-head">
              <h2>Sécurité de la session</h2>
            </header>
            <dl className="aside-meta">
              <dt>Expiration de session</dt>
              <dd className="session-countdown">{formatSessionCountdown(profile?.sessionExpiresAt)}</dd>
            </dl>
            <p className="panel-caption">Prolongée à chaque action dans l’application.</p>
            <div className="panel-actions">
              <button type="button" className="button-danger-outline" onClick={() => void logout()}>
                Se déconnecter
              </button>
            </div>
          </section>
        </aside>
      </div>
    </section>
  );
}
