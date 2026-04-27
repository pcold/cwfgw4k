import { describe, expect, it } from 'vitest';
import { defaultScoreboardTournament, earliestUnfinalized, tournamentLabel } from './tournament';
import type { Tournament } from '@/shared/api/types';

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

describe('earliestUnfinalized', () => {
  it('returns the first non-completed tournament when the list is sorted oldest-first', () => {
    const tournaments: Tournament[] = [
      makeTournament({ id: 'tn-1', status: 'completed' }),
      makeTournament({ id: 'tn-2', status: 'completed' }),
      makeTournament({ id: 'tn-3', status: 'in_progress' }),
      makeTournament({ id: 'tn-4', status: 'upcoming' }),
    ];
    expect(earliestUnfinalized(tournaments)).toBe('tn-3');
  });

  it('returns null when every event is completed (caller picks the fallback)', () => {
    const tournaments: Tournament[] = [
      makeTournament({ id: 'tn-1', status: 'completed' }),
      makeTournament({ id: 'tn-2', status: 'completed' }),
    ];
    expect(earliestUnfinalized(tournaments)).toBeNull();
  });

  it('returns null on an empty list', () => {
    expect(earliestUnfinalized([])).toBeNull();
  });
});

describe('defaultScoreboardTournament', () => {
  it('returns the earliest non-completed tournament when one exists', () => {
    const tournaments: Tournament[] = [
      makeTournament({ id: 'tn-1', status: 'completed' }),
      makeTournament({ id: 'tn-2', status: 'in_progress' }),
      makeTournament({ id: 'tn-3', status: 'upcoming' }),
    ];
    expect(defaultScoreboardTournament(tournaments)).toBe('tn-2');
  });

  it('falls back to the last tournament when every event is completed', () => {
    const tournaments: Tournament[] = [
      makeTournament({ id: 'tn-1', status: 'completed' }),
      makeTournament({ id: 'tn-2', status: 'completed' }),
      makeTournament({ id: 'tn-3', status: 'completed' }),
    ];
    expect(defaultScoreboardTournament(tournaments)).toBe('tn-3');
  });

  it('returns null on an empty list', () => {
    expect(defaultScoreboardTournament([])).toBeNull();
  });
});
