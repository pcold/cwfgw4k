import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { act, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useLocation } from 'react-router';
import { api } from '@/shared/api/client';
import type { RosterTeam } from '@/shared/api/types';
import { useAuth } from '@/features/auth/AuthContext';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { makeLeague, makeSeason, makeUser } from '@/shared/test/fixtures';
import { renderWithProviders } from '@/shared/test/renderWithProviders';
import { useMyTeam } from './MyTeamContext';
import MyTeamPicker from './MyTeamPicker';

vi.mock('@/shared/api/client', () => ({
  api: {
    authMe: vi.fn(),
    leagues: vi.fn(),
    seasons: vi.fn(),
    rosters: vi.fn(),
  },
  ApiError: class ApiError extends Error {},
}));

const MY_TEAM_KEY = 'cwfg.myTeam';

const NEWEST_SEASON = makeSeason({ id: 'sn-2026', name: 'Fall', seasonYear: 2026 });
const OLD_SEASON = makeSeason({ id: 'sn-2025', name: 'Fall', seasonYear: 2025 });

const rosterTeam = (teamName: string): RosterTeam => ({ teamId: `tm-${teamName}`, teamNumber: null, teamName, picks: [] });

// PAYRAY became JURAY: the old season has the old name.
const NEWEST_TEAMS = ['Womble', 'BLAU', 'Juray', 'Poczik'].map(rosterTeam);
const OLD_TEAMS = ['BLAU', 'PAYRAY', 'Poczik'].map(rosterTeam);

function mockSeasons(newestTeams: readonly RosterTeam[] = NEWEST_TEAMS): void {
  vi.mocked(api.leagues).mockResolvedValue([makeLeague()]);
  vi.mocked(api.seasons).mockResolvedValue([NEWEST_SEASON, OLD_SEASON]);
  vi.mocked(api.rosters).mockImplementation((seasonId) =>
    Promise.resolve(seasonId === NEWEST_SEASON.id ? [...newestTeams] : OLD_TEAMS),
  );
}

function Probe() {
  const { choice } = useMyTeam();
  const location = useLocation();
  const { setSeasonId } = useLeagueSeason();
  return (
    <>
      <output aria-label="Choice">{choice.kind === 'team' ? `team ${choice.teamName}` : choice.kind}</output>
      <output aria-label="Location">{`${location.pathname}${location.search}`}</output>
      <button type="button" onClick={() => setSeasonId(OLD_SEASON.id)}>
        View the old season
      </button>
    </>
  );
}

function renderPicker(initialPath = '/rankings') {
  return renderWithProviders(
    <>
      <Probe />
      <MyTeamPicker />
    </>,
    { initialPath, withMyTeamProvider: true },
  );
}

const picker = (): Promise<HTMLElement> => screen.findByRole('dialog', { name: 'Which team is yours?' });

const choice = (): HTMLElement => screen.getByRole('status', { name: 'Choice' });

const rostersRequested = (): Promise<void> =>
  waitFor(() => {
    expect(api.rosters).toHaveBeenCalledWith(NEWEST_SEASON.id);
  });

/** Past the rosters landing and the query's notify, so an absent picker is absent for good and not merely not yet. */
async function settled(): Promise<void> {
  await rostersRequested();
  await act(async () => {
    await vi.mocked(api.rosters).mock.results.at(-1)?.value;
    await new Promise((resolve) => {
      setTimeout(resolve, 0);
    });
  });
}

function backdrop(): HTMLElement {
  const overlay = screen.getByRole('dialog').parentElement;
  if (overlay === null) throw new Error('The dialog has no backdrop');
  return overlay;
}

