import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import DateField from './DateField';
import SelectField from './SelectField';
import TextField from './TextField';
import { TAP_TARGET_CLASS } from './formControls';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('blur wiring', () => {
  it('leaves a field with nothing to reveal free to omit its blur handler', async () => {
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <TextField id="reason" label="Termination reason" value="" error={undefined} onChange={vi.fn()} />
        <DateField id="effective" label="Effective date" value="" error={undefined} onChange={vi.fn()} />
        <SelectField
          id="carrier"
          label="Carrier"
          value=""
          error={undefined}
          placeholder="Select a carrier"
          options={[{ value: 'anthem', label: 'Anthem' }]}
          onChange={vi.fn()}
        />
      </>,
      NO_PROVIDERS,
    );

    await user.click(screen.getByLabelText('Termination reason'));
    await user.tab();
    await user.tab();
    await user.tab();

    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByLabelText('Termination reason')).toHaveAttribute('aria-invalid', 'false');
    expect(screen.getByLabelText('Effective date')).toHaveAttribute('aria-invalid', 'false');
    expect(screen.getByLabelText('Carrier')).toHaveAttribute('aria-invalid', 'false');
  });
});

describe('TAP_TARGET_CLASS', () => {
  // jsdom has no layout, so the class is the only place the 44px a phone thumb needs can be held.
  it('is 44px tall, and every field control carries it', () => {
    renderWithProviders(
      <>
        <TextField id="name" label="Name" value="" error={undefined} onChange={vi.fn()} />
        <DateField id="dob" label="Date of birth" value="" error={undefined} onChange={vi.fn()} />
        <SelectField
          id="relationship"
          label="Relationship"
          value=""
          error={undefined}
          placeholder="Select a relationship"
          options={[{ value: 'spouse', label: 'Spouse' }]}
          onChange={vi.fn()}
        />
      </>,
      NO_PROVIDERS,
    );

    expect(TAP_TARGET_CLASS).toBe('min-h-11');
    for (const label of ['Name', 'Date of birth', 'Relationship']) {
      expect(screen.getByLabelText(label)).toHaveClass(TAP_TARGET_CLASS);
    }
  });
});
