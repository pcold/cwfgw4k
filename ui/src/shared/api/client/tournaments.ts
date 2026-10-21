import type { CurrentTournament, FinalizedTournament, Tournament } from '../types';
import { getJson, postJson, putJson } from './core';

export const tournamentsApi = {
  tournaments: (seasonId: string): Promise<Tournament[]> =>
    getJson<Tournament[]>(`/tournaments?season_id=${encodeURIComponent(seasonId)}`),
  resettableTournaments: (seasonId: string): Promise<Tournament[]> =>
    getJson<Tournament[]>(`/tournaments/resettable?season_id=${encodeURIComponent(seasonId)}`),
  currentTournament: (seasonId: string): Promise<CurrentTournament> =>
    getJson<CurrentTournament>(`/tournaments/current?season_id=${encodeURIComponent(seasonId)}`),
  updateTournament: (id: string, body: { payoutMultiplier?: number }): Promise<Tournament> =>
    putJson<Tournament>(`/tournaments/${encodeURIComponent(id)}`, body),
  finalizeTournament: (tournamentId: string): Promise<FinalizedTournament> =>
    postJson<FinalizedTournament>(`/tournaments/${tournamentId}/finalize`),
  resetTournament: (tournamentId: string): Promise<Tournament> =>
    postJson<Tournament>(`/tournaments/${tournamentId}/reset`),
};
