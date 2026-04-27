import type { Season, Tournament, League, User } from '@/shared/api/types';

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
    status: 'active',
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
