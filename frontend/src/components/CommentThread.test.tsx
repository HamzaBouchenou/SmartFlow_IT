import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CommentThread } from './CommentThread';
import * as commentsApi from '../api/comments';
import type { CommentResponse } from '../api/types';

vi.mock('../api/comments');

function comment(overrides: Partial<CommentResponse> = {}): CommentResponse {
  return {
    id: 1,
    authorId: 1,
    authorName: 'Amina Idrissi',
    body: 'Un commentaire existant.',
    createdAt: '2026-08-01T10:00:00Z',
    mentions: [],
    ...overrides,
  };
}

describe('CommentThread', () => {
  beforeEach(() => {
    vi.mocked(commentsApi.listComments).mockResolvedValue([]);
    vi.mocked(commentsApi.addComment).mockResolvedValue(comment());
  });

  it('§6.4 - affiche les commentaires existants', async () => {
    vi.mocked(commentsApi.listComments).mockResolvedValue([comment({ body: 'Bonjour, un souci ?' })]);

    render(<CommentThread requestId={42} />);

    expect(await screen.findByText('Bonjour, un souci ?')).toBeInTheDocument();
    expect(screen.getByText('Amina Idrissi')).toBeInTheDocument();
  });

  it('§6.4/ADR-24 - affiche les personnes réellement mentionnées, telles que le serveur les a retenues', async () => {
    vi.mocked(commentsApi.listComments).mockResolvedValue([
      comment({ body: 'Un avis @nawal@x.local ?', mentions: [{ userId: 9, name: 'Nawal Manager' }] }),
    ]);

    render(<CommentThread requestId={42} />);

    expect(await screen.findByText(/Mentionne : Nawal Manager/)).toBeInTheDocument();
  });

  it("ADR-24 - une adresse que le serveur n'a pas retenue n'est jamais affichée comme mentionnée", async () => {
    // Le texte nomme quelqu'un, mais le serveur ne l'a pas retenu (inconnu ou hors
    // périmètre) : l'écran suit `mentions`, jamais ce que le corps du message contient.
    vi.mocked(commentsApi.listComments).mockResolvedValue([
      comment({ body: 'Un avis @inconnu@x.local ?', mentions: [] }),
    ]);

    render(<CommentThread requestId={42} />);

    await screen.findByText('Un avis @inconnu@x.local ?');
    expect(screen.queryByText(/Mentionne :/)).not.toBeInTheDocument();
  });

  it("affiche un message quand aucun commentaire n'existe encore", async () => {
    render(<CommentThread requestId={42} />);

    expect(await screen.findByText(/aucun commentaire/i)).toBeInTheDocument();
  });

  it('§6.4 - publie un nouveau commentaire et recharge la liste', async () => {
    const user = userEvent.setup();
    render(<CommentThread requestId={42} />);
    await waitFor(() => expect(commentsApi.listComments).toHaveBeenCalledWith(42));

    await user.type(screen.getByLabelText(/ajouter un commentaire/i), 'Nouvelle précision');
    await user.click(screen.getByRole('button', { name: /publier/i }));

    await waitFor(() => expect(commentsApi.addComment).toHaveBeenCalledWith(42, { body: 'Nouvelle précision' }));
    expect(commentsApi.listComments).toHaveBeenCalledTimes(2);
  });

  it('le bouton Publier reste désactivé tant que le champ est vide', async () => {
    render(<CommentThread requestId={42} />);
    await screen.findByText(/aucun commentaire/i);

    expect(screen.getByRole('button', { name: /publier/i })).toBeDisabled();
  });
});
