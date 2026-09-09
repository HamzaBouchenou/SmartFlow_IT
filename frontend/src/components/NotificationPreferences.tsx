import { useEffect, useState } from 'react';
import * as profileApi from '../api/profile';
import type { NotificationPreferenceResponse, NotificationType } from '../api/types';
import { ErrorBanner } from './ErrorBanner';
import { ToggleSwitch } from './ToggleSwitch';
import { notificationPreferenceLabel } from '../lib/format';

/**
 * §6.8 - « Préférences de notification limitées pour éviter la désactivation des alertes
 * obligatoires » (maquette 09, colonne de droite).
 *
 * Le partage entre obligatoires et facultatives n'est jamais posé ici : il vient du
 * drapeau `mandatory` que le serveur renvoie pour chaque type, lequel vient lui-même de
 * `MandatoryNotificationRule` (ADR-12), la même règle pure que `NotificationService`
 * consulte au moment d'envoyer. Une seule liste de vérité, jamais deux qui pourraient
 * diverger - c'est aussi pourquoi une alerte obligatoire s'affiche verrouillée plutôt que
 * masquée : l'utilisateur doit voir qu'elle existe et qu'il ne peut pas la couper.
 *
 * Ce que ces bascules gouvernent est uniquement le canal e-mail. La notification
 * applicative elle-même est toujours écrite (ADR-12), ce que la carte « Canaux » de la
 * page dit explicitement plutôt que de le laisser deviner.
 */
export function NotificationPreferences() {
  const [preferences, setPreferences] = useState<NotificationPreferenceResponse[]>([]);
  const [error, setError] = useState<unknown>(null);
  const [saving, setSaving] = useState<NotificationType | null>(null);

  useEffect(() => {
    let cancelled = false;
    profileApi
      .listNotificationPreferences()
      .then((loaded) => {
        if (!cancelled) {
          setPreferences(loaded);
        }
      })
      .catch((loadError: unknown) => {
        if (!cancelled) {
          setError(loadError);
        }
      });
    return () => {
      cancelled = true;
    };
  }, []);

  /** Le serveur reste seul juge : désactiver un type obligatoire est refusé côté API
   * (NOTIFICATION_TYPE_MANDATORY), l'écran se contente de relire la réponse. */
  async function handleToggle(type: NotificationType, emailEnabled: boolean) {
    setSaving(type);
    setError(null);
    try {
      const updated = await profileApi.updateNotificationPreference(type, { emailEnabled });
      setPreferences((current) =>
        current.map((preference) => (preference.notificationType === type ? updated : preference)),
      );
    } catch (toggleError) {
      setError(toggleError);
    } finally {
      setSaving(null);
    }
  }

  const mandatory = preferences.filter((preference) => preference.mandatory);
  const optional = preferences.filter((preference) => !preference.mandatory);

  return (
    <section className="panel preferences-panel">
      <header className="panel-head">
        <h2>Préférences</h2>
      </header>
      <p className="panel-caption">Certaines alertes ne peuvent pas être désactivées.</p>
      <ErrorBanner error={error} />

      {mandatory.length > 0 && (
        <>
          <h3 className="preference-group">Obligatoires</h3>
          <ul className="preference-list">
            {mandatory.map((preference) => (
              <li key={preference.notificationType}>
                <span className="preference-label">
                  {notificationPreferenceLabel(preference.notificationType)}
                </span>
                <ToggleSwitch
                  label={`${notificationPreferenceLabel(preference.notificationType)} (obligatoire)`}
                  checked
                  disabled
                  onChange={() => undefined}
                />
              </li>
            ))}
          </ul>
        </>
      )}

      {optional.length > 0 && (
        <>
          <h3 className="preference-group">Facultatives</h3>
          <ul className="preference-list">
            {optional.map((preference) => (
              <li key={preference.notificationType}>
                <span className="preference-label">
                  {notificationPreferenceLabel(preference.notificationType)}
                </span>
                <ToggleSwitch
                  label={notificationPreferenceLabel(preference.notificationType)}
                  checked={preference.emailEnabled}
                  disabled={saving === preference.notificationType}
                  onChange={(checked) => void handleToggle(preference.notificationType, checked)}
                />
              </li>
            ))}
          </ul>
        </>
      )}
    </section>
  );
}
