import type {
  League,
  LiveLeaderboardEntry,
  RoundScorecard,
  Season,
  Tournament,
  User,
  WonLostTotals,
} from '@/shared/api/types';

// Centralized factories for the most-mocked API shapes. Tests that don't
// care about a specific field get sensible defaults; everything else is
// passed via the override arg. Keeps test mocks honest when the wire
// shape grows new fields — you change the default once, not in 12 files.

export function makeSeason(overrides: Partial<Season> = {}): Season {
  return {
    id: 'sn-1',
    leagueId: 'lg-1',
    name: 'Spring',
    seasonYear: 2026,
    seasonNumber: 1,
    tieFloor: 1,
    sideBetAmount: 15,
    maxTeams: 13,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

export function makeLeague(overrides: Partial<League> = {}): League {
  return {
    id: 'lg-1',
    name: 'Alpha',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

export function makeTournament(overrides: Partial<Tournament> = {}): Tournament {
  return {
    id: 'tn-1',
    pgaTournamentId: null,
    name: 'Sample Open',
    seasonId: 'sn-1',
    startDate: '2026-03-01',
    endDate: '2026-03-04',
    courseName: null,
    status: 'completed',
    purseAmount: null,
    payoutMultiplier: 1,
    week: '10',
    isTeamEvent: false,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

export function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 'u-1',
    username: 'admin',
    role: 'admin',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

export function makeWonLostTotals(overrides: Partial<WonLostTotals> = {}): WonLostTotals {
  return { won: 0, lost: 0, net: 0, ...overrides };
}

// `rostered` follows `players` unless overridden, as it does on the wire.
export function makeLeaderboardEntry(overrides: Partial<LiveLeaderboardEntry> = {}): LiveLeaderboardEntry {
  const players = overrides.players ?? [{ name: 'Scottie Scheffler', teamNames: [] }];
  return {
    position: 1,
    scoreToPar: 0,
    roundScores: [],
    totalStrokes: null,
    scorecards: [],
    rostered: players.some((player) => player.teamNames.length > 0),
    ...overrides,
    players,
  };
}

const FIXTURE_PAR = 4;

function strokesSum(strokes: readonly (number | null)[]): number | null {
  const played = strokes.filter((stroke) => stroke !== null);
  return played.length === 0 ? null : played.reduce((sum, stroke) => sum + stroke, 0);
}

// Every hole a par 4. `strokes` runs from hole 1, and a hole past its end or given null is unplayed.
export function makeRoundScorecard(round: number, strokes: readonly (number | null)[]): RoundScorecard {
  const holes = Array.from({ length: 18 }, (_, index) => {
    const played = strokes[index] ?? null;
    return { hole: index + 1, par: FIXTURE_PAR, strokes: played, toPar: played === null ? null : played - FIXTURE_PAR };
  });
  const front = holes.slice(0, 9).map((hole) => hole.strokes);
  const back = holes.slice(9).map((hole) => hole.strokes);
  return {
    round,
    holes,
    frontNine: strokesSum(front),
    backNine: strokesSum(back),
    total: strokesSum([...front, ...back]),
    frontPar: FIXTURE_PAR * 9,
    backPar: FIXTURE_PAR * 9,
    totalPar: FIXTURE_PAR * 18,
  };
}
