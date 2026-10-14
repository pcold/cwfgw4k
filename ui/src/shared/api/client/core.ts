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
    for (const [k, v] of Object.entries(value)) {
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
    for (const [k, v] of Object.entries(value)) {
      out[snakeToCamel(k)] = camelizeKeys(v);
    }
    return out;
  }
  return value;
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

async function readJson<T>(resp: Response): Promise<T> {
  const text = await resp.text();
  const parsed: unknown = text ? JSON.parse(text) : null;
  return camelizeKeys(parsed) as T;
}

/** `path` is relative to `/api/v1`, as it is for every helper in this file. */
export async function getJson<T>(path: string): Promise<T> {
  return readJson<T>(await request(path));
}

export async function postJson<T>(path: string, payload?: unknown): Promise<T> {
  const init: RequestInit = { method: 'POST' };
  if (payload !== undefined) {
    init.headers = { 'Content-Type': 'application/json' };
    init.body = JSON.stringify(snakeifyKeys(payload));
  }
  return readJson<T>(await request(path, init));
}

export async function putJson<T>(path: string, payload: unknown): Promise<T> {
  const init: RequestInit = {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(snakeifyKeys(payload)),
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
