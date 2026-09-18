import { afterEach, describe, expect, it, vi } from 'vitest';
import { act, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { NotificationBell } from './NotificationBell';
import * as notificationsApi from '../api/notifications';
import { ApiError } from '../api/client';

vi.mock('../api/notifications');

describe('NotificationBell', () => {
  it('§6.8 - affiche le nombre de notifications non lues', async () => {
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue({ count: 3 });

    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    );

    expect(await screen.findByText('3')).toBeInTheDocument();
  });

  it("n'affiche aucun badge quand il n'y a aucune notification non lue", async () => {
    vi.mocked(notificationsApi.unreadCount).mockResolvedValue({ count: 0 });

    render(
      <MemoryRouter>
        <NotificationBell />
      </MemoryRouter>,
    );

    await screen.findByText(/notifications/i);
    expect(screen.queryByText('0')).not.toBeInTheDocument();
  });

  it("ADR-22 - cesse d'interroger le serveur quand la session est perdue, au lieu de sonder une route protégée jusqu'à la fermeture de l'onglet", async () => {
    vi.useFakeTimers();
    try {
      vi.mocked(notificationsApi.unreadCount).mockRejectedValue(
        new ApiError(401, { code: 'SESSION_EXPIRED', message: 'Votre session a expiré.' }),
      );

      render(
        <MemoryRouter>
          <NotificationBell />
        </MemoryRouter>,
      );

      // Le premier appel, celui qui accompagne l'affichage, a bien eu lieu et a échoué.
      await act(async () => {});
      expect(notificationsApi.unreadCount).toHaveBeenCalledTimes(1);

      // Plusieurs périodes de sondage plus tard : toujours un seul appel. Le drapeau
      // `cancelled` seul ne suffisait pas ici - il taisait le résultat mais laissait le
      // minuteur repartir toutes les 30 secondes.
      await act(async () => {
        vi.advanceTimersByTime(5 * 30_000);
      });
      expect(notificationsApi.unreadCount).toHaveBeenCalledTimes(1);
    } finally {
      vi.useRealTimers();
    }
  });
});

afterEach(() => {
  vi.clearAllMocks();
});