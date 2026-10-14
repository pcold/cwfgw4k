import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { onSessionExpired } from '../sessionExpiry';
import { ApiError, deleteNoContent, getJson, postJson } from './core';
import { api } from './index';

function refusal(status: number, body: string, statusText = ''): Response {
  return new Response(body, { status, statusText });
}

async function rejection(pending: Promise<unknown>): Promise<ApiError> {
  const caught = await pending.then(
    () => null,
    (e: unknown) => e,
  );
  if (!(caught instanceof ApiError)) throw new Error(`Expected an ApiError, but caught ${String(caught)}.`);
  return caught;
}

describe('decoding a refused request', () => {
  let fetchMock: Mock<typeof fetch>;

  beforeEach(() => {
    fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('reads the error body of a refused read, as it does a refused write', async () => {
    fetchMock.mockResolvedValueOnce(refusal(404, JSON.stringify({ error: 'season not found' }), 'Not Found'));

    const error = await rejection(getJson('/seasons/sn-1/rules'));

    expect(error.status).toBe(404);
    expect(error.message).toBe('season not found');
  });

  it('reads the sentence a refusal carries beside its code', async () => {
    fetchMock.mockResolvedValueOnce(
      refusal(409, JSON.stringify({ refusal: 'tournament_finalized', error: 'tournament is finalized' })),
    );

    const error = await rejection(postJson('/tournaments/tn-1/reset'));

    expect(error.message).toBe('tournament is finalized');
  });

  it('falls back to the status line when a refused write carries no JSON', async () => {
    fetchMock.mockResolvedValueOnce(refusal(502, '<html>Bad Gateway</html>', 'Bad Gateway'));

    const error = await rejection(postJson('/leagues', { name: 'x' }));

    expect(error.status).toBe(502);
    expect(error.message).toBe('502 Bad Gateway');
  });

  it('falls back to the status line when the refusal body is empty', async () => {
    fetchMock.mockResolvedValueOnce(refusal(500, '', 'Server Error'));

    const error = await rejection(deleteNoContent('/seasons/sn-1'));

    expect(error.message).toBe('500 Server Error');
  });
});

describe('decoding an empty body', () => {
  let fetchMock: Mock<typeof fetch>;

  beforeEach(() => {
    fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal('fetch', fetchMock);
  });

  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it('refuses a read that answers with nothing, rather than handing its caller null', async () => {
    fetchMock.mockResolvedValueOnce(new Response('', { status: 200 }));

    await expect(getJson('/leagues')).rejects.toThrow(SyntaxError);
  });

  it('resolves a write that answers with nothing to null', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }));

    await expect(postJson('/tournaments/tn-1/reset')).resolves.toBeNull();
  });
});

describe('reporting an expired session', () => {
  const sessionExpired = vi.fn();
  let fetchMock: Mock<typeof fetch>;
  let unsubscribe: () => void = () => undefined;

  beforeEach(() => {
    fetchMock = vi.fn<typeof fetch>();
    vi.stubGlobal('fetch', fetchMock);
    sessionExpired.mockReset();
    unsubscribe = onSessionExpired(sessionExpired);
  });

  afterEach(() => {
    unsubscribe();
    vi.unstubAllGlobals();
  });

  const expired = (): Response => refusal(401, JSON.stringify({ error: 'authentication required' }));

  it('reports a read the server refuses', async () => {
    fetchMock.mockResolvedValue(expired());

    await expect(api.tournamentCompetitors('tn-1')).rejects.toThrow('authentication required');

    expect(sessionExpired).toHaveBeenCalledTimes(1);
  });

  it('reports a write the server refuses', async () => {
    fetchMock.mockResolvedValue(expired());

    await expect(api.createLeague('Castlewood')).rejects.toThrow('authentication required');

    expect(sessionExpired).toHaveBeenCalledTimes(1);
  });

  it('stays out of a rejected login, which the login modal answers itself', async () => {
    fetchMock.mockResolvedValue(refusal(401, JSON.stringify({ error: 'invalid username or password' })));

    await expect(api.login('admin', 'wrong')).rejects.toThrow('invalid username or password');

    expect(sessionExpired).not.toHaveBeenCalled();
  });

  it('stays out of the session check, whose 401 is the answer and not a symptom', async () => {
    fetchMock.mockResolvedValue(expired());

    await expect(api.authMe()).resolves.toBeNull();

    expect(sessionExpired).not.toHaveBeenCalled();
  });

  it('stays out of a refusal that is not a 401', async () => {
    fetchMock.mockResolvedValue(refusal(403, JSON.stringify({ error: 'admin role required' })));

    await expect(api.createLeague('Castlewood')).rejects.toThrow('admin role required');

    expect(sessionExpired).not.toHaveBeenCalled();
  });

  it('reports every refusal, leaving the subscriber to collapse a page-wide expiry', async () => {
    fetchMock.mockImplementation(() => Promise.resolve(expired()));

    await Promise.allSettled([api.leagues(), api.tournaments('sn-1'), api.golfers()]);

    expect(sessionExpired).toHaveBeenCalledTimes(3);
  });
});
