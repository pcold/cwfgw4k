import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import SelectField from './SelectField';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('SelectField', () => {
  it('shows an inline error and links it to the select for assistive tech', () => {
    renderWithProviders(
      <SelectField
        id="relationship"
        label="Relationship"
        value=""
        error="Relationship is required"
        placeholder="Select a relationship"
        options={[{ value: 'spouse', label: 'Spouse' }]}
        onChange={vi.fn()}
        onBlur={vi.fn()}
      />,
      NO_PROVIDERS,
    );

    const select = screen.getByLabelText('Relationship');
    expect(select).toHaveAttribute('aria-invalid', 'true');
    expect(select).toHaveAttribute('aria-describedby', 'relationship-error');
    expect(screen.getByRole('alert')).toHaveTextContent('Relationship is required');
  });

  // What makes the constraint bite is the placeholder's empty value, not its `disabled`
  // attribute — a placeholder carrying a real value would silently satisfy `required`.
  it('counts a required select as missing while the placeholder is the selection', () => {
    renderWithProviders(
      <SelectField
        id="relationship"
        label="Relationship"
        value=""
        error={undefined}
        required
        placeholder="Select a relationship"
        options={[{ value: 'spouse', label: 'Spouse' }]}
        onChange={vi.fn()}
        onBlur={vi.fn()}
      />,
      NO_PROVIDERS,
    );

    const select = screen.getByLabelText<HTMLSelectElement>('Relationship');
    expect(select).toBeRequired();
    expect(select.validity.valueMissing).toBe(true);
  });

  it('sizes the select at 16px so iOS does not zoom the page into it', () => {
    renderWithProviders(
      <SelectField
        id="relationship"
        label="Relationship"
        value=""
        error={undefined}
        placeholder="Select a relationship"
        options={[{ value: 'spouse', label: 'Spouse' }]}
        onChange={vi.fn()}
      />,
      NO_PROVIDERS,
    );

    const select = screen.getByLabelText('Relationship');
    expect(select).toHaveClass('text-base');
    expect(select).not.toHaveClass('text-sm');
  });

  it('reports the picked option by its value', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <SelectField
        id="relationship"
        label="Relationship"
        value=""
        error={undefined}
        placeholder="Select a relationship"
        options={[{ value: 'spouse', label: 'Spouse' }]}
        onChange={onChange}
      />,
      NO_PROVIDERS,
    );

    await user.selectOptions(screen.getByLabelText('Relationship'), 'Spouse');

    expect(onChange).toHaveBeenCalledWith('spouse');
  });
});
