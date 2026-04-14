import type { League, Rankings, RosterTeam, Season, WeeklyReport } from './types';

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function getJson<T>(path: string): Promise<T> {
  const resp = await fetch(path);
  if (!resp.ok) throw new ApiError(resp.status, `${resp.status} ${resp.statusText}`);
  return resp.json() as Promise<T>;
}

export const api = {
  leagues: () => getJson<League[]>('/api/v1/leagues'),
  seasons: (leagueId: string) =>
    getJson<Season[]>(`/api/v1/seasons?leagueId=${encodeURIComponent(leagueId)}`),
  seasonReport: (seasonId: string, live: boolean) =>
    getJson<WeeklyReport>(`/api/v1/seasons/${seasonId}/report${live ? '?live=true' : ''}`),
  rankings: (seasonId: string, live: boolean) =>
    getJson<Rankings>(`/api/v1/seasons/${seasonId}/rankings${live ? '?live=true' : ''}`),
  rosters: (seasonId: string) => getJson<RosterTeam[]>(`/api/v1/seasons/${seasonId}/rosters`),
};