describe('MyTeamPicker', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    mockSeasons();
  });

  afterEach(() => {
    window.localStorage.clear();
  });

  it('asks on a first visit, listing the newest season’s teams alphabetically in two columns', async () => {
    renderPicker();

    const teams = within(await picker()).getByRole('group', { name: 'Teams' });
    expect(within(teams).getAllByRole('button').map((button) => button.textContent)).toEqual([
      'BLAU',
      'JURAY',
      'POCZIK',
      'WOMBLE',
    ]);
    expect(teams).toHaveClass('grid', 'grid-cols-2');
  });

  it('only selects on a team button; the primary button names the team and alone saves it', async () => {
    const user = userEvent.setup();
    renderPicker();
    const dialog = await picker();
    expect(within(dialog).getByRole('button', { name: 'Set my team' })).toBeDisabled();

    await user.click(within(dialog).getByRole('button', { name: 'WOMBLE' }));

    expect(within(dialog).getByRole('button', { name: 'WOMBLE' })).toHaveAttribute('aria-pressed', 'true');
    expect(window.localStorage.getItem(MY_TEAM_KEY)).toBeNull();
    await user.click(within(dialog).getByRole('button', { name: 'Set WOMBLE as my team' }));

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(choice()).toHaveTextContent('team WOMBLE');
  });

  it('does not ask again on the next visit once a team is set', async () => {
    const user = userEvent.setup();
    const { unmount } = renderPicker();
    const dialog = await picker();
    await user.click(within(dialog).getByRole('button', { name: 'JURAY' }));
    await user.click(within(dialog).getByRole('button', { name: 'Set JURAY as my team' }));
    unmount();

    renderPicker();
    await settled();

    expect(choice()).toHaveTextContent('team JURAY');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('remembers Just browsing, and does not ask again on the next visit', async () => {
    const user = userEvent.setup();
    const { unmount } = renderPicker();
    await user.click(within(await picker()).getByRole('button', { name: 'Just browsing' }));
    expect(screen.queryByRole('dialog')).toBeNull();
    unmount();

    renderPicker();
    await settled();

    expect(choice()).toHaveTextContent('browsing');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it.each([
    ['Close', async (user: ReturnType<typeof userEvent.setup>) => user.click(screen.getByRole('button', { name: 'Close' }))],
    ['Escape', async (user: ReturnType<typeof userEvent.setup>) => user.keyboard('{Escape}')],
    [
      'the backdrop',
      async (user: ReturnType<typeof userEvent.setup>) => user.click(backdrop()),
    ],
  ])('records nothing when dismissed by %s, and asks again on the next visit', async (_how, dismiss) => {
    const user = userEvent.setup();
    const { unmount } = renderPicker();
    await picker();

    await dismiss(user);

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(window.localStorage.getItem(MY_TEAM_KEY)).toBeNull();
    expect(choice()).toHaveTextContent('unasked');
    unmount();

    renderPicker();
    expect(await picker()).toBeInTheDocument();
  });

  it('stays closed while the newest season’s teams load', async () => {
    vi.mocked(api.rosters).mockReturnValue(new Promise(() => {}));
    renderPicker();

    await rostersRequested();

    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('keeps a saved team and does not ask while the newest season has no teams', async () => {
    mockSeasons([]);
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"WOMBLE"}');
    renderPicker();
    await settled();

    expect(screen.queryByRole('dialog')).toBeNull();
    expect(choice()).toHaveTextContent('team WOMBLE');
  });

  it('does not ask a first-time visitor while the newest season has no teams', async () => {
    mockSeasons([]);
    renderPicker();
    await settled();

    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('asks again when the saved team is no longer in the newest season', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"PAYRAY"}');
    renderPicker();

    expect(await picker()).toBeInTheDocument();
    expect(choice()).toHaveTextContent('unasked');
  });

  it('keeps the saved team while an old season, which lacks it, is the one on screen', async () => {
    const user = userEvent.setup();
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"team","teamName":"JURAY"}');
    renderPicker();
    await settled();

    await user.click(screen.getByRole('button', { name: 'View the old season' }));
    await settled();

    expect(choice()).toHaveTextContent('team JURAY');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('saves a ?team= link, skips the picker and removes only that param', async () => {
    renderPicker('/rankings?week=3&team=womble&live=false');

    await waitFor(() => {
      expect(screen.getByRole('status', { name: 'Location' })).toHaveTextContent('/rankings?week=3&live=false');
    });
    expect(choice()).toHaveTextContent('team WOMBLE');
    expect(window.localStorage.getItem(MY_TEAM_KEY)).toBe('{"kind":"team","teamName":"WOMBLE"}');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('lets a ?team= link replace a saved choice', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"browsing"}');
    renderPicker('/?team=Poczik');

    await waitFor(() => {
      expect(choice()).toHaveTextContent('team POCZIK');
    });
    expect(screen.getByRole('status', { name: 'Location' })).toHaveTextContent(/^\/$/);
  });

  it('removes an unmatched ?team= link, saves nothing and asks as on any first visit', async () => {
    renderPicker('/scoreboard?team=payray');

    expect(await picker()).toBeInTheDocument();
    expect(screen.getByRole('status', { name: 'Location' })).toHaveTextContent(/^\/scoreboard$/);
    expect(window.localStorage.getItem(MY_TEAM_KEY)).toBeNull();
  });

  it('stays closed on Admin', async () => {
    renderPicker('/admin');
    await settled();

    expect(choice()).toHaveTextContent('unasked');
    expect(screen.queryByRole('dialog')).toBeNull();
  });

  it('keeps the team for this visit when storage refuses the write', async () => {
    const user = userEvent.setup();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('quota', 'QuotaExceededError');
    });
    try {
      renderPicker();
      const dialog = await picker();
      await user.click(within(dialog).getByRole('button', { name: 'BLAU' }));
      await user.click(within(dialog).getByRole('button', { name: 'Set BLAU as my team' }));

      expect(choice()).toHaveTextContent('team BLAU');
      expect(screen.queryByRole('dialog')).toBeNull();
    } finally {
      vi.restoreAllMocks();
    }
  });

  it('asks when storage refuses the read', async () => {
    window.localStorage.setItem(MY_TEAM_KEY, '{"kind":"browsing"}');
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('denied', 'SecurityError');
    });
    try {
      renderPicker();

      expect(await picker()).toBeInTheDocument();
    } finally {
      vi.restoreAllMocks();
    }
  });
});

