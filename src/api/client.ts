import type {
  ActionMessageResponse,
  AuthStatus,
  League,
  Rankings,
  RosterConfirmResult,
  RosterConfirmTeamInput,
  RosterPreview,
  RosterTeam,
  ScheduleUploadResult,
  Season,
  SeasonRules,
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

function camelToSnake(key: string): string {
  return key.replace(/[A-Z]/g, (c) => `_${c.toLowerCase()}`);
}

export function snakeifyKeys(value: unknown): unknown {
  if (Array.isArray(value)) return value.map(snakeifyKeys);
  if (value !== null && typeof value === 'object') {
    const out: Record<string, unknown> = {};
    for (const [k, v] of Object.entries(value as Record<string, unknown>)) {
      out[camelToSnake(k)] = snakeifyKeys(v);
    }
    return out;
  }
  return value;
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

async function postJson<T>(path: string, payload?: unknown): Promise<T> {
  const init: RequestInit = { method: 'POST' };
  if (payload !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = JSON.stringify(snakeifyKeys(payload));
  }
  const resp = await fetch(path, init);
  const text = await resp.text();
  const parsed: unknown = text ? JSON.parse(text) : null;
  if (!resp.ok) {
    const errMsg =
      parsed && typeof parsed === 'object' && 'error' in parsed
        ? String((parsed as { error: unknown }).error)
        : `${resp.status} ${resp.statusText}`;
    throw new ApiError(resp.status, errMsg);
  }
  return camelizeKeys(parsed) as T;
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

  authMe: () => getJson<AuthStatus>('/api/v1/auth/me'),
  login: (username: string, password: string) =>
    postJson<{ ok: boolean }>('/api/v1/auth/login', { username, password }),
  logout: () => postJson<{ ok: boolean }>('/api/v1/auth/logout'),

  createLeague: (name: string) => postJson<League>('/api/v1/leagues', { name }),
  createSeason: (input: {
    leagueId: string;
    name: string;
    seasonYear: number;
    rules: SeasonRules;
  }) => postJson<Season>('/api/v1/seasons', input),
  uploadSchedule: (input: { seasonId: string; seasonYear: number; schedule: string }) =>
    postJson<ScheduleUploadResult>('/api/v1/admin/season', input),
  previewRoster: (roster: string) =>
    postJson<RosterPreview>('/api/v1/admin/roster/preview', { roster }),
  confirmRoster: (input: { seasonId: string; teams: RosterConfirmTeamInput[] }) =>
    postJson<RosterConfirmResult>('/api/v1/admin/roster/confirm', input),
  finalizeTournament: (tournamentId: string) =>
    postJson<ActionMessageResponse>(`/api/v1/tournaments/${tournamentId}/finalize`),
  resetTournament: (tournamentId: string) =>
    postJson<ActionMessageResponse>(`/api/v1/tournaments/${tournamentId}/reset`),
  cleanSeasonResults: (seasonId: string) =>
    postJson<ActionMessageResponse>(`/api/v1/seasons/${seasonId}/clean-results`),
};
