import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import DateField from './DateField';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('DateField', () => {
  it('renders a labelled native date input seeded with the ISO value', () => {
    renderWithProviders(
      <DateField id="dob" label="Date of birth" value="1990-01-15" error={undefined} onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    const input = screen.getByLabelText('Date of birth');
    expect(input).toHaveAttribute('type', 'date');
    expect(input).toHaveValue('1990-01-15');
  });

  it('reports the picked date as an ISO string through onChange', async () => {
    const onChange = vi.fn();
    renderWithProviders(
      <DateField id="dob" label="Date of birth" value="" error={undefined} onChange={onChange} />,
      NO_PROVIDERS,
    );

    await userEvent.type(screen.getByLabelText('Date of birth'), '2020-06-01');

    expect(onChange).toHaveBeenLastCalledWith('2020-06-01');
  });

  it('shows an inline error and links it to the input for assistive tech', () => {
    renderWithProviders(
      <DateField id="dob" label="Date of birth" value="" error="Date of birth is required" onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    const input = screen.getByLabelText('Date of birth');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAttribute('aria-describedby', 'dob-error');
    expect(screen.getByRole('alert')).toHaveTextContent('Date of birth is required');
  });

  it('folds the optional marker into the accessible name rather than leaving it loose text', () => {
    renderWithProviders(
      <DateField id="end" label="End date" value="" error={undefined} optional onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    expect(screen.getByLabelText('End date (optional)')).toBeInTheDocument();
  });
});
