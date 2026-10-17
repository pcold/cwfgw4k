import { beforeEach, describe, expect, it, vi } from 'vitest';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import type { api } from '@/shared/api/client';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { makeSeason } from '@/shared/test/fixtures';
import { ApiError } from '@/shared/api/client';
import type { SeasonRules } from '@/shared/api/types';
import CreateSeasonSection from './CreateSeasonSection';

const leaguesMock = vi.fn<typeof api.leagues>();
const defaultSeasonRulesMock = vi.fn<typeof api.defaultSeasonRules>();
const createSeasonMock = vi.fn<typeof api.createSeason>();

vi.mock('@/shared/api/client', () => ({
  api: {
    leagues: () => leaguesMock(),
    defaultSeasonRules: () => defaultSeasonRulesMock(),
    createSeason: (input: Parameters<typeof api.createSeason>[0]) => createSeasonMock(input),
  },
  ApiError: class ApiError extends Error {
    readonly status: number;
    constructor(status: number, message: string) {
      super(message);
      this.status = status;
    }
  },
}));

// Unlike the real defaults on purpose: a form that prefilled from anywhere but the server fails here.
const serverDefaults: SeasonRules = {
  payouts: [30, 20, 10],
  tieFloor: 2,
  sideBetRounds: [3, 4],
  sideBetAmount: 25,
};