function AuthProbe() {
  const { username, isAdmin } = useAuth();
  return <output aria-label="Signed in as">{`${username ?? 'nobody'} ${isAdmin ? 'admin' : ''}`}</output>;
}

describe('MyTeamPicker beside the admin session', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    window.localStorage.clear();
    window.sessionStorage.clear();
    mockSeasons();
  });

  afterEach(() => {
    window.localStorage.clear();
    window.sessionStorage.clear();
  });

  it('leaves the auth state and every other storage key as it found them', async () => {
    vi.mocked(api.authMe).mockResolvedValue(makeUser({ username: 'commish', role: 'admin' }));
    window.localStorage.setItem('other.key', 'kept');
    window.sessionStorage.setItem('session.key', 'kept');
    const user = userEvent.setup();
    renderWithProviders(
      <>
        <AuthProbe />
        <Probe />
        <MyTeamPicker />
      </>,
      { withMyTeamProvider: true, withAuthProvider: true },
    );
    await waitFor(() => {
      expect(screen.getByRole('status', { name: 'Signed in as' })).toHaveTextContent('commish admin');
    });

    const dialog = await picker();
    await user.click(within(dialog).getByRole('button', { name: 'WOMBLE' }));
    await user.click(within(dialog).getByRole('button', { name: 'Set WOMBLE as my team' }));

    expect(screen.getByRole('status', { name: 'Signed in as' })).toHaveTextContent('commish admin');
    expect(api.authMe).toHaveBeenCalledOnce();
    expect(Object.keys(window.localStorage).sort()).toEqual([MY_TEAM_KEY, 'other.key']);
    expect(window.localStorage.getItem('other.key')).toBe('kept');
    expect(Object.keys(window.sessionStorage)).toEqual(['session.key']);
    expect(window.sessionStorage.getItem('session.key')).toBe('kept');
  });
});
