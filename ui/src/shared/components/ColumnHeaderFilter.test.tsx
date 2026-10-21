import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import ColumnHeaderFilter from './ColumnHeaderFilter';
import { TAP_TARGET_CLASS } from './formControls';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

const OPTIONS = [
  { value: '', label: 'Team' },
  { value: 'Aces', label: 'Aces' },
];

describe('ColumnHeaderFilter', () => {
  it('reports the picked option by its value', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <ColumnHeaderFilter ariaLabel="Filter by team" value="" onChange={onChange} options={OPTIONS} />,
      NO_PROVIDERS,
    );

    await user.selectOptions(screen.getByRole('combobox', { name: 'Filter by team' }), 'Aces');

    expect(onChange).toHaveBeenCalledWith('Aces');
  });

  it('sizes the filter at 16px with a 44px tap target, and keeps the header borderless', () => {
    renderWithProviders(
      <ColumnHeaderFilter ariaLabel="Filter by team" value="" onChange={vi.fn()} options={OPTIONS} />,
      NO_PROVIDERS,
    );

    const select = screen.getByRole('combobox', { name: 'Filter by team' });
    expect(select).toHaveClass('text-base', TAP_TARGET_CLASS, 'bg-transparent');
    expect(select).not.toHaveClass('text-xs');
    expect(select).not.toHaveClass('border');
  });

  it('restates uppercase, which a browser resets on a select inside the column head', () => {
    renderWithProviders(
      <ColumnHeaderFilter ariaLabel="Filter by team" value="" onChange={vi.fn()} options={OPTIONS} />,
      NO_PROVIDERS,
    );

    expect(screen.getByRole('combobox', { name: 'Filter by team' })).toHaveClass('uppercase');
  });
});