describe('CreateSeasonSection', () => {
  beforeEach(() => {
    leaguesMock.mockReset();
    defaultSeasonRulesMock.mockReset();
    createSeasonMock.mockReset();
    defaultSeasonRulesMock.mockResolvedValue(serverDefaults);
    leaguesMock.mockResolvedValue([
      { id: 'lg-1', name: 'Alpha', createdAt: '2026-01-01T00:00:00Z' },
      { id: 'lg-2', name: 'Beta', createdAt: '2026-01-01T00:00:00Z' },
    ]);
  });

  it('submits the season with the rules the server serves as defaults', async () => {
    createSeasonMock.mockResolvedValue(makeSeason({ id: 'sn-5', seasonYear: 2030 }));

    const user = userEvent.setup();
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });

    // Wait for leagues to populate the select so leagueId is set.
    await waitFor(() => {
      expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
    });

    const yearInput = screen.getByLabelText<HTMLInputElement>(/Year/i);
    await user.clear(yearInput);
    await user.type(yearInput, '2030');
    await user.type(screen.getByLabelText(/Season Name/i), 'Spring');

    await user.click(screen.getByRole('button', { name: /Create Season/i }));

    await waitFor(() => {
      expect(createSeasonMock).toHaveBeenCalledTimes(1);
    });
    expect(createSeasonMock).toHaveBeenCalledWith({
      leagueId: 'lg-1',
      name: 'Spring',
      seasonYear: 2030,
      rules: serverDefaults,
    });
    expect(await screen.findByText(/Season "Spring" created/i)).toBeInTheDocument();
  });

  it('prefills the rule fields from the server defaults', async () => {
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });

    expect(await screen.findByLabelText(/Rounds/i)).toHaveValue('3,4');
    expect(screen.getByLabelText(/Tie Floor/i)).toHaveValue(2);
    expect(screen.getByLabelText(/Bet Amount/i)).toHaveValue(25);
  });

  it('lets the user add and remove payout places', async () => {
    const user = userEvent.setup();
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
    });

    expect(screen.getAllByLabelText(/^Payout \d+$/)).toHaveLength(3);

    await user.click(screen.getByRole('button', { name: /\+ Add place/i }));
    expect(screen.getAllByLabelText(/^Payout \d+$/)).toHaveLength(4);

    await user.click(screen.getByRole('button', { name: /- Remove last/i }));
    expect(screen.getAllByLabelText(/^Payout \d+$/)).toHaveLength(3);
  });

  it('holds the payout inputs and the form buttons to the tap-target height, the payouts at 16px', async () => {
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });

    for (const payout of await screen.findAllByLabelText(/^Payout \d+$/)) {
      expect(payout).toHaveClass('text-base', TAP_TARGET_CLASS);
    }
    for (const name of [/\+ Add place/i, /- Remove last/i, /^Create Season$/i]) {
      expect(screen.getByRole('button', { name })).toHaveClass(TAP_TARGET_CLASS);
    }
  });

  it('shows the reason the server refuses a season', async () => {
    createSeasonMock.mockRejectedValue(new ApiError(400, 'payouts must list at least one place'));

    const user = userEvent.setup();
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });

    await waitFor(() => {
      expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
    });
    await user.type(screen.getByLabelText(/Season Name/i), 'Spring');
    await user.click(screen.getByRole('button', { name: /Create Season/i }));

    expect(await screen.findByRole('alert')).toHaveTextContent(
      /payouts must list at least one place/i,
    );
  });

  it('offers no form when the defaults fail to load', async () => {
    defaultSeasonRulesMock.mockRejectedValue(new Error('network down'));

    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });

    expect(await screen.findByText(/Failed to load default season rules/i)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /Create Season/i })).not.toBeInTheDocument();
  });

  it('disables submit when the season name is blank', async () => {
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });
    await waitFor(() => {
      expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
    });
    expect(screen.getByRole('button', { name: /Create Season/i })).toBeDisabled();
  });

  it('bounds every rule amount input below at zero', async () => {
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });

    expect(await screen.findByLabelText(/Bet Amount/i)).toHaveAttribute('min', '0');
    expect(screen.getByLabelText(/Tie Floor/i)).toHaveAttribute('min', '0');
    screen.getAllByLabelText(/^Payout \d+$/).forEach((payout) => {
      expect(payout).toHaveAttribute('min', '0');
    });
  });

  it.each([/^Year$/i, /Bet Amount/i, /Tie Floor/i, /^Payout 1$/])(
    'disables submit rather than sending 0 when %s is cleared',
    async (label) => {
      const user = userEvent.setup();
      renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });
      await waitFor(() => {
        expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
      });
      await user.type(screen.getByLabelText(/Season Name/i), 'Spring');

      await user.clear(screen.getByLabelText(label));

      const submit = screen.getByRole('button', { name: /Create Season/i });
      expect(submit).toBeDisabled();
      await user.click(submit);
      expect(createSeasonMock).not.toHaveBeenCalled();
    },
  );

  it('submits a zero bet amount typed in explicitly', async () => {
    createSeasonMock.mockResolvedValue(makeSeason({ id: 'sn-6' }));
    const user = userEvent.setup();
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });
    await waitFor(() => {
      expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
    });
    await user.type(screen.getByLabelText(/Season Name/i), 'Spring');

    const betAmount = screen.getByLabelText(/Bet Amount/i);
    await user.clear(betAmount);
    await user.type(betAmount, '0');
    await user.click(screen.getByRole('button', { name: /Create Season/i }));

    await waitFor(() => {
      expect(createSeasonMock).toHaveBeenCalledTimes(1);
    });
    expect(createSeasonMock.mock.calls[0]?.[0].rules).toEqual({ ...serverDefaults, sideBetAmount: 0 });
  });

  it('submits no side-bet rounds when the rounds field is cleared', async () => {
    createSeasonMock.mockResolvedValue(makeSeason({ id: 'sn-7' }));
    const user = userEvent.setup();
    renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });
    await waitFor(() => {
      expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
    });
    await user.type(screen.getByLabelText(/Season Name/i), 'Spring');

    const rounds = screen.getByLabelText(/Rounds/i);
    await user.clear(rounds);
    expect(rounds).toHaveAttribute('placeholder', 'none');
    await user.click(screen.getByRole('button', { name: /Create Season/i }));

    await waitFor(() => {
      expect(createSeasonMock).toHaveBeenCalledTimes(1);
    });
    expect(createSeasonMock.mock.calls[0]?.[0].rules).toEqual({ ...serverDefaults, sideBetRounds: [] });
  });

  it.each(['five', '5-8'])(
    'disables submit and says why rather than sending fewer side-bet rounds than %j',
    async (typed) => {
      const user = userEvent.setup();
      renderWithProviders(<CreateSeasonSection />, { withLeagueSeasonProvider: false });
      await waitFor(() => {
        expect(screen.getByLabelText(/^League$/i)).toHaveValue('lg-1');
      });
      await user.type(screen.getByLabelText(/Season Name/i), 'Spring');

      const rounds = screen.getByLabelText(/Rounds/i);
      await user.clear(rounds);
      await user.type(rounds, typed);

      expect(screen.getByRole('alert')).toHaveTextContent(/blank for none/i);
      const submit = screen.getByRole('button', { name: /Create Season/i });
      expect(submit).toBeDisabled();
      await user.click(submit);
      expect(createSeasonMock).not.toHaveBeenCalled();
    },
  );
});
