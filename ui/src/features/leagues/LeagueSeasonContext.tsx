import { createContext, useContext, useMemo, useState, type ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import type { League, Season } from '@/shared/api/types';

interface LeagueSeasonValue {
  leagues: League[] | undefined;
  leaguesLoading: boolean;
  leaguesError: unknown;
  leagueId: string | null;
  setLeagueId: (id: string) => void;

  seasons: Season[] | undefined;
  seasonsLoading: boolean;
  seasonId: string | null;
  setSeasonId: (id: string) => void;

  live: boolean;
  setLive: (live: boolean) => void;
}

const LeagueSeasonContext = createContext<LeagueSeasonValue | null>(null);

export function LeagueSeasonProvider({ children }: { children: ReactNode }) {
  const leaguesQuery = useQuery({ queryKey: ['leagues'], queryFn: api.leagues });
  const [userLeagueId, setUserLeagueId] = useState<string | null>(null);
  // Derive the active league from the user's pick + loaded leagues so we don't
  // have to sync state-to-state in a useEffect on first load.
  const leagueId = userLeagueId ?? leaguesQuery.data?.[0]?.id ?? null;

  const seasonsQuery = useQuery({
    queryKey: ['seasons', leagueId],
    queryFn: leagueId === null ? skipToken : () => api.seasons(leagueId),
  });

  const [userSeasonId, setUserSeasonId] = useState<string | null>(null);
  // Same shape as leagueId, but with a "pick is no longer valid" guard so a
  // league switch falls back to the new league's first season instead of
  // pinning to a stale id.
  const seasonId =
    userSeasonId !== null && seasonsQuery.data?.some((s) => s.id === userSeasonId)
      ? userSeasonId
      : seasonsQuery.data?.[0]?.id ?? null;

  const [live, setLive] = useState(true);

  const value = useMemo<LeagueSeasonValue>(
    () => ({
      leagues: leaguesQuery.data,
      leaguesLoading: leaguesQuery.isLoading,
      leaguesError: leaguesQuery.error,
      leagueId,
      setLeagueId: (id) => {
        setUserLeagueId(id);
        setUserSeasonId(null);
      },
      seasons: seasonsQuery.data,
      seasonsLoading: seasonsQuery.isLoading,
      seasonId,
      setSeasonId: setUserSeasonId,
      live,
      setLive,
    }),
    [
      leaguesQuery.data,
      leaguesQuery.isLoading,
      leaguesQuery.error,
      seasonsQuery.data,
      seasonsQuery.isLoading,
      leagueId,
      seasonId,
      live,
    ],
  );

  return <LeagueSeasonContext.Provider value={value}>{children}</LeagueSeasonContext.Provider>;
}

export function useLeagueSeason(): LeagueSeasonValue {
  const ctx = useContext(LeagueSeasonContext);
  if (!ctx) throw new Error('useLeagueSeason must be used inside a LeagueSeasonProvider');
  return ctx;
}
