import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { nth } from '@/shared/test/nth';
import { ApiError, api } from './index';
import { adminApi } from './admin';
import { authApi } from './auth';
import { golfersApi } from './golfers';
import { leaguesApi } from './leagues';
import { reportsApi } from './reports';
import { seasonsApi } from './seasons';
import { teamsApi } from './teams';
import { tournamentLinksApi } from './tournamentLinks';
import { tournamentsApi } from './tournaments';

type FetchMock = Mock<typeof fetch>;

function apiError(caught: unknown): ApiError {
  if (!(caught instanceof ApiError)) throw new Error(`Expected an ApiError, but caught ${String(caught)}.`);
  return caught;
}

function requestInit(call: Parameters<typeof fetch>): RequestInit {
  const init = call[1];
  if (!init) throw new Error('fetch was called with a URL and no RequestInit.');
  return init;
}

function jsonBody(call: Parameters<typeof fetch>): unknown {
  const body = requestInit(call).body;
  if (typeof body !== 'string') throw new Error('fetch was called without a string body.');
  return JSON.parse(body);
}

function mockJson(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

describe('api client', () => {
  let fetchMock: FetchMock;

  beforeEach(() => {
    fetchMock = vi.fn<typeof fetch>();
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

  it('seasonReport() asks for the finalized season report, with no query string', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({}));
    await api.seasonReport('season-1');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/seasons/season-1/report');
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
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/sn-1/rankings?live=true&through=tn-7');
  });

  it('returns the response body key for key', async () => {
    const wire = [
      {
        teamId: 't-1',
        teamName: 'Aces',
        picks: [{ round: 1, golferName: 'Scheffler', ownershipPct: 100 }],
      },
    ];
    fetchMock.mockResolvedValueOnce(mockJson(wire));
    const result = await api.rosters('sn-1');
    expect(result).toEqual(wire);
  });

  it('throws ApiError with the HTTP status when the response is not ok', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(new Response('nope', { status: 503, statusText: 'Boom' })));
    const err = await api.leagues().then(
      () => null,
      (e: unknown) => e,
    );
    expect(err).toBeInstanceOf(ApiError);
    expect(err).toMatchObject({ name: 'ApiError', status: 503 });
  });

  it('createSeason() sends the request as the body, key for key', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        id: 'sn-9',
        leagueId: 'lg-1',
        name: 'Spring',
        seasonYear: 2026,
        seasonNumber: 1,
      }),
    );
    const result = await api.createSeason({
      leagueId: 'lg-1',
      name: 'Spring',
      seasonYear: 2026,
      rules: { payouts: [18, 12], tieFloor: 1, sideBetRounds: [5, 6], sideBetAmount: 15 },
    });
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/seasons');
    const body = jsonBody(call);
    expect(body).toEqual({
      leagueId: 'lg-1',
      name: 'Spring',
      seasonYear: 2026,
      rules: {
        payouts: [18, 12],
        tieFloor: 1,
        sideBetRounds: [5, 6],
        sideBetAmount: 15,
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
    expect(apiError(err).message).toBe('Bad request');
    expect(apiError(err).status).toBe(400);
  });

  it('login() posts credentials and returns the User from the body', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        id: 'u-1',
        username: 'admin',
        role: 'admin',
        createdAt: '2026-01-01T00:00:00Z',
      }),
    );
    const result = await api.login('admin', 'secret');
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/auth/login');
    expect(jsonBody(call)).toEqual({
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
        createdAt: '2026-01-01T00:00:00Z',
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
    expect(apiError(err).status).toBe(500);
  });

  it('logout() POSTs and tolerates an empty 204 response', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(api.logout()).resolves.toBeUndefined();
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/auth/logout');
    expect(requestInit(call).method).toBe('POST');
  });

  it('resetTournament() posts to the tournament path without a body', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ message: 'Reset complete' }));
    const result = await api.resetTournament('tn-7');
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/tournaments/tn-7/reset');
    expect(requestInit(call).method).toBe('POST');
    expect(requestInit(call).body).toBeUndefined();
    expect(result).toEqual({ message: 'Reset complete' });
  });

  it('seasonRules() fetches the rules endpoint for the season', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({}));
    await api.seasonRules('sn-1');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/seasons/sn-1/rules');
  });

  it('defaultSeasonRules() fetches the default-rules endpoint', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({}));
    await api.defaultSeasonRules();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/seasons/default-rules');
  });

  it('tournaments() url-encodes the season id', async () => {
    fetchMock.mockResolvedValueOnce(mockJson([]));
    await api.tournaments('sn/1 a');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tournaments?season_id=sn%2F1%20a');
  });

  it('currentTournament() url-encodes the season id and returns the answer', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ type: 'unfinalized', tournamentId: 'tn-1' }));
    const current = await api.currentTournament('sn/1 a');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tournaments/current?season_id=sn%2F1%20a');
    expect(current).toEqual({ type: 'unfinalized', tournamentId: 'tn-1' });
  });

  it('resettableTournaments() url-encodes the season id', async () => {
    fetchMock.mockResolvedValueOnce(mockJson([]));
    await api.resettableTournaments('sn/1 a');
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/tournaments/resettable?season_id=sn%2F1%20a');
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

  it('updateTournament() PUTs the request as the body and url-encodes the id', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ id: 'tn-1', payoutMultiplier: 2 }));
    await api.updateTournament('tn/1', { payoutMultiplier: 2 });
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/tournaments/tn%2F1');
    expect(requestInit(call).method).toBe('PUT');
    expect(jsonBody(call)).toEqual({ payoutMultiplier: 2 });
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
    expect(apiError(err).status).toBe(409);
    expect(apiError(err).message).toBe('cannot edit completed');
  });

  it('previewSeasonSchedule() POSTs the date range with the season in the path', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ entries: [], skipped: [] }));
    await api.previewSeasonSchedule('sn 1', {
      startDate: '2026-01-01',
      endDate: '2026-04-01',
    });
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/admin/seasons/sn%201/upload/preview');
    expect(requestInit(call).method).toBe('POST');
    expect(jsonBody(call)).toEqual({
      startDate: '2026-01-01',
      endDate: '2026-04-01',
    });
  });

  it('confirmSeasonSchedule() POSTs the kept entries with the season in the path', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ created: [], skipped: [] }));
    await api.confirmSeasonSchedule('sn 1', {
      entries: [{ espnEventId: 'e-1', name: 'Sony Open', startDate: '2026-01-15', endDate: '2026-01-18' }],
    });
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/admin/seasons/sn%201/upload/confirm');
    expect(requestInit(call).method).toBe('POST');
    expect(jsonBody(call)).toEqual({
      entries: [{ espnEventId: 'e-1', name: 'Sony Open', startDate: '2026-01-15', endDate: '2026-01-18' }],
    });
  });

  it('previewRoster() POSTs the raw TSV body as text/plain', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({ teams: [], totalPicks: 0, matchedCount: 0, ambiguousCount: 0, unmatchedCount: 0 }),
    );
    const tsv = 'team_number\tteam_name\tround\tplayer_name\townership_pct\n1\tBROWN\t1\tScottie Scheffler\t';
    await api.previewRoster(tsv);
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/admin/roster/preview');
    expect(requestInit(call).headers).toEqual({ 'Content-Type': 'text/plain' });
    expect(requestInit(call).body).toBe(tsv);
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
    expect(apiError(err).message).toBe('header invalid');
  });

  it('confirmRoster() POSTs the envelope including the discriminated assignment', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ teamsCreated: 0, golfersCreated: 0, teams: [] }));
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
    const body = jsonBody(nth(fetchMock.mock.calls, 0));
    expect(body).toEqual({
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
  });

  it('finalizeTournament() POSTs to the finalize path and returns the parsed body', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        tournament: { id: 'tn-7', name: 'Sony', status: 'completed' },
        importWarnings: [{ tournamentId: 'tn-7', tournamentName: 'Sony', createdGolfers: [] }],
      }),
    );
    const result = await api.finalizeTournament('tn-7');
    expect(fetchMock).toHaveBeenCalledWith(
      '/api/v1/tournaments/tn-7/finalize',
      expect.objectContaining({ method: 'POST' }),
    );
    expect(result).toMatchObject({
      tournament: { id: 'tn-7', status: 'completed' },
      importWarnings: [{ tournamentId: 'tn-7', tournamentName: 'Sony', createdGolfers: [] }],
    });
  });

  it('cleanSeasonResults() returns the deletion counts from the body', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        scoresDeleted: 12,
        resultsDeleted: 3,
        tournamentsReset: 2,
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
      tournamentsReset: 2,
    });
  });

  it('deleteSeason() DELETEs the season path and url-encodes the id', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await expect(api.deleteSeason('sn 1')).resolves.toBeUndefined();
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/seasons/sn%201');
    expect(requestInit(call).method).toBe('DELETE');
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
    expect(apiError(err).status).toBe(409);
    expect(apiError(err).message).toBe('season has tournaments');
  });

  it('deleteSeason() falls back to the status line when the error body is not JSON', async () => {
    fetchMock.mockResolvedValueOnce(new Response('plain text whoops', { status: 500, statusText: 'Boom' }));
    const err = await api.deleteSeason('sn-1').then(
      () => null,
      (e: unknown) => e,
    );
    expect(apiError(err).message).toBe('500 Boom');
  });

  it('golfers() adds the trimmed search filter only when one is present', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(mockJson([])));
    await api.golfers();
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/golfers');
    await api.golfers('  fitz  ');
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/golfers?search=fitz');
    await api.golfers('   ');
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/golfers');
  });

  it('tournamentCompetitors() fetches the admin competitors endpoint and returns the body', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        tournamentId: 'tn-1',
        isFinalized: false,
        competitors: [
          {
            espnCompetitorId: 'team:1:1',
            name: 'Fitzpatrick',
            position: 1,
            isTeamPartner: true,
            linkedGolfer: null,
            hasOverride: false,
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

  it('upsertTournamentPlayerOverride() POSTs the request as the body to the player-overrides endpoint', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ tournamentId: 'tn-1', espnCompetitorId: 'abc', golferId: 'g-1' }));
    const result = await api.upsertTournamentPlayerOverride('tn-1', {
      espnCompetitorId: 'abc',
      golferId: 'g-1',
    });
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/admin/tournaments/tn-1/player-overrides');
    expect(requestInit(call).method).toBe('POST');
    const body = jsonBody(call);
    expect(body).toEqual({ espnCompetitorId: 'abc', golferId: 'g-1' });
    expect(result).toEqual({
      tournamentId: 'tn-1',
      espnCompetitorId: 'abc',
      golferId: 'g-1',
    });
  });

  it('deleteTournamentPlayerOverride() DELETEs and url-encodes both ids', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));
    await api.deleteTournamentPlayerOverride('tn 1', 'team:1:1');
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/admin/tournaments/tn%201/player-overrides/team%3A1%3A1');
    expect(requestInit(call).method).toBe('DELETE');
  });

  it('createGolferAndLink() POSTs the request as the body to the new-golfer endpoint', async () => {
    fetchMock.mockResolvedValueOnce(mockJson({ tournamentId: 'tn-1', espnCompetitorId: 'abc', golferId: 'g-99' }));
    const result = await api.createGolferAndLink('tn-1', { espnCompetitorId: 'abc', name: 'Alex Fitzpatrick' });
    const call = nth(fetchMock.mock.calls, 0);
    expect(call[0]).toBe('/api/v1/admin/tournaments/tn-1/player-overrides/new-golfer');
    expect(requestInit(call).method).toBe('POST');
    expect(jsonBody(call)).toEqual({ espnCompetitorId: 'abc', name: 'Alex Fitzpatrick' });
    expect(result.golferId).toBe('g-99');
  });

  it('dataHealth() fetches the admin data-health endpoint and returns each check', async () => {
    fetchMock.mockResolvedValueOnce(
      mockJson({
        rosteredTwins: { count: 0, rows: [] },
        rosteredWithoutEspnId: {
          count: 1,
          rows: [{ id: 'g-1', firstName: 'Si Woo', lastName: 'Kim', pgaPlayerId: null }],
        },
        blankFirstNames: { count: 0, rows: [] },
      }),
    );
    const result = await api.dataHealth();
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/admin/data-health');
    expect(result.rosteredWithoutEspnId).toEqual({
      count: 1,
      rows: [{ id: 'g-1', firstName: 'Si Woo', lastName: 'Kim', pgaPlayerId: null }],
    });
  });
});

describe('api', () => {
  it('takes every endpoint from exactly one slice, so no spread silently shadows another', () => {
    const slices = [
      adminApi,
      authApi,
      golfersApi,
      leaguesApi,
      reportsApi,
      seasonsApi,
      teamsApi,
      tournamentLinksApi,
      tournamentsApi,
    ];
    const declared = slices.flatMap((slice) => Object.keys(slice));

    expect(Object.keys(api)).toHaveLength(declared.length);
  });
});
