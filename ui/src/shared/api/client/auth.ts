import type { LoginRequest, User } from '../types';
import { ApiError, getJson, postJson, postNoContent } from './core';

export const authApi = {
  // /auth/me returns the User on success and 401 when not logged in. The
  // unauthenticated case is a normal app state, not an error, so swallow
  // 401 and return null. Any other status still throws.
  authMe: async (): Promise<User | null> => {
    try {
      return await getJson<User>('/auth/me');
    } catch (e) {
      if (e instanceof ApiError && e.status === 401) return null;
      throw e;
    }
  },
  login: (username: string, password: string): Promise<User> =>
    postJson<User>('/auth/login', { username, password } satisfies LoginRequest),
  logout: (): Promise<void> => postNoContent('/auth/logout'),
};
