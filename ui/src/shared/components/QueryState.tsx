import type { ReactNode } from 'react';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';

export interface QueryStateLike<T> {
  isLoading: boolean;
  isError: boolean;
  error: unknown;
  data: T | undefined;
}

interface QueryStateProps<T> {
  query: QueryStateLike<T>;
  label: string;
  children: (data: T) => ReactNode;
}

export function QueryState<T>({ query, label, children }: QueryStateProps<T>): ReactNode {
  if (query.isLoading) {
    return <p className="text-gray-400">Loading {label}…</p>;
  }
  if (query.isError) {
    return (
      <p className="text-red-400">
        Failed to load {label}: {String(query.error)}
      </p>
    );
  }
  if (query.data === undefined) return null;
  return <>{children(query.data)}</>;
}

export function useLeaguesGate(): ReactNode | null {
  const { leagues, leaguesLoading, leaguesError } = useLeagueSeason();
  if (leaguesLoading) {
    return <p className="text-gray-400">Loading leagues…</p>;
  }
  if (leaguesError) {
    return (
      <p className="text-red-400">Failed to load leagues: {String(leaguesError)}</p>
    );
  }
  if (!leagues || leagues.length === 0) {
    return <p className="text-gray-400">No leagues configured.</p>;
  }
  return null;
}
