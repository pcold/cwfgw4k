import type { GolferHistory, PlayerRankings, Rankings, WeeklyReport } from '../types';
import { getJson } from './core';

export const reportsApi = {
  seasonReport: (seasonId: string, live: boolean): Promise<WeeklyReport> =>
    getJson<WeeklyReport>(`/seasons/${seasonId}/report${live ? '?live=true' : ''}`),
  rankings: (seasonId: string, live: boolean, throughTournamentId?: string): Promise<Rankings> => {
    const params = new URLSearchParams();
    if (live) params.set('live', 'true');
    if (throughTournamentId) params.set('through', throughTournamentId);
    const qs = params.toString();
    return getJson<Rankings>(`/seasons/${seasonId}/rankings${qs ? `?${qs}` : ''}`);
  },
  playerRankings: (seasonId: string, live: boolean, throughTournamentId?: string): Promise<PlayerRankings> => {
    const params = new URLSearchParams();
    if (live) params.set('live', 'true');
    if (throughTournamentId) params.set('through', throughTournamentId);
    const qs = params.toString();
    return getJson<PlayerRankings>(`/seasons/${seasonId}/player-rankings${qs ? `?${qs}` : ''}`);
  },
  tournamentReport: (seasonId: string, tournamentId: string, live: boolean): Promise<WeeklyReport> =>
    getJson<WeeklyReport>(`/seasons/${seasonId}/report/${tournamentId}${live ? '?live=true' : ''}`),
  golferHistory: (seasonId: string, golferId: string): Promise<GolferHistory> =>
    getJson<GolferHistory>(`/seasons/${seasonId}/golfer/${golferId}/history`),
};
