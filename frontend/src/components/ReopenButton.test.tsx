import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { ReopenButton } from './ReopenButton';

describe('ReopenButton', () => {
  it('RG-08 - calls onReopen when clicked', async () => {
    const user = userEvent.setup();
    const onReopen = vi.fn().mockResolvedValue(undefined);
    render(<ReopenButton onReopen={onReopen} />);

    await user.click(screen.getByRole('button', { name: /rouvrir/i }));

    expect(onReopen).toHaveBeenCalledTimes(1);
  });

  it('disables itself while the reopen call is in flight', async () => {
    const user = userEvent.setup();
    let resolvePromise: () => void = () => {};
    const onReopen = vi.fn(() => new Promise<void>((resolve) => { resolvePromise = resolve; }));
    render(<ReopenButton onReopen={onReopen} />);

    await user.click(screen.getByRole('button', { name: /rouvrir/i }));
    expect(screen.getByRole('button', { name: /rouvrir/i })).toBeDisabled();

    resolvePromise();
  });
});
