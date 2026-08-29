import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';
import { RequestTimeline } from './RequestTimeline';
import * as requestsApi from '../api/requests';
import type { RequestHistoryResponse } from '../api/types';

vi.mock('../api/requests');

function historyEntry(overrides: Partial<RequestHistoryResponse> = {}): RequestHistoryResponse {
  return {
    id: 1,
    action: 'ASSIGN',
    fromStepName: 'Qualification',
    toStepName: 'Validation',
    actorId: 7,
    actorName: 'Sara Bennis',
    comment: null,
    occurredAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

describe('RequestTimeline', () => {
  beforeEach(() => {
    vi.mocked(requestsApi.getHistory).mockResolvedValue([]);
  });

  it("§6.4 - affiche chaque ligne d'historique avec son action, ses étapes et son auteur", async () => {
    vi.mocked(requestsApi.getHistory).mockResolvedValue([historyEntry()]);

    render(<RequestTimeline requestId={42} />);

    expect(await screen.findByText(/affecter/i)).toBeInTheDocument();
    expect(screen.getByText('Sara Bennis', { exact: false })).toBeInTheDocument();
    expect(screen.getByText('Qualification → Validation')).toBeInTheDocument();
  });

  it("affiche un message quand l'historique est encore vide", async () => {
    render(<RequestTimeline requestId={42} />);

    expect(await screen.findByText(/aucune étape enregistrée/i)).toBeInTheDocument();
  });

  it('affiche le commentaire de la transition quand il existe', async () => {
    vi.mocked(requestsApi.getHistory).mockResolvedValue([historyEntry({ comment: 'Budget non disponible' })]);

    render(<RequestTimeline requestId={42} />);

    expect(await screen.findByText('Budget non disponible')).toBeInTheDocument();
  });
});
