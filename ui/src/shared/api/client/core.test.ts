import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest';
import { ApiError, deleteNoContent, getJson, postJson } from './core';

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
