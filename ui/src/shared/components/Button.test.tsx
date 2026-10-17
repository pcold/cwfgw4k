import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import Button from './Button';
import { TAP_TARGET_CLASS } from './formFields';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('Button', () => {
  it.each([
    ['primary', ['bg-primary', 'hover:bg-primary-hover', 'disabled:bg-disabled', 'text-on-primary']],
    ['secondary', ['bg-secondary', 'hover:bg-secondary-hover', 'disabled:bg-disabled', 'text-on-fill']],
    [
      'danger',
      ['bg-semantic-danger-fill', 'hover:bg-semantic-danger-fill-hover', 'disabled:bg-disabled', 'text-on-fill'],
    ],
  ] as const)('fills a %s button with its tone, hovered and disabled', (tone, classes) => {
    renderWithProviders(<Button tone={tone} size="regular">Save</Button>, NO_PROVIDERS);

    expect(screen.getByRole('button', { name: 'Save' })).toHaveClass(...classes, 'rounded', 'font-medium');
  });

  it.each([
    ['regular', ['px-4', 'py-2', 'text-sm'], ['px-3', 'py-1', 'text-xs']],
    ['compact', ['px-3', 'py-1', 'text-xs'], ['px-4', 'py-2', 'text-sm']],
  ] as const)('pads a %s button to its size and keeps it a 44px tap target', (size, classes, otherSize) => {
    renderWithProviders(<Button tone="primary" size={size}>Save</Button>, NO_PROVIDERS);

    const button = screen.getByRole('button', { name: 'Save' });
    expect(button).toHaveClass(...classes, TAP_TARGET_CLASS);
    expect(button).not.toHaveClass(...otherSize);
  });

  it('is a plain button unless asked to submit, so it never submits a form it sits in by accident', () => {
    renderWithProviders(
      <>
        <Button tone="secondary" size="regular">Cancel</Button>
        <Button tone="primary" size="regular" type="submit">Log in</Button>
      </>,
      NO_PROVIDERS,
    );

    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveAttribute('type', 'button');
    expect(screen.getByRole('button', { name: 'Log in' })).toHaveAttribute('type', 'submit');
  });

  it('adds its layout class beside the tone and size', () => {
    renderWithProviders(<Button tone="danger" size="regular" layout="whitespace-nowrap">Delete</Button>, NO_PROVIDERS);

    expect(screen.getByRole('button', { name: 'Delete' })).toHaveClass('whitespace-nowrap', 'bg-semantic-danger-fill');
  });

  it('calls its handler on a click', async () => {
    const onClick = vi.fn();
    renderWithProviders(<Button tone="primary" size="compact" onClick={onClick}>Finalize</Button>, NO_PROVIDERS);

    await userEvent.click(screen.getByRole('button', { name: 'Finalize' }));

    expect(onClick).toHaveBeenCalledTimes(1);
  });

  it('ignores a click while disabled', async () => {
    const onClick = vi.fn();
    renderWithProviders(
      <Button tone="primary" size="compact" onClick={onClick} disabled>Finalize</Button>,
      NO_PROVIDERS,
    );

    await userEvent.click(screen.getByRole('button', { name: 'Finalize' }));

    expect(onClick).not.toHaveBeenCalled();
  });

  it('points assistive tech at the text that explains it', () => {
    renderWithProviders(
      <>
        <Button tone="primary" size="regular" disabled aria-describedby="why">Confirm</Button>
        <span id="why">Resolve every pick first.</span>
      </>,
      NO_PROVIDERS,
    );

    expect(screen.getByRole('button', { name: 'Confirm' })).toHaveAccessibleDescription('Resolve every pick first.');
  });
});
