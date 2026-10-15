import { describe, it, expect, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { DateField, FieldShell, SelectField, TextField } from './formFields';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('TextField', () => {
  it('shows an inline error and links it to the input for assistive tech', () => {
    renderWithProviders(
      <TextField id="name" label="Name" value="" error="Name is required" onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    const input = screen.getByLabelText('Name');
    expect(input).toHaveAttribute('aria-invalid', 'true');
    expect(input).toHaveAccessibleDescription('Name is required');
    expect(screen.getByRole('alert')).toHaveTextContent('Name is required');
  });

  it('leaves the input unmarked when there is no error', () => {
    renderWithProviders(
      <TextField id="name" label="Name" value="" error={undefined} onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    const input = screen.getByLabelText('Name');
    expect(input).toHaveAttribute('aria-invalid', 'false');
    expect(input).not.toHaveAttribute('aria-describedby');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('reports each keystroke as the new value through onChange', async () => {
    const onChange = vi.fn();
    renderWithProviders(
      <TextField id="name" label="Name" value="" error={undefined} onChange={onChange} />,
      NO_PROVIDERS,
    );

    await userEvent.type(screen.getByLabelText('Name'), 'a');

    expect(onChange).toHaveBeenLastCalledWith('a');
  });

  it('masks a password field', () => {
    renderWithProviders(
      <TextField id="pw" label="Password" type="password" value="hunter2" error={undefined} onChange={vi.fn()} />,
      NO_PROVIDERS,
    );

    expect(screen.getByLabelText('Password')).toHaveAttribute('type', 'password');
  });

  it('takes focus on mount only when asked', () => {
    renderWithProviders(
      <>
        <TextField id="user" label="Username" value="" error={undefined} initialFocus onChange={vi.fn()} />
        <TextField id="pw" label="Password" value="" error={undefined} onChange={vi.fn()} />
      </>,
      NO_PROVIDERS,
    );

    expect(screen.getByLabelText('Username')).toHaveFocus();
  });

  it('takes no keystrokes while disabled', async () => {
    const onChange = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <TextField id="name" label="Name" value="" error={undefined} disabled onChange={onChange} />,
      NO_PROVIDERS,
    );

    await user.type(screen.getByLabelText('Name'), 'x');

    expect(screen.getByLabelText('Name')).toBeDisabled();
    expect(onChange).not.toHaveBeenCalled();
  });
});

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
