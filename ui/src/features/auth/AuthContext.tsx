import { createContext, useCallback, useContext, useEffect, useMemo, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient, type QueryClient } from '@tanstack/react-query';
import { ApiError, api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { onSessionExpired } from '@/shared/api/sessionExpiry';
import type { User, UserRole } from '@/shared/api/types';

interface AuthValue {
  authenticated: boolean;
  loading: boolean;
  username: string | null;
  role: UserRole | null;
  isAdmin: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  loginError: string | null;
  loginPending: boolean;
}

const AuthContext = createContext<AuthValue | null>(null);

/**
 * Re-ask `me` rather than set it to null: a 401 from a request sent before a fresh login must not
 * sign the new session out. Not a reset, which would unmount the page (and its unsaved input) while
 * `me` answers; `cancelRefetch: false` joins a burst of reports to the one re-ask in flight.
 */
function expireSession(queryClient: QueryClient): void {
  if (!queryClient.getQueryData<User | null>(queryKeys.auth.me())) return;
  void queryClient.refetchQueries({ queryKey: queryKeys.auth.me() }, { cancelRefetch: false });
}

export function AuthProvider({ children }: { children: ReactNode }): ReactNode {
  const queryClient = useQueryClient();

  // null = explicitly unauthenticated (the client swallows 401 from /me into null).
  // undefined = still loading.
  const authQuery = useQuery<User | null>({
    queryKey: queryKeys.auth.me(),
    queryFn: api.authMe,
    staleTime: Infinity,
  });

  useEffect(() => onSessionExpired(() => expireSession(queryClient)), [queryClient]);

  const loginMutation = useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      api.login(username, password),
    onSuccess: (user) => {
      queryClient.setQueryData<User | null>(queryKeys.auth.me(), user);
    },
  });

  const logoutMutation = useMutation({
    mutationFn: () => api.logout(),
    onSuccess: () => {
      queryClient.setQueryData<User | null>(queryKeys.auth.me(), null);
    },
  });

  const login = useCallback(
    async (username: string, password: string) => {
      await loginMutation.mutateAsync({ username, password });
    },
    [loginMutation],
  );

  const logout = useCallback(async () => {
    await logoutMutation.mutateAsync();
  }, [logoutMutation]);

  // Backend maps invalid-credentials to 401 (DomainError.Unauthorized).
  const loginError =
    loginMutation.error instanceof ApiError
      ? loginMutation.error.status === 401
        ? 'Invalid credentials'
        : loginMutation.error.message
      : loginMutation.error instanceof Error
        ? loginMutation.error.message
        : null;

  const role = authQuery.data?.role ?? null;
  const value = useMemo<AuthValue>(
    () => ({
      authenticated: !!authQuery.data,
      loading: authQuery.isLoading,
      username: authQuery.data?.username ?? null,
      role,
      isAdmin: role === 'admin',
      login,
      logout,
      loginError,
      loginPending: loginMutation.isPending,
    }),
    [
      authQuery.data,
      authQuery.isLoading,
      role,
      login,
      logout,
      loginError,
      loginMutation.isPending,
    ],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthValue {
  const ctx = useContext(AuthContext);
  if (!ctx) throw new Error('useAuth must be used inside an AuthProvider');
  return ctx;
}
