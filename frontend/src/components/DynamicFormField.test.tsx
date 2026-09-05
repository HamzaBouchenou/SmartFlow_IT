import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { DynamicFormField } from './DynamicFormField';
import type { FormFieldResponse } from '../api/types';

function field(overrides: Partial<FormFieldResponse>): FormFieldResponse {
  return {
    id: 1,
    code: 'urgency',
    label: 'Urgence',
    fieldType: 'TEXT',
    required: false,
    displayOrder: 1,
    helpText: null,
    visibleWhenFieldCode: null,
    visibleWhenValue: null,
    options: [],
    ...overrides,
  };
}

describe('DynamicFormField', () => {
  it('§6.3 - marque un champ obligatoire et le signale dans le libellé', () => {
    render(<DynamicFormField field={field({ required: true })} value="" onChange={vi.fn()} />);
    expect(screen.getByLabelText(/urgence/i)).toBeRequired();
    expect(screen.getByText('*')).toBeInTheDocument();
  });

  it('§6.3 - un champ LIST propose ses options triées par displayOrder', () => {
    render(
      <DynamicFormField
        field={field({
          fieldType: 'LIST',
          options: [
            { value: 'high', label: 'Haute', displayOrder: 2 },
            { value: 'low', label: 'Basse', displayOrder: 1 },
          ],
        })}
        value=""
        onChange={vi.fn()}
      />,
    );
    const options = screen.getAllByRole('option').map((option) => option.textContent);
    expect(options).toEqual(['— choisir —', 'Basse', 'Haute']);
  });

  it('un champ CHECKBOX transmet la valeur sous forme de chaîne "true"/"false"', async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    render(<DynamicFormField field={field({ fieldType: 'CHECKBOX', code: 'confirm' })} value="false" onChange={onChange} />);

    await user.click(screen.getByLabelText(/urgence/i));
    expect(onChange).toHaveBeenCalledWith('confirm', 'true');
  });

  it('RG-09 - un champ FILE est désactivé (pièces jointes pas encore disponibles)', () => {
    render(<DynamicFormField field={field({ fieldType: 'FILE' })} value="" onChange={vi.fn()} />);
    expect(screen.getByLabelText(/urgence/i)).toBeDisabled();
  });

  it('affiche le message d\'erreur transmis pour ce champ', () => {
    render(<DynamicFormField field={field({})} value="" onChange={vi.fn()} error="champ invalide" />);
    expect(screen.getByText('champ invalide')).toBeInTheDocument();
  });

  it('§8 (Accessibilité) - relie le champ à son message d\'erreur via aria-describedby/aria-invalid, pas seulement par la couleur', () => {
    render(<DynamicFormField field={field({})} value="" onChange={vi.fn()} error="champ invalide" />);
    const input = screen.getByLabelText(/urgence/i);
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input.getAttribute('aria-describedby')).toContain('field-urgency-error');
    expect(screen.getByText('champ invalide')).toHaveAttribute('id', 'field-urgency-error');
  });

  it('§8 (Accessibilité) - relie le champ à son texte d\'aide via aria-describedby quand aucune erreur n\'est présente', () => {
    render(<DynamicFormField field={field({ helpText: 'Précisez le contexte.' })} value="" onChange={vi.fn()} />);
    const input = screen.getByLabelText(/urgence/i);
    expect(input).not.toHaveAttribute('aria-invalid');
    expect(input.getAttribute('aria-describedby')).toBe('field-urgency-help');
  });
});
