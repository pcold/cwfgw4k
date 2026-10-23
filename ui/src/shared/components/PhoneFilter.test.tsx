import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import PhoneFilter from './PhoneFilter';
import { FIELD_CLASS } from './formControls';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

const OPTIONS = [
  { value: '', label: 'All teams' },
  { value: 'Aces', label: 'Aces' },
];

describe('PhoneFilter', () => {
  it('reports the picked option by its value, and lets the all option be picked back', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(<PhoneFilter label="Team" value="Aces" options={OPTIONS} onChange={onChange} />, NO_PROVIDERS);

    await user.selectOptions(screen.getByRole('combobox', { name: 'Team' }), 'All teams');

    expect(onChange).toHaveBeenCalledWith('');
  });

  it('sets the select as a form field under a screen-reader-only label', () => {
    renderWithProviders(<PhoneFilter label="Team" value="" options={OPTIONS} onChange={vi.fn()} />, NO_PROVIDERS);

    expect(screen.getByRole('combobox', { name: 'Team' })).toHaveClass(...FIELD_CLASS.split(' '));
    expect(screen.getByText('Team')).toHaveClass('sr-only');
  });
});
