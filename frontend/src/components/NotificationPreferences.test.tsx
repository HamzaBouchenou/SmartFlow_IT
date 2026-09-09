import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { NotificationPreferences } from './NotificationPreferences';
import * as profileApi from '../api/profile';

vi.mock('../api/profile');

const listNotificationPreferences = vi.mocked(profileApi.listNotificationPreferences);
const updateNotificationPreference = vi.mocked(profileApi.updateNotificationPreference);

beforeEach(() => {
  vi.resetAllMocks();
  listNotificationPreferences.mockResolvedValue([
    { notificationType: 'ASSIGNMENT', emailEnabled: true, mandatory: false },
    { notificationType: 'SLA_BREACH', emailEnabled: true, mandatory: true },
  ]);
});

describe('NotificationPreferences', () => {
  it('§6.8 - une alerte obligatoire reste visible et cochée, mais non basculable', async () => {
    render(<NotificationPreferences />);

    const mandatory = await screen.findByLabelText(/retard et escalade \(obligatoire\)/i);
    // Visible, pas masquée : l'utilisateur doit voir que l'alerte existe et qu'il ne peut
    // pas la couper (§6.8 - "préférences limitées pour éviter la désactivation").
    expect(mandatory).toBeChecked();
    expect(mandatory).toBeDisabled();
  });

  it('§6.8 - une alerte facultative se bascule et l’écran relit la réponse du serveur', async () => {
    const user = userEvent.setup();
    updateNotificationPreference.mockResolvedValue({
      notificationType: 'ASSIGNMENT',
      emailEnabled: false,
      mandatory: false,
    });

    render(<NotificationPreferences />);
    const optional = await screen.findByLabelText(/^affectation d’une demande$/i);
    expect(optional).toBeChecked();

    await user.click(optional);

    expect(updateNotificationPreference).toHaveBeenCalledWith('ASSIGNMENT', { emailEnabled: false });
    await waitFor(() => expect(screen.getByLabelText(/^affectation d’une demande$/i)).not.toBeChecked());
  });

  it('le partage obligatoire/facultatif vient du serveur, jamais d’une liste écrite ici', async () => {
    // Le même type renvoyé comme obligatoire doit s'afficher verrouillé, sans que rien
    // dans le composant ne connaisse la liste d'ADR-12 (MandatoryNotificationRule).
    listNotificationPreferences.mockResolvedValue([
      { notificationType: 'ASSIGNMENT', emailEnabled: true, mandatory: true },
    ]);

    render(<NotificationPreferences />);

    expect(await screen.findByLabelText(/affectation d’une demande \(obligatoire\)/i)).toBeDisabled();
    expect(screen.getByText('Obligatoires')).toBeInTheDocument();
    expect(screen.queryByText('Facultatives')).not.toBeInTheDocument();
  });
});
