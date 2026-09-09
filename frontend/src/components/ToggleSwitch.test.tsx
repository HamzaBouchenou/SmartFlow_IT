import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ToggleSwitch } from './ToggleSwitch';

describe('ToggleSwitch', () => {
  it('reste une case à cocher native, focalisable et annoncée par son libellé (§8)', async () => {
    const user = userEvent.setup();
    render(<ToggleSwitch label="Clôture d’une demande" checked={false} onChange={vi.fn()} />);

    const toggle = screen.getByRole('checkbox', { name: 'Clôture d’une demande' });
    await user.tab();
    expect(toggle).toHaveFocus();
  });

  it('transmet le nouvel état, et plus rien une fois désactivé', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const { rerender } = render(<ToggleSwitch label="Alerte" checked={false} onChange={onChange} />);

    await user.click(screen.getByRole('checkbox', { name: 'Alerte' }));
    expect(onChange).toHaveBeenCalledWith(true);

    onChange.mockClear();
    rerender(<ToggleSwitch label="Alerte" checked disabled onChange={onChange} />);
    await user.click(screen.getByRole('checkbox', { name: 'Alerte' }));
    expect(onChange).not.toHaveBeenCalled();
  });
});
