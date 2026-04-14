import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError, api } from './client';

type FetchMock = ReturnType<typeof vi.fn>;

function mockJson(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  });
}

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
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/seasons?leagueId=league%2Fwith%20space');
  });

  it('seasonReport() appends live=true only when requested', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(mockJson({})));
    await api.seasonReport('season-1', false);
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/season-1/report');
    await api.seasonReport('season-1', true);
    expect(fetchMock).toHaveBeenLastCalledWith('/api/v1/seasons/season-1/report?live=true');
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
});
