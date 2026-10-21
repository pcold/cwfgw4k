import { describe, expect, it, vi } from 'vitest';
import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { TAP_TARGET_CLASS } from './formControls';
import { LiveOverlayCheckbox } from './LiveOverlayToggle';

const NO_PROVIDERS = { withLeagueSeasonProvider: false };

describe('LiveOverlayCheckbox', () => {
  it('reports a tap anywhere on its label as the toggle', async () => {
    const toggleLive = vi.fn();
    const user = userEvent.setup();
    renderWithProviders(
      <LiveOverlayCheckbox id="live" state={{ eligible: true, liveChecked: false, toggleLive }} />,
      NO_PROVIDERS,
    );

    await user.click(screen.getByText('Live overlay'));

    expect(toggleLive).toHaveBeenCalledWith(true);
  });

  // The 16px box is too small to hit; the label around it is the tap target.
  it('gives the label around the checkbox the tap-target height', () => {
    renderWithProviders(
      <LiveOverlayCheckbox id="live" state={{ eligible: true, liveChecked: false, toggleLive: vi.fn() }} />,
      NO_PROVIDERS,
    );

    expect(screen.getByRole('checkbox', { name: 'Live overlay' }).closest('label')).toHaveClass(TAP_TARGET_CLASS);
  });
});
