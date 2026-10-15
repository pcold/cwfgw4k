import { useState, type ReactNode } from 'react';
import { useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { League } from '@/shared/api/types';
import { FIELD_CLASS, FieldShell } from '@/shared/components/formFields';

export interface AdminLeagueChoice {
  leagues: League[];
  /** Empty until the leagues load. */
  leagueId: string;
  setLeagueId: (id: string) => void;
}

export function useAdminLeague(): AdminLeagueChoice {
  const leaguesQuery = useQuery<League[]>({ queryKey: queryKeys.leagues.root, queryFn: api.leagues });
  const [userLeagueId, setUserLeagueId] = useState<string>('');
  const leagues = leaguesQuery.data ?? [];
  return { leagues, leagueId: userLeagueId || (leagues[0]?.id ?? ''), setLeagueId: setUserLeagueId };
}

interface AdminLeagueFieldProps {
  id: string;
  leagues: League[];
  leagueId: string;
  onChange: (id: string) => void;
}

export function AdminLeagueField({ id, leagues, leagueId, onChange }: AdminLeagueFieldProps): ReactNode {
  return (
    <FieldShell id={id} label="League" error={undefined} width="sm:w-64">
      <select id={id} value={leagueId} onChange={(e) => onChange(e.target.value)} className={FIELD_CLASS}>
        {leagues.map((lg) => (
          <option key={lg.id} value={lg.id}>
            {lg.name}
          </option>
        ))}
      </select>
    </FieldShell>
  );
}
