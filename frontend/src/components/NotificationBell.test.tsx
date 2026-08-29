import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router-dom';
import { NotificationBell } from './NotificationBell';
import * as notificationsApi from '../api/notifications';

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
});
