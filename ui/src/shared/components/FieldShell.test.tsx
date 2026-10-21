import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import FieldShell from './FieldShell';
import TextField from './TextField';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('FieldShell', () => {
  it('labels the control the caller wires to its id, and names the error for it', () => {
    renderWithProviders(
      <FieldShell id="season" label="Season" error="Pick a season">
        <select id="season" aria-describedby="season-error">
          <option>2026</option>
        </select>
      </FieldShell>,
      NO_PROVIDERS,
    );

    expect(screen.getByLabelText('Season')).toHaveAccessibleDescription('Pick a season');
  });

  it('keeps a hidden label naming the control', () => {
    renderWithProviders(
      <FieldShell id="multiplier" label="Payout multiplier" error={undefined} hideLabel>
        <input id="multiplier" type="number" />
      </FieldShell>,
      NO_PROVIDERS,
    );

    expect(screen.getByLabelText('Payout multiplier')).toBeInTheDocument();
    expect(screen.getByText('Payout multiplier')).toHaveClass('sr-only');
  });

  it('sizes the box the label sits in, leaving the control to fill it', () => {
    renderWithProviders(
      <TextField id="name" label="Name" value="" error={undefined} width="w-full sm:w-72" onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    const input = screen.getByLabelText('Name');
    expect(input.parentElement).toHaveClass('w-full', 'sm:w-72');
    expect(input).not.toHaveClass('sm:w-72');
  });
});
