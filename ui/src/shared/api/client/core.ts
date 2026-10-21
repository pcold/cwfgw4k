import { notifySessionExpired } from '../sessionExpiry';

const BASE = '/api/v1';

// Their 401 answers the question asked rather than reporting an expiry: login's is a bad password,
// and `me`'s is how the expiry gets confirmed, so reporting it would loop.
const AUTH_PROBE_PATHS: readonly string[] = ['/auth/login', '/auth/me'];

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

// `ErrorBody` and `RefusalBody` both carry `error`; any other body, a proxy's HTML page included,
// leaves the status line to speak.
function errorBodyMessage(text: string): string | null {
  if (!text) return null;
  try {
    const parsed: unknown = JSON.parse(text);
    return parsed !== null && typeof parsed === 'object' && 'error' in parsed ? String(parsed.error) : null;
  } catch {
    return null;
  }
}

async function requestError(resp: Response): Promise<ApiError> {
  const message = errorBodyMessage(await resp.text()) ?? `${resp.status} ${resp.statusText}`;
  return new ApiError(resp.status, message);
}

async function request(path: string, init?: RequestInit): Promise<Response> {
  const url = `${BASE}${path}`;
  const resp = await (init === undefined ? fetch(url) : fetch(url, init));
  if (resp.ok) return resp;
  if (resp.status === 401 && !AUTH_PROBE_PATHS.includes(path)) notifySessionExpired();
  throw await requestError(resp);
}

function decodeJson<T>(parsed: unknown): T {
  return parsed as T;
}

// Only a write may answer with an empty body, which resolves to null.
async function readJson<T>(resp: Response): Promise<T> {
  const text = await resp.text();
  return decodeJson<T>(text ? JSON.parse(text) : null);
}

/**
 * `path` is relative to `/api/v1`, as it is for every helper in this file. An empty body throws
 * rather than reaching a reader as a null `T`.
 */
export async function getJson<T>(path: string): Promise<T> {
  const resp = await request(path);
  const parsed: unknown = await resp.json();
  return decodeJson<T>(parsed);
}

export async function postJson<T>(path: string, payload?: unknown): Promise<T> {
  const init: RequestInit = { method: 'POST' };
  if (payload !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = JSON.stringify(payload);
  }
  return readJson<T>(await request(path, init));
}

export async function putJson<T>(path: string, payload: unknown): Promise<T> {
  const init: RequestInit = {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload),
  };
  return readJson<T>(await request(path, init));
}

export async function postText<T>(path: string, body: string): Promise<T> {
  const init: RequestInit = { method: 'POST', headers: { 'Content-Type': 'text/plain' }, body };
  return readJson<T>(await request(path, init));
}

export async function postNoContent(path: string): Promise<void> {
  await request(path, { method: 'POST' });
}

export async function deleteNoContent(path: string): Promise<void> {
  await request(path, { method: 'DELETE' });
}
