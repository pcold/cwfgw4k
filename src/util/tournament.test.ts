import { describe, expect, it } from 'vitest';
import { tournamentLabel } from './tournament';
import type { Tournament } from '@/api/types';

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
