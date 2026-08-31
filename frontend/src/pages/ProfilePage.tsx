import { useState } from 'react';
import * as profileApi from '../api/profile';
import { useAuth } from '../auth/AuthContext';
import { ErrorBanner } from '../components/ErrorBanner';

/** §6.1 - libre-service : "Consultation et mise à jour des informations de profil
 * autorisées" (prénom/nom seulement - jamais l'e-mail, le service ou le responsable
 * hiérarchique, réservés à un administrateur fonctionnel, ProfileController.java) et
 * changement de mot de passe (exige l'ancien, distinct d'une réinitialisation admin). */
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
      <h1>Mon profil</h1>

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
    </section>
  );
}
