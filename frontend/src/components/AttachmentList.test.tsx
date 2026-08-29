import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AttachmentList } from './AttachmentList';
import * as attachmentsApi from '../api/attachments';
import type { AttachmentResponse } from '../api/types';

vi.mock('../api/attachments');

function attachment(overrides: Partial<AttachmentResponse> = {}): AttachmentResponse {
  return {
    id: 1,
    originalFilename: 'justificatif.pdf',
    contentType: 'application/pdf',
    sizeBytes: 2048,
    uploadedById: 1,
    uploadedByName: 'Amina Idrissi',
    uploadedAt: '2026-08-01T10:00:00Z',
    ...overrides,
  };
}

describe('AttachmentList', () => {
  beforeEach(() => {
    vi.mocked(attachmentsApi.listAttachments).mockResolvedValue([]);
    vi.mocked(attachmentsApi.uploadAttachment).mockResolvedValue(attachment());
    vi.mocked(attachmentsApi.attachmentDownloadPath).mockImplementation(
      (requestId, attachmentId) => `/api/v1/requests/${requestId}/attachments/${attachmentId}`,
    );
  });

  it('RG-09 - affiche les pièces jointes existantes avec un lien de téléchargement direct, jamais une URL construite ailleurs', async () => {
    vi.mocked(attachmentsApi.listAttachments).mockResolvedValue([attachment()]);

    render(<AttachmentList requestId={7} />);

    const link = await screen.findByRole('link', { name: /justificatif\.pdf/i });
    expect(link).toHaveAttribute('href', '/api/v1/requests/7/attachments/1');
    expect(screen.getByText(/amina idrissi/i)).toBeInTheDocument();
  });

  it("affiche un message quand aucune pièce jointe n'existe encore", async () => {
    render(<AttachmentList requestId={7} />);

    expect(await screen.findByText(/aucune pièce jointe/i)).toBeInTheDocument();
  });

  it('RG-09 - dépose un fichier sélectionné et recharge la liste', async () => {
    const user = userEvent.setup();
    render(<AttachmentList requestId={7} />);
    await waitFor(() => expect(attachmentsApi.listAttachments).toHaveBeenCalledWith(7));

    const file = new File(['contenu'], 'rapport.pdf', { type: 'application/pdf' });
    await user.upload(screen.getByLabelText(/ajouter une pièce jointe/i), file);
    await user.click(screen.getByRole('button', { name: /déposer/i }));

    await waitFor(() => expect(attachmentsApi.uploadAttachment).toHaveBeenCalledWith(7, file));
    expect(attachmentsApi.listAttachments).toHaveBeenCalledTimes(2);
  });
});
