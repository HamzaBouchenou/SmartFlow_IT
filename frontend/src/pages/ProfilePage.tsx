import { useEffect, useState } from 'react';
import * as profileApi from '../api/profile';
import type { NotificationPreferenceResponse, NotificationType } from '../api/types';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.8 - libellés d'affichage seulement : la liste des types et lesquels sont obligatoires
 * viennent toujours du serveur (MandatoryNotificationRule/ADR-12), jamais d'ici. */
const NOTIFICATION_LABELS: Record<NotificationType, string> = {
  SUBMISSION: 'Soumission d’une demande',
  ASSIGNMENT: 'Affectation d’une demande',
  INFO_REQUESTED: 'Demande de complément',
  DECISION: 'Décision (validation, rejet, retour)',
  SLA_WARNING: 'Échéance SLA proche',
  SLA_BREACH: 'Échéance SLA dépassée',
  CLOSURE: 'Clôture d’une demande',
};

/** §6.1 - libre-service : "Consultation et mise à jour des informations de profil
 * autorisées" (prénom/nom seulement - jamais l'e-mail, le service ou le responsable
 * hiérarchique, réservés à un administrateur fonctionnel, ProfileController.java),
 * changement de mot de passe (exige l'ancien, distinct d'une réinitialisation admin) et
 * §6.8 préférences de notification par e-mail. */
export function ProfilePage() {
  const { user } = useAuth();
  const [firstName, setFirstName] = useState(user?.firstName ?? '');
  const [lastName, setLastName] = useState(user?.lastName ?? '');
  const [profileError, setProfileError] = useState<unknown>(null);
  const [profileSaved, setProfileSaved] = useState(false);
  const [savingProfile, setSavingProfile] = useState(false);

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [passwordError, setPasswordError] = useState<unknown>(null);
  const [passwordSaved, setPasswordSaved] = useState(false);
  const [savingPassword, setSavingPassword] = useState(false);

  const [preferences, setPreferences] = useState<NotificationPreferenceResponse[]>([]);
  const [preferenceError, setPreferenceError] = useState<unknown>(null);
  const [savingPreference, setSavingPreference] = useState<NotificationType | null>(null);

  useEffect(() => {
    let cancelled = false;
    profileApi
      .listNotificationPreferences()
      .then((loaded) => {
        if (!cancelled) {
          setPreferences(loaded);
        }
      })
      .catch((error: unknown) => {
        if (!cancelled) {
          setPreferenceError(error);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  /** §6.8 - le serveur reste seul juge : un type obligatoire est refusé côté API (ADR-12),
   * l'écran se contente de désactiver la bascule et de relire la réponse. */
  async function handlePreferenceToggle(type: NotificationType, emailEnabled: boolean) {
    setSavingPreference(type);
    setPreferenceError(null);
    try {
      const updated = await profileApi.updateNotificationPreference(type, { emailEnabled });
      setPreferences((current) =>
        current.map((preference) => (preference.notificationType === type ? updated : preference)),
      );
    } catch (error) {
      setPreferenceError(error);
    } finally {
      setSavingPreference(null);
    }
  }

  async function handleProfileSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSavingProfile(true);
    setProfileError(null);
    setProfileSaved(false);
    try {
      await profileApi.updateProfile({ firstName, lastName });
      setProfileSaved(true);
    } catch (error) {
      setProfileError(error);
    } finally {
      setSavingProfile(false);
    }
  }

  async function handlePasswordSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSavingPassword(true);
    setPasswordError(null);
    setPasswordSaved(false);
    try {
      await profileApi.changePassword({ currentPassword, newPassword });
      setPasswordSaved(true);
      setCurrentPassword('');
      setNewPassword('');
    } catch (error) {
      setPasswordError(error);
    } finally {
      setSavingPassword(false);
    }
  }

  return (
    <section className="profile-page">

      <form className="action-form" onSubmit={(event) => void handleProfileSubmit(event)}>
        <h2>Informations de profil</h2>
        <label htmlFor="profile-email">E-mail</label>
        <input id="profile-email" type="email" value={user?.email ?? ''} disabled />
        <label htmlFor="profile-first-name">Prénom</label>
        <input
          id="profile-first-name"
          required
          value={firstName}
          onChange={(event) => setFirstName(event.target.value)}
        />
        <label htmlFor="profile-last-name">Nom</label>
        <input
          id="profile-last-name"
          required
          value={lastName}
          onChange={(event) => setLastName(event.target.value)}
        />
        <ErrorBanner error={profileError} />
        {profileSaved && <p className="form-success">Profil mis à jour.</p>}
        <div className="action-form-buttons">
          <button type="submit" disabled={savingProfile}>
            Enregistrer
          </button>
        </div>
      </form>

      <form className="action-form" onSubmit={(event) => void handlePasswordSubmit(event)}>
        <h2>Changer de mot de passe</h2>
        <label htmlFor="current-password">Mot de passe actuel</label>
        <input
          id="current-password"
          type="password"
          required
          value={currentPassword}
          onChange={(event) => setCurrentPassword(event.target.value)}
        />
        <label htmlFor="new-password">Nouveau mot de passe</label>
        <input
          id="new-password"
          type="password"
          required
          minLength={8}
          value={newPassword}
          onChange={(event) => setNewPassword(event.target.value)}
        />
        <ErrorBanner error={passwordError} />
        {passwordSaved && <p className="form-success">Mot de passe changé.</p>}
        <div className="action-form-buttons">
          <button type="submit" disabled={savingPassword}>
            Changer le mot de passe
          </button>
        </div>
      </form>

      <section className="notification-preferences">
        <h2>Préférences de notification</h2>
        <p className="form-hint">
          Les alertes d’échéance SLA restent toujours envoyées : §6.8 interdit de désactiver
          une alerte obligatoire.
        </p>
        <ErrorBanner error={preferenceError} />
        <ul className="preference-list">
          {preferences.map((preference) => (
            <li key={preference.notificationType}>
              <label>
                <input
                  type="checkbox"
                  checked={preference.emailEnabled}
                  disabled={preference.mandatory || savingPreference === preference.notificationType}
                  onChange={(event) =>
                    void handlePreferenceToggle(preference.notificationType, event.target.checked)
                  }
                />
                {NOTIFICATION_LABELS[preference.notificationType] ?? preference.notificationType}
              </label>
              {preference.mandatory && <span className="preference-mandatory">obligatoire</span>}
            </li>
          ))}
        </ul>
      </section>
    </section>
  );
}
