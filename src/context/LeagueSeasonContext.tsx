import { createContext, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/api/client';
import type { League, Season } from '@/api/types';

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
  const [leagueId, setLeagueId] = useState<string | null>(null);

  useEffect(() => {
    if (!leagueId && leaguesQuery.data && leaguesQuery.data.length > 0) {
      setLeagueId(leaguesQuery.data[0].id);
    }
  }, [leaguesQuery.data, leagueId]);

  const seasonsQuery = useQuery({
    queryKey: ['seasons', leagueId],
    queryFn: () => api.seasons(leagueId!),
    enabled: !!leagueId,
  });

  const [seasonId, setSeasonId] = useState<string | null>(null);
  useEffect(() => {
    if (seasonsQuery.data && seasonsQuery.data.length > 0) {
      const stillValid = seasonId && seasonsQuery.data.some((s) => s.id === seasonId);
      if (!stillValid) setSeasonId(seasonsQuery.data[0].id);
    }
  }, [seasonsQuery.data, seasonId]);

  const [live, setLive] = useState(false);

  const value = useMemo<LeagueSeasonValue>(
    () => ({
      leagues: leaguesQuery.data,
      leaguesLoading: leaguesQuery.isLoading,
      leaguesError: leaguesQuery.error,
      leagueId,
      setLeagueId: (id) => {
        setLeagueId(id);
        setSeasonId(null);
      },
      seasons: seasonsQuery.data,
      seasonsLoading: seasonsQuery.isLoading,
      seasonId,
      setSeasonId,
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
