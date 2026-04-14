import type {
  League,
  Rankings,
  RosterTeam,
  Season,
  Tournament,
  WeeklyReport,
} from './types';

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

function snakeToCamel(key: string): string {
  return key.replace(/_([a-z0-9])/g, (_, c: string) => c.toUpperCase());
}

// Recursively rewrites snake_case object keys to camelCase. The backend emits
// snake_case via circe's `withSnakeCaseMemberNames` to stay compatible with the
// legacy Alpine UI; this transform lets the React side use idiomatic camelCase
// types. Once Alpine is retired we can drop the backend config and delete this.
export function camelizeKeys(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(camelizeKeys);
  if (value !== null && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      out[snakeToCamel(k)] = camelizeKeys(v);
    }
    return out;
  }
  return value;
}

async function getJson<T>(path: string): Promise<T> {
  const resp = await fetch(path);
  if (!resp.ok) throw new ApiError(resp.status, `${resp.status} ${resp.statusText}`);
  const body = (await resp.json()) as unknown;
  return camelizeKeys(body) as T;
}

export const api = {
  leagues: () => getJson<League[]>('/api/v1/leagues'),
  seasons: (leagueId: string) =>
    getJson<Season[]>(`/api/v1/seasons?leagueId=${encodeURIComponent(leagueId)}`),
  seasonReport: (seasonId: string, live: boolean) =>
    getJson<WeeklyReport>(`/api/v1/seasons/${seasonId}/report${live ? '?live=true' : ''}`),
  rankings: (seasonId: string, live: boolean, throughTournamentId?: string) => {
    const params = new URLSearchParams();
    if (live) params.set('live', 'true');
    if (throughTournamentId) params.set('through', throughTournamentId);
    const qs = params.toString();
    return getJson<Rankings>(`/api/v1/seasons/${seasonId}/rankings${qs ? `?${qs}` : ''}`);
  },
  rosters: (seasonId: string) => getJson<RosterTeam[]>(`/api/v1/seasons/${seasonId}/rosters`),
  tournaments: (seasonId: string) =>
    getJson<Tournament[]>(
      `/api/v1/tournaments?seasonId=${encodeURIComponent(seasonId)}`,
    ),
  tournamentReport: (seasonId: string, tournamentId: string, live: boolean) =>
    getJson<WeeklyReport>(
      `/api/v1/seasons/${seasonId}/report/${tournamentId}${live ? '?live=true' : ''}`,
    ),
};
