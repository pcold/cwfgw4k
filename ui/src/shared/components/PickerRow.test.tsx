import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import PickerRow from './PickerRow';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

const OPTIONS = [
  { value: 'all', label: 'All Tournaments' },
  { value: 't-1', label: 'The Masters' },
];

describe('PickerRow', () => {
  it('names the select by its label and reports the picked option by its value', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <PickerRow id="through" label="Through" value="all" options={OPTIONS} onChange={onChange} />,
      NO_PROVIDERS,
    );

    const select = screen.getByRole('combobox', { name: 'Through' });
    expect(select).toHaveValue('all');
    await user.selectOptions(select, 'The Masters');

    expect(onChange).toHaveBeenCalledWith('t-1');
  });

  it('puts the trailing controls in the row after the select', () => {
    renderWithProviders(
      <PickerRow id="through" label="Through" value="all" options={OPTIONS} onChange={vi.fn()}>
        <button type="button">Download PDF</button>
      </PickerRow>,
      NO_PROVIDERS,
    );

    const select = screen.getByRole('combobox', { name: 'Through' });
    expect(select.nextElementSibling).toBe(screen.getByRole('button', { name: 'Download PDF' }));
  });

  // jsdom has no layout, so the classes are the only place a phone's 375px width can be held.
  it('wraps the row on a phone, lets the select shrink to the line, and sizes it at 16px', () => {
    renderWithProviders(
      <PickerRow id="through" label="Through" value="all" options={OPTIONS} onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    const select = screen.getByRole('combobox', { name: 'Through' });
    expect(select.parentElement).toHaveClass('flex', 'flex-wrap');
    expect(select).toHaveClass('w-full', 'min-w-0', 'truncate', 'sm:w-auto', 'text-base');
    expect(select).not.toHaveClass('text-sm');
  });
});
