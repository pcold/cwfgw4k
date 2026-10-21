import { createContext, useCallback, useContext, useEffect, useMemo, useState, type ReactNode } from 'react';
import { useMatch, useSearchParams } from 'react-router';
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

/** Admin is the commissioner's tool, not a page a visitor lands on; a team preference has nothing to offer it. */
const NO_PICKER_PATH = '/admin';

interface MyTeamValue {
  /** Already checked against the newest season's teams. */
  readonly choice: MyTeamChoice;
  /** The newest season's teams, alphabetical; empty until they load. */
  readonly teamNames: readonly string[];
  /** False on Admin, and until the newest season has names to offer; Change is only offered where it can ask. */
  readonly canAsk: boolean;
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
 * Validates against `seasons[0]` through the Rosters page's own query key, so the two share one request and cache.
 * Reads no auth state and writes nothing but its own storage key: the choice is a preference and unlocks nothing.
 */
export function MyTeamProvider({ children }: { children: ReactNode }): ReactNode {
  const { seasons } = useLeagueSeason();
  const newestSeasonId = seasons?.[0]?.id ?? null;
  const rostersQuery = useQuery({
    queryKey: queryKeys.rosters.bySeason(newestSeasonId),
    queryFn: newestSeasonId === null ? skipToken : () => api.rosters(newestSeasonId),
  });
  const newestTeamNames = useMemo(() => rostersQuery.data?.map((team) => team.teamName), [rostersQuery.data]);

  const [saved, setSaved] = useState<MyTeamChoice>(() => parseMyTeam(readStoredMyTeam()));
  const [dismissed, setDismissed] = useState(false);
  const [searchParams, setSearchParams] = useSearchParams();
  const linkedTeam = searchParams.get(TEAM_PARAM);

  // Waits for the names, so an unmatched link can't be told from one read too early. Removed matched or not.
  useEffect(() => {
    if (linkedTeam === null || newestTeamNames === undefined) return;
    const matched = matchTeamName(linkedTeam, newestTeamNames);
    if (matched !== null) persist({ kind: 'team', teamName: matched }, setSaved);
    setSearchParams(
      (current) => {
        const remaining = new URLSearchParams(current);
        remaining.delete(TEAM_PARAM);
        return remaining;
      },
      { replace: true },
    );
  }, [linkedTeam, newestTeamNames, setSearchParams]);

  const onNoPickerPage = useMatch(NO_PICKER_PATH) !== null;
  const canAsk = !onNoPickerPage && newestTeamNames !== undefined && newestTeamNames.length > 0;
  const choice = validateMyTeam(saved, newestTeamNames);
  const asking = choice.kind === 'unasked' && canAsk && linkedTeam === null && !dismissed;

  const chooseTeam = useCallback(
    (teamName: string) => persist({ kind: 'team', teamName: normaliseTeamName(teamName) }, setSaved),
    [],
  );
  const chooseBrowsing = useCallback(() => persist({ kind: 'browsing' }, setSaved), []);
  const dismissPicker = useCallback(() => setDismissed(true), []);
  const changeTeam = useCallback(() => {
    persist(UNASKED, setSaved);
    setDismissed(false);
  }, []);

  const value = useMemo<MyTeamValue>(
    () => ({
      choice,
      teamNames: sortTeamNames(newestTeamNames ?? []),
      canAsk,
      asking,
      chooseTeam,
      chooseBrowsing,
      dismissPicker,
      changeTeam,
    }),
    [choice, newestTeamNames, canAsk, asking, chooseTeam, chooseBrowsing, dismissPicker, changeTeam],
  );

  return <MyTeamContext.Provider value={value}>{children}</MyTeamContext.Provider>;
}

export function useMyTeam(): MyTeamValue {
  const ctx = useContext(MyTeamContext);
  if (!ctx) throw new Error('useMyTeam must be used inside a MyTeamProvider');
  return ctx;
}
