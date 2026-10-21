import { createContext, useContext, useEffect, useState, type ReactNode } from 'react';
import { useSearchParams } from 'react-router';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import {
  UNASKED,
  matchTeamName,
  normaliseTeamName,
  parseMyTeam,
  serialiseMyTeam,
  sortTeamNames,
  validateMyTeam,
  type MyTeamChoice,
} from './myTeamModel';
import { readStoredMyTeam, writeStoredMyTeam } from './myTeamStorage';

/** A personal link, `/?team=womble`, which sets the team on a fresh device. */
const TEAM_PARAM = 'team';

interface MyTeamValue {
  /** Already checked against the newest season's teams. */
  readonly choice: MyTeamChoice;
  /** The newest season's teams, alphabetical; empty until they load. */
  readonly teamNames: readonly string[];
  /** Not route-aware: the picker decides where it may open. */
  readonly asking: boolean;
  readonly chooseTeam: (teamName: string) => void;
  readonly chooseBrowsing: () => void;
  /** Records nothing, so the next visit asks again. */
  readonly dismissPicker: () => void;
  /** Clears the choice and reopens the picker. */
  readonly changeTeam: () => void;
}

const MyTeamContext = createContext<MyTeamValue | null>(null);

function persist(choice: MyTeamChoice, setSaved: (choice: MyTeamChoice) => void): void {
  setSaved(choice);
  writeStoredMyTeam(serialiseMyTeam(choice));
}

/**
 * Validates against `seasons[0]` and reads the rosters cache the Rosters page fills, so nothing new is fetched.
 * Reads no auth state and writes nothing but its own storage key: the choice is a preference and unlocks nothing.
 */
export function MyTeamProvider({ children }: { children: ReactNode }): ReactNode {
  const { seasons } = useLeagueSeason();
  const newestSeasonId = seasons?.[0]?.id ?? null;
  const rostersQuery = useQuery({
    queryKey: queryKeys.rosters.bySeason(newestSeasonId),
    queryFn: newestSeasonId === null ? skipToken : () => api.rosters(newestSeasonId),
  });
  const newestRosters = rostersQuery.data;
  const newestTeamNames = newestRosters?.map((team) => team.teamName);

  const [saved, setSaved] = useState<MyTeamChoice>(() => parseMyTeam(readStoredMyTeam()));
  const [dismissed, setDismissed] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const linkedTeam = searchParams.get(TEAM_PARAM);

  // Waits for the names, so an unmatched link can't be told from one read too early. Removed matched or not.
  useEffect(() => {
    if (linkedTeam === null || newestRosters === undefined) return;
    const matched = matchTeamName(
      linkedTeam,
      newestRosters.map((team) => team.teamName),
    );
    if (matched !== null) persist({ kind: 'team', teamName: matched }, setSaved);
    setSearchParams(
      (current) => {
        const remaining = new URLSearchParams(current);
        remaining.delete(TEAM_PARAM);
        return remaining;
      },
      { replace: true },
    );
  }, [linkedTeam, newestRosters, setSearchParams]);

  const choice = validateMyTeam(saved, newestTeamNames);
  const value: MyTeamValue = {
    choice,
    teamNames: sortTeamNames(newestTeamNames ?? []),
    asking:
      choice.kind === 'unasked' &&
      newestTeamNames !== undefined &&
      newestTeamNames.length > 0 &&
      linkedTeam === null &&
      !dismissed,
    chooseTeam: (teamName) => persist({ kind: 'team', teamName: normaliseTeamName(teamName) }, setSaved),
    chooseBrowsing: () => persist({ kind: 'browsing' }, setSaved),
    dismissPicker: () => setDismissed(true),
    changeTeam: () => {
      persist(UNASKED, setSaved);
      setDismissed(false);
    },
  };

  return <MyTeamContext.Provider value={value}>{children}</MyTeamContext.Provider>;
}

export function useMyTeam(): MyTeamValue {
  const ctx = useContext(MyTeamContext);
  if (!ctx) throw new Error('useMyTeam must be used inside a MyTeamProvider');
  return ctx;
}
