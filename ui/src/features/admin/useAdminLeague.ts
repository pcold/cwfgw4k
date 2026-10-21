import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { League } from '@/shared/api/types';

/** @see useAdminLeague */
export interface AdminLeagueChoice {
  leagues: League[];
  /** Empty until the leagues load. */
  leagueId: string;
  setLeagueId: (id: string) => void;
}

/** Falls back to the first league until the admin picks one. */
export function useAdminLeague(): AdminLeagueChoice {
  const leaguesQuery = useQuery<League[]>({ queryKey: queryKeys.leagues.root, queryFn: api.leagues });
  const [userLeagueId, setUserLeagueId] = useState<string>('');
  const leagues = leaguesQuery.data ?? [];
  return { leagues, leagueId: userLeagueId || (leagues[0]?.id ?? ''), setLeagueId: setUserLeagueId };
}
