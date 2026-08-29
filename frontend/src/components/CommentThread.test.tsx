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
