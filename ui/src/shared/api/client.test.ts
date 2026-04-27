import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api, camelizeKeys, snakeifyKeys } from './client';

type FetchMock = ReturnType<typeof vi.fn>;

function mockJson(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('camelizeKeys', () => {
  it('passes through primitives and null', () => {
    expect(camelizeKeys(42)).toBe(42);
    expect(camelizeKeys('hi')).toBe('hi');
    expect(camelizeKeys(null)).toBe(null);
  });

  it('rewrites nested objects and arrays', () => {
    const input = {
      top_level: {
        inner_key: [{ leaf_one: 1, leaf_two: null }],
      },
    };
    expect(camelizeKeys(input)).toEqual({
      topLevel: { innerKey: [{ leafOne: 1, leafTwo: null }] },
    });
  });

  it('leaves already-camelCase keys alone', () => {
    expect(camelizeKeys({ teamName: 'Aces', total: 120 })).toEqual({
      teamName: 'Aces',
      total: 120,
    });
  });
});

describe('snakeifyKeys', () => {
  it('converts camelCase to snake_case recursively', () => {
    expect(
      snakeifyKeys({
        leagueId: 'lg-1',
        seasonYear: 2026,
        rules: { tieFloor: 1, sideBetRounds: [5, 6] },
      }),
    ).toEqual({
      league_id: 'lg-1',
      season_year: 2026,
      rules: { tie_floor: 1, side_bet_rounds: [5, 6] },
    });
  });

  it('passes through primitives and null', () => {
    expect(snakeifyKeys(null)).toBe(null);
    expect(snakeifyKeys(42)).toBe(42);
  });
});

describe('api client', () => {
  let fetchMock: FetchMock;

  beforeEach(() => {
    fetchMock = vi.fn();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('leagues() fetches /api/v1/leagues and returns parsed JSON', async () => {
    const leagues = [{ id: 'a', name: 'League A', createdAt: '2026-01-01T00:00:00Z' }];
    fetchMock.mockResolvedValueOnce(mockJson(leagues));

    const result = await api.leagues();

    expect(fetchMock).toHaveBeenCalledWith('/api/v1/leagues');
    expect(result).toEqual(leagues);
  });

  it('seasons() url-encodes the league id', async () => {
    fetchMock.mockResolvedValueOnce(mockJson([]));
    await api.seasons('league/with space');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/seasons?league_id=league%2Fwith%20space');
  });

  it('seasonReport() appends live=true only when requested', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(mockJson({})));
    await api.seasonReport('season-1', false);
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/season-1/report');
    await api.seasonReport('season-1', true);
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/season-1/report?live=true');
  });

  it('rankings() builds the query string from live + through tournament', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(mockJson({})));
    await api.rankings('sn-1', false);
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/sn-1/rankings');
    await api.rankings('sn-1', true);
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/sn-1/rankings?live=true');
    await api.rankings('sn-1', false, 'tn-7');
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/sn-1/rankings?through=tn-7');
    await api.rankings('sn-1', true, 'tn-7');
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/v1/seasons/sn-1/rankings?live=true&through=tn-7',
    );
  });

  it('converts snake_case response keys to camelCase', async () => {
    const wire = [
      {
        team_id: 't-1',
        team_name: 'Aces',
        picks: [{ round: 1, golfer_name: 'Scheffler', ownership_pct: 100 }],
      },
    ];
    fetchMock.mockResolvedValueOnce(mockJson(wire));
    const result = await api.rosters('sn-1');
    expect(result).toEqual([
      {
        teamId: 't-1',
        teamName: 'Aces',
        picks: [{ round: 1, golferName: 'Scheffler', ownershipPct: 100 }],
      },
    ]);
  });

  it('throws ApiError with the HTTP status when the response is not ok', async () => {
    fetchMock.mockImplementation(() =>
      Promise.resolve(new Response('nope', { status: 503, statusText: 'Boom' })),
    );
    const err = await api.leagues().then(
      () => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ name: 'ApiError', status: 503 });
  });

  it('createSeason() sends snake_case keys in the body', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        id: 'sn-9',
        league_id: 'lg-1',
        name: 'Spring',
        season_year: 2026,
        season_number: 1,
        status: 'active',
      }),
    );
    const result = await api.createSeason({
      leagueId: 'lg-1',
      name: 'Spring',
      seasonYear: 2026,
      rules: { payouts: [18, 12], tieFloor: 1, sideBetRounds: [5, 6], sideBetAmount: 15 },
    });
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/seasons');
    const body = JSON.parse(String((call[1] as RequestInit).body)) as Record<string, unknown>;
    expect(body).toEqual({
      league_id: 'lg-1',
      name: 'Spring',
      season_year: 2026,
      rules: {
        payouts: [18, 12],
        tie_floor: 1,
        side_bet_rounds: [5, 6],
        side_bet_amount: 15,
      },
    });
    expect(result).toMatchObject({ id: 'sn-9', leagueId: 'lg-1', seasonYear: 2026 });
  });

  it('postJson surfaces the server-supplied error message', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: 'Bad request' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    const err = await api.createLeague('bad').then(
      () => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).message).toBe('Bad request');
    expect((err as ApiError).status).toBe(400);
  });

  it('login() posts credentials and returns the User from the body', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        id: 'u-1',
        username: 'admin',
        role: 'admin',
        created_at: '2026-01-01T00:00:00Z',
      }),
    );
    const result = await api.login('admin', 'secret');
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/auth/login');
    expect(JSON.parse(String((call[1] as RequestInit).body))).toEqual({
      username: 'admin',
      password: 'secret',
    });
    expect(result).toEqual({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
  });

  it('authMe() returns null when the server responds 401', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: 'unauthorized' }), {
        status: 401,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    const result = await api.authMe();
    expect(result).toBeNull();
  });

  it('authMe() returns the User when the server responds 200', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        id: 'u-1',
        username: 'admin',
        role: 'admin',
        created_at: '2026-01-01T00:00:00Z',
      }),
    );
    const result = await api.authMe();
    expect(result).toEqual({
      id: 'u-1',
      username: 'admin',
      role: 'admin',
      createdAt: '2026-01-01T00:00:00Z',
    });
  });

  it('logout() POSTs and tolerates an empty 204 response', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(api.logout()).resolves.toBeUndefined();
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/auth/logout');
    expect((call[1] as RequestInit).method).toBe('POST');
  });

  it('resetTournament() posts to the tournament path without a body', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ message: 'Reset complete' }));
    const result = await api.resetTournament('tn-7');
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/tournaments/tn-7/reset');
    expect((call[1] as RequestInit).method).toBe('POST');
    expect((call[1] as RequestInit).body).toBeUndefined();
    expect(result).toEqual({ message: 'Reset complete' });
  });
});
