import { createContext, useCallback, useContext, useMemo, type ReactNode } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ApiError, api } from '@/shared/api/client';
import type { User } from '@/shared/api/types';

interface AuthValue {
  authenticated: boolean;
  loading: boolean;
  username: string | null;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  loginError: string | null;
  loginPending: boolean;
}

const AuthContext = createContext<AuthValue | null>(null);

const AUTH_KEY = ['auth', 'me'] as const;

export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  // null = explicitly unauthenticated (the client swallows 401 from /me into null).
  // undefined = still loading.
  const authQuery = useQuery<User | null>({
    queryKey: AUTH_KEY,
    queryFn: api.authMe,
    staleTime: Infinity,
  });

  const loginMutation = useMutation({
    mutationFn: ({ username, password }: { username: string; password: string }) =>
      api.login(username, password),
    onSuccess: (user) => {
      queryClient.setQueryData<User | null>(AUTH_KEY, user);
    },
  });

  const logoutMutation = useMutation({
    mutationFn: () => api.logout(),
    onSuccess: () => {
      queryClient.setQueryData<User | null>(AUTH_KEY, null);
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

  const value = useMemo<AuthValue>(
    () => ({
      authenticated: !!authQuery.data,
      loading: authQuery.isLoading,
      username: authQuery.data?.username ?? null,
      login,
      logout,
      loginError,
      loginPending: loginMutation.isPending,
    }),
    [
      authQuery.data,
      authQuery.isLoading,
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
