import { describe, expect, it } from 'vitest';
import type { Tournament } from '@/shared/api/types';
import {
  ALL_TOURNAMENTS,
  defaultReportSelection,
  defaultScoreboardSelection,
  pickInSeason,
  tournamentLabel,
  tournamentShortLabel,
  tournamentWeekLabel,
} from './tournament';

function makeTournament(overrides: Partial<Tournament> = {}): Tournament {
  return {
    id: 'tn-1',
    pgaTournamentId: null,
    name: 'Sony Open',
    seasonId: 'sn-1',
    startDate: '2026-01-15',
    endDate: '2026-01-18',
    courseName: null,
    status: 'completed',
    purseAmount: null,
    payoutMultiplier: 1,
    week: '1',
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('tournamentLabel', () => {
  it('omits the multiplier when it is 1', () => {
    expect(tournamentLabel(makeTournament())).toBe('Wk 1 — Sony Open — completed');
  });

  it('shows the multiplier in parens when it is not 1', () => {
    expect(tournamentLabel(makeTournament({ payoutMultiplier: 2 }))).toBe(
      'Wk 1 — Sony Open (2x) — completed',
    );
  });

  it('falls back to ? when the week is null', () => {
    expect(tournamentLabel(makeTournament({ week: null }))).toBe('Wk ? — Sony Open — completed');
  });

  it('shows status regardless of completion', () => {
    expect(tournamentLabel(makeTournament({ status: 'in_progress' }))).toBe(
      'Wk 1 — Sony Open — in_progress',
    );
  });
});

describe('tournamentShortLabel', () => {
  it('reads the week and the name alone, without the multiplier or the status', () => {
    expect(tournamentShortLabel(makeTournament({ payoutMultiplier: 2 }))).toBe('Wk 1 · Sony Open');
  });

  it('falls back to ? when the week is null', () => {
    expect(tournamentShortLabel(makeTournament({ week: null }))).toBe('Wk ? · Sony Open');
  });
});

describe('tournamentWeekLabel', () => {
  it('reads the week alone', () => {
    expect(tournamentWeekLabel(makeTournament({ week: '11' }))).toBe('Wk 11');
  });

  it('falls back to ? when the week is null', () => {
    expect(tournamentWeekLabel(makeTournament({ week: null }))).toBe('Wk ?');
  });
});

describe('pickInSeason', () => {
  const loaded = [makeTournament({ id: 'tn-1' }), makeTournament({ id: 'tn-2' })];

  it('keeps a pick the loaded season holds', () => {
    expect(pickInSeason('tn-2', loaded)).toBe('tn-2');
  });

  it('drops a pick the loaded season lacks', () => {
    expect(pickInSeason('tn-other', loaded)).toBeNull();
    expect(pickInSeason('tn-1', [])).toBeNull();
  });

  it('keeps All Tournaments, which every season has', () => {
    expect(pickInSeason(ALL_TOURNAMENTS, [])).toBe(ALL_TOURNAMENTS);
  });

  it('has nothing to keep before a pick', () => {
    expect(pickInSeason(null, loaded)).toBeNull();
  });
});

describe('defaultReportSelection', () => {
  it('waits for the server rather than guessing', () => {
    expect(defaultReportSelection(undefined)).toBeNull();
  });

  it('opens on the unfinalized tournament the server names', () => {
    expect(defaultReportSelection({ type: 'unfinalized', tournamentId: 'tn-3' })).toBe('tn-3');
  });

  it('opens on All Tournaments once the season is finalized, not on its last event', () => {
    expect(defaultReportSelection({ type: 'season_finalized', tournamentId: 'tn-9' })).toBe(
      ALL_TOURNAMENTS,
    );
  });

  it('opens on All Tournaments for a season with none', () => {
    expect(defaultReportSelection({ type: 'no_tournaments' })).toBe(ALL_TOURNAMENTS);
  });
});

describe('defaultScoreboardSelection', () => {
  it('waits for the server rather than guessing', () => {
    expect(defaultScoreboardSelection(undefined)).toBeNull();
  });

  it('opens on the unfinalized tournament the server names', () => {
    expect(defaultScoreboardSelection({ type: 'unfinalized', tournamentId: 'tn-3' })).toBe('tn-3');
  });

  it('opens on the last tournament once the season is finalized', () => {
    expect(defaultScoreboardSelection({ type: 'season_finalized', tournamentId: 'tn-9' })).toBe(
      'tn-9',
    );
  });

  it('selects nothing for a season with none', () => {
    expect(defaultScoreboardSelection({ type: 'no_tournaments' })).toBeNull();
  });
});
