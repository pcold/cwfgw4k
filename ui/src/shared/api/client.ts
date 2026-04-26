import type {
  ActionMessageResponse,
  AuthStatus,
  GolferHistory,
  League,
  Rankings,
  RosterConfirmResult,
  RosterConfirmTeamInput,
  RosterPreview,
  RosterTeam,
  SeasonImportResult,
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

async function putJson<T>(path: string, payload: unknown): Promise<T> {
  const resp = await fetch(path, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(snakeifyKeys(payload)),
  });
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

async function postText<T>(path: string, body: string): Promise<T> {
  const resp = await fetch(path, {
    method: 'POST',
    headers: { 'Content-Type': 'text/plain' },
    body,
  });
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

async function deleteEmpty(path: string): Promise<void> {
  const resp = await fetch(path, { method: 'DELETE' });
  if (!resp.ok) {
    const text = await resp.text();
    let errMsg = `${resp.status} ${resp.statusText}`;
    if (text) {
      try {
        const parsed = JSON.parse(text) as unknown;
        if (parsed && typeof parsed === 'object' && 'error' in parsed) {
          errMsg = String((parsed as { error: unknown }).error);
        }
      } catch {
        // fall through to the status-line message
      }
    }
    throw new ApiError(resp.status, errMsg);
  }
}

export const api = {
  leagues: () => getJson<League[]>('/api/v1/leagues'),
  seasons: (leagueId: string) =>
    getJson<Season[]>(`/api/v1/seasons?league_id=${encodeURIComponent(leagueId)}`),
  seasonRules: (seasonId: string) =>
    getJson<SeasonRules>(`/api/v1/seasons/${seasonId}/rules`),
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
      `/api/v1/tournaments?season_id=${encodeURIComponent(seasonId)}`,
    ),
  updateTournament: (id: string, body: { payoutMultiplier?: number }) =>
    putJson<Tournament>(`/api/v1/tournaments/${encodeURIComponent(id)}`, body),
  tournamentReport: (seasonId: string, tournamentId: string, live: boolean) =>
    getJson<WeeklyReport>(
      `/api/v1/seasons/${seasonId}/report/${tournamentId}${live ? '?live=true' : ''}`,
    ),
  golferHistory: (seasonId: string, golferId: string) =>
    getJson<GolferHistory>(`/api/v1/seasons/${seasonId}/golfer/${golferId}/history`),

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
  importSeasonSchedule: (input: { seasonId: string; startDate: string; endDate: string }) =>
    postJson<SeasonImportResult>(
      `/api/v1/admin/seasons/${encodeURIComponent(input.seasonId)}/upload`,
      { startDate: input.startDate, endDate: input.endDate },
    ),
  // The backend's previewRoster route consumes the raw TSV/CSV body via
  // receiveText() rather than a JSON envelope, so post the text as-is.
  previewRoster: (roster: string) =>
    postText<RosterPreview>('/api/v1/admin/roster/preview', roster),
  confirmRoster: (input: { seasonId: string; teams: RosterConfirmTeamInput[] }) =>
    postJson<RosterConfirmResult>('/api/v1/admin/roster/confirm', input),
  finalizeTournament: (tournamentId: string) =>
    postJson<ActionMessageResponse>(`/api/v1/tournaments/${tournamentId}/finalize`),
  resetTournament: (tournamentId: string) =>
    postJson<ActionMessageResponse>(`/api/v1/tournaments/${tournamentId}/reset`),
  cleanSeasonResults: (seasonId: string) =>
    postJson<ActionMessageResponse>(`/api/v1/seasons/${seasonId}/clean-results`),
  deleteSeason: (seasonId: string) =>
    deleteEmpty(`/api/v1/seasons/${encodeURIComponent(seasonId)}`),
};
