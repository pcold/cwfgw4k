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

  it('authMe() rethrows non-401 errors instead of swallowing them', async () => {
    fetchMock.mockResolvedValueOnce(new Response('boom', { status: 500, statusText: 'ISE' }));
    const err = await api.authMe().then(
      () => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(500);
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

  it('seasonRules() fetches the rules endpoint for the season', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({}));
    await api.seasonRules('sn-1');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/seasons/sn-1/rules');
  });

  it('tournaments() url-encodes the season id', async () => {
    fetchMock.mockResolvedValueOnce(mockJson([]));
    await api.tournaments('sn/1 a');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tournaments?season_id=sn%2F1%20a');
  });

  it('tournamentReport() appends live=true only when requested', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(mockJson({})));
    await api.tournamentReport('sn-1', 'tn-1', false);
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/sn-1/report/tn-1');
    await api.tournamentReport('sn-1', 'tn-1', true);
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/sn-1/report/tn-1?live=true');
  });

  it('golferHistory() fetches the golfer-history endpoint', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({}));
    await api.golferHistory('sn-1', 'g-1');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/seasons/sn-1/golfer/g-1/history');
  });

  it('updateTournament() PUTs a snake_case body and url-encodes the id', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ id: 'tn-1', payout_multiplier: 2 }));
    await api.updateTournament('tn/1', { payoutMultiplier: 2 });
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/tournaments/tn%2F1');
    expect((call[1] as RequestInit).method).toBe('PUT');
    expect(JSON.parse(String((call[1] as RequestInit).body))).toEqual({ payout_multiplier: 2 });
  });

  it('updateTournament() surfaces the server-supplied error message on PUT failure', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: 'cannot edit completed' }), {
        status: 409,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    const err = await api.updateTournament('tn-1', { payoutMultiplier: 2 }).then(
      () => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect((err as ApiError).status).toBe(409);
    expect((err as ApiError).message).toBe('cannot edit completed');
  });

  it('importSeasonSchedule() POSTs the date range with the season in the path', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ created: [], skipped: [] }));
    await api.importSeasonSchedule({
      seasonId: 'sn 1',
      startDate: '2026-01-01',
      endDate: '2026-04-01',
    });
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/admin/seasons/sn%201/upload');
    expect((call[1] as RequestInit).method).toBe('POST');
    expect(JSON.parse(String((call[1] as RequestInit).body))).toEqual({
      start_date: '2026-01-01',
      end_date: '2026-04-01',
    });
  });

  it('previewRoster() POSTs the raw TSV body as text/plain', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({ teams: [], total_picks: 0, matched_count: 0, ambiguous_count: 0, unmatched_count: 0 }),
    );
    const tsv = 'team_number\tteam_name\tround\tplayer_name\townership_pct\n1\tBROWN\t1\tScottie Scheffler\t';
    await api.previewRoster(tsv);
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/admin/roster/preview');
    expect((call[1] as RequestInit).headers).toEqual({ 'Content-Type': 'text/plain' });
    expect((call[1] as RequestInit).body).toBe(tsv);
  });

  it('previewRoster() surfaces the server error on a 400 response', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: 'header invalid' }), {
        status: 400,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    const err = await api.previewRoster('garbage').then(
      () => null,
      (e: unknown) => e,
    );
    expect((err as ApiError).message).toBe('header invalid');
  });

  it('confirmRoster() POSTs a snake_case envelope including the discriminated assignment', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ teams_created: 0, golfers_created: 0, teams: [] }));
    await api.confirmRoster({
      seasonId: 'sn-1',
      teams: [
        {
          teamNumber: 1,
          teamName: 'BROWN',
          picks: [
            {
              round: 1,
              ownershipPct: 75,
              assignment: { type: 'existing', golferId: 'g-1' },
            },
          ],
        },
      ],
    });
    const body = JSON.parse(String((fetchMock.mock.calls[0][1] as RequestInit).body));
    expect(body).toEqual({
      season_id: 'sn-1',
      teams: [
        {
          team_number: 1,
          team_name: 'BROWN',
          picks: [
            {
              round: 1,
              ownership_pct: 75,
              assignment: { type: 'existing', golfer_id: 'g-1' },
            },
          ],
        },
      ],
    });
  });

  it('finalizeTournament() POSTs to the finalize path and returns the parsed body', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ id: 'tn-7', name: 'Sony', status: 'completed' }));
    const result = await api.finalizeTournament('tn-7');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/tournaments/tn-7/finalize',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(result).toMatchObject({ id: 'tn-7', status: 'completed' });
  });

  it('cleanSeasonResults() returns the deletion counts from the body', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        scores_deleted: 12,
        results_deleted: 3,
        standings_deleted: 5,
        tournaments_reset: 2,
      }),
    );
    const result = await api.cleanSeasonResults('sn-1');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/seasons/sn-1/clean-results',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(result).toEqual({
      scoresDeleted: 12,
      resultsDeleted: 3,
      standingsDeleted: 5,
      tournamentsReset: 2,
    });
  });

  it('deleteSeason() DELETEs the season path and url-encodes the id', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(api.deleteSeason('sn 1')).resolves.toBeUndefined();
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/seasons/sn%201');
    expect((call[1] as RequestInit).method).toBe('DELETE');
  });

  it('deleteSeason() surfaces a server-supplied error message', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ error: 'season has tournaments' }), {
        status: 409,
        headers: { 'Content-Type': 'application/json' },
      }),
    );
    const err = await api.deleteSeason('sn-1').then(
      () => null,
      (e: unknown) => e,
    );
    expect((err as ApiError).status).toBe(409);
    expect((err as ApiError).message).toBe('season has tournaments');
  });

  it('deleteSeason() falls back to the status line when the error body is not JSON', async () => {
    fetchMock.mockResolvedValueOnce(new Response('plain text whoops', { status: 500, statusText: 'Boom' }));
    const err = await api.deleteSeason('sn-1').then(
      () => null,
      (e: unknown) => e,
    );
    expect((err as ApiError).message).toBe('500 Boom');
  });

  it('golfers() requests inactive too and adds the search filter when present', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(mockJson([])));
    await api.golfers();
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/golfers?active=false');
    await api.golfers('  fitz  ');
    expect(fetchMock).toHaveBeenLastCalledWith(
      '/api/v1/golfers?active=false&search=fitz',
    );
    await api.golfers('   ');
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/golfers?active=false');
  });

  it('tournamentCompetitors() fetches the admin competitors endpoint and camelizes the body', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        tournament_id: 'tn-1',
        is_finalized: false,
        competitors: [
          {
            espn_competitor_id: 'team:1:1',
            name: 'Fitzpatrick',
            position: 1,
            is_team_partner: true,
            linked_golfer: null,
            has_override: false,
          },
        ],
      }),
    );
    const result = await api.tournamentCompetitors('tn 1');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/admin/tournaments/tn%201/competitors');
    expect(result.isFinalized).toBe(false);
    expect(result.competitors[0]).toMatchObject({
      espnCompetitorId: 'team:1:1',
      isTeamPartner: true,
      hasOverride: false,
    });
  });

  it('upsertTournamentPlayerOverride() POSTs a snake_case body to the player-overrides endpoint', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({ tournament_id: 'tn-1', espn_competitor_id: 'abc', golfer_id: 'g-1' }),
    );
    const result = await api.upsertTournamentPlayerOverride('tn-1', {
      espnCompetitorId: 'abc',
      golferId: 'g-1',
    });
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/admin/tournaments/tn-1/player-overrides');
    expect((call[1] as RequestInit).method).toBe('POST');
    const body = JSON.parse(String((call[1] as RequestInit).body)) as Record<string, unknown>;
    expect(body).toEqual({ espn_competitor_id: 'abc', golfer_id: 'g-1' });
    expect(result).toEqual({
      tournamentId: 'tn-1',
      espnCompetitorId: 'abc',
      golferId: 'g-1',
    });
  });

  it('deleteTournamentPlayerOverride() DELETEs and url-encodes both ids', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await api.deleteTournamentPlayerOverride('tn 1', 'team:1:1');
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/admin/tournaments/tn%201/player-overrides/team%3A1%3A1');
    expect((call[1] as RequestInit).method).toBe('DELETE');
  });

  it('createGolfer() POSTs a snake_case body to /api/v1/golfers', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        id: 'g-99',
        pga_player_id: null,
        first_name: 'Alex',
        last_name: 'Fitzpatrick',
        country: null,
        world_ranking: null,
        active: true,
        updated_at: '2026-04-29T00:00:00Z',
      }),
    );
    const result = await api.createGolfer({ firstName: 'Alex', lastName: 'Fitzpatrick' });
    const call = fetchMock.mock.calls[0];
    expect(call[0]).toBe('/api/v1/golfers');
    expect((call[1] as RequestInit).method).toBe('POST');
    const body = JSON.parse(String((call[1] as RequestInit).body)) as Record<string, unknown>;
    expect(body).toEqual({ first_name: 'Alex', last_name: 'Fitzpatrick' });
    expect(result.firstName).toBe('Alex');
    expect(result.pgaPlayerId).toBeNull();
  });
});
