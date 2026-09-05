import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ActionBar } from './ActionBar';
import type { WorkflowAction } from '../api/types';

// CLAUDE.md - "Rendre un bouton d'action depuis le rôle côté React plutôt que depuis
// availableActions[]" est explicitement listé comme ce qu'il ne faut jamais faire :
// ActionBar ne doit produire un bouton QUE pour les actions listées dans `actions`, jamais
// pour une action absente de cette liste, quelle que soit la raison métier de son absence.
const ALL_ACTIONS: WorkflowAction[] = ['VALIDATE', 'REJECT', 'RETURN', 'ASSIGN', 'REQUEST_INFO', 'CLOSE'];

describe('ActionBar', () => {
  it('ne rend un bouton que pour chaque action listée dans availableActions, aucune autre', () => {
    const onExecute = vi.fn().mockResolvedValue(undefined);
    const granted: WorkflowAction[] = ['VALIDATE', 'RETURN'];
    render(<ActionBar actions={granted} onExecute={onExecute} />);

    for (const action of granted) {
      expect(screen.getByRole('button', { name: new RegExp(actionButtonName(action), 'i') })).toBeInTheDocument();
    }
    const forbidden = ALL_ACTIONS.filter((action) => !granted.includes(action));
    for (const action of forbidden) {
      expect(screen.queryByRole('button', { name: new RegExp(actionButtonName(action), 'i') })).not.toBeInTheDocument();
    }
  });

  it("affiche un message et aucun bouton quand availableActions est vide", () => {
    render(<ActionBar actions={[]} onExecute={vi.fn()} />);
    expect(screen.getByText(/aucune action possible/i)).toBeInTheDocument();
    expect(screen.queryByRole('button')).not.toBeInTheDocument();
  });

  it('RG-05 - exige un commentaire avant de confirmer un REJECT', async () => {
    const user = userEvent.setup();
    const onExecute = vi.fn().mockResolvedValue(undefined);
    render(<ActionBar actions={['REJECT']} onExecute={onExecute} />);

    await user.click(screen.getByRole('button', { name: /rejeter/i }));
    const commentField = screen.getByLabelText(/commentaire/i);
    expect(commentField).toBeRequired();

    await user.type(commentField, 'Motif du rejet');
    await user.click(screen.getByRole('button', { name: /confirmer/i }));

    expect(onExecute).toHaveBeenCalledWith(
      expect.objectContaining({ action: 'REJECT', comment: 'Motif du rejet' }),
    );
  });

  it('§6.4 - exige un motif de clôture avant de confirmer un CLOSE', async () => {
    const user = userEvent.setup();
    const onExecute = vi.fn().mockResolvedValue(undefined);
    render(<ActionBar actions={['CLOSE']} onExecute={onExecute} />);

    await user.click(screen.getByRole('button', { name: /clôturer/i }));
    expect(screen.getByLabelText(/motif de clôture/i)).toBeRequired();
  });

  it('§6.4 - le niveau de satisfaction reste facultatif mais est transmis quand renseigné', async () => {
    const user = userEvent.setup();
    const onExecute = vi.fn().mockResolvedValue(undefined);
    render(<ActionBar actions={['CLOSE']} onExecute={onExecute} />);

    await user.click(screen.getByRole('button', { name: /clôturer/i }));
    expect(screen.getByLabelText(/niveau de satisfaction/i)).not.toBeRequired();

    await user.type(screen.getByLabelText(/motif de clôture/i), 'Résolu');
    await user.selectOptions(screen.getByLabelText(/niveau de satisfaction/i), '4');
    await user.click(screen.getByRole('button', { name: /confirmer/i }));

    expect(onExecute).toHaveBeenCalledWith(expect.objectContaining({ action: 'CLOSE', satisfactionRating: 4 }));
  });

  it("§6.6 - l'affectation automatique masque le champ agent précis et transmet autoAssign", async () => {
    const user = userEvent.setup();
    const onExecute = vi.fn().mockResolvedValue(undefined);
    render(<ActionBar actions={['ASSIGN']} onExecute={onExecute} />);

    await user.click(screen.getByRole('button', { name: /affecter/i }));
    await user.click(screen.getByLabelText(/affectation automatique/i));
    expect(screen.queryByLabelText(/identifiant de l'agent/i)).not.toBeInTheDocument();

    await user.type(screen.getByLabelText(/identifiant de l'équipe/i), '7');
    await user.click(screen.getByRole('button', { name: /confirmer/i }));

    expect(onExecute).toHaveBeenCalledWith(
      expect.objectContaining({ action: 'ASSIGN', assignedTeamId: 7, autoAssign: true, assignedUserId: null }),
    );
  });
});

function actionButtonName(action: WorkflowAction): string {
  const labels: Record<WorkflowAction, string> = {
    VALIDATE: 'Valider',
    REJECT: 'Rejeter',
    RETURN: 'Retourner',
    ASSIGN: 'Affecter',
    REQUEST_INFO: 'Demander un complément',
    CLOSE: 'Clôturer',
  };
  return labels[action];
}
