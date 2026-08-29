import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AiAssistPanel } from './AiAssistPanel';
import * as aiApi from '../api/ai';
import type { AiAnalysisResponse } from '../api/types';

vi.mock('../api/ai');

function analysis(overrides: Partial<AiAnalysisResponse> = {}): AiAnalysisResponse {
  return {
    id: 1,
    requestId: 7,
    analysisType: 'CLASSIFICATION',
    rawResult: '{"category":"MATERIEL","priority":"HIGH"}',
    confidenceScore: 0.87,
    suggestedValue: '{"category":"MATERIEL","priority":"HIGH"}',
    acceptedValue: null,
    validatedById: null,
    validatedByName: null,
    validatedAt: null,
    createdAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

describe('AiAssistPanel', () => {
  beforeEach(() => {
    vi.mocked(aiApi.listAnalyses).mockResolvedValue([]);
    vi.mocked(aiApi.analyze).mockResolvedValue(analysis());
    vi.mocked(aiApi.validateAnalysis).mockResolvedValue(analysis({ acceptedValue: 'x', validatedAt: '2026-08-01T11:00:00Z' }));
  });

  it("§12.1 - affiche la suggestion de classification décodée (catégorie et priorité), jamais le JSON brut", async () => {
    vi.mocked(aiApi.listAnalyses).mockResolvedValue([analysis()]);

    render(<AiAssistPanel requestId={7} />);

    expect(await screen.findByText(/Catégorie : MATERIEL · Priorité : HIGH/)).toBeInTheDocument();
    expect(screen.getByText(/confiance 87 %/)).toBeInTheDocument();
  });

  it('RG-10 - une analyse déjà validée affiche qui l\'a validée, sans proposer de nouveau formulaire', async () => {
    vi.mocked(aiApi.listAnalyses).mockResolvedValue([
      analysis({ acceptedValue: '{"category":"MATERIEL","priority":"HIGH"}', validatedById: 3, validatedByName: 'Sara Bennis', validatedAt: '2026-08-01T11:00:00Z' }),
    ]);

    render(<AiAssistPanel requestId={7} />);

    expect(await screen.findByText(/validée par sara bennis/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /valider cette suggestion/i })).not.toBeInTheDocument();
  });

  it('déclenche une nouvelle analyse CLASSIFICATION et recharge la liste', async () => {
    const user = userEvent.setup();
    render(<AiAssistPanel requestId={7} />);
    await waitFor(() => expect(aiApi.listAnalyses).toHaveBeenCalledWith(7));

    await user.click(screen.getByRole('button', { name: /suggérer catégorie et priorité/i }));

    await waitFor(() => expect(aiApi.analyze).toHaveBeenCalledWith(7, { analysisType: 'CLASSIFICATION' }));
    expect(aiApi.listAnalyses).toHaveBeenCalledTimes(2);
  });

  it("RG-10 - valider une suggestion appelle uniquement validateAnalysis, jamais un appel qui modifierait la demande", async () => {
    const user = userEvent.setup();
    vi.mocked(aiApi.listAnalyses).mockResolvedValue([analysis()]);
    render(<AiAssistPanel requestId={7} />);
    await screen.findByRole('button', { name: /valider cette suggestion/i });

    await user.click(screen.getByRole('button', { name: /valider cette suggestion/i }));

    await waitFor(() =>
      expect(aiApi.validateAnalysis).toHaveBeenCalledWith(1, { acceptedValue: '{"category":"MATERIEL","priority":"HIGH"}' }),
    );
  });
});
