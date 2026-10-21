import type { ReactNode } from 'react';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';

/** Null once at least one league has loaded; anything else is the line a page renders in its body's place. */
export function useLeaguesGate(): ReactNode | null {
  const { leagues, leaguesLoading, leaguesError } = useLeagueSeason();
  if (leaguesLoading) {
    return <p className="text-ink-muted">Loading leagues…</p>;
  }
  if (leaguesError) {
    return (
      <p className="text-semantic-danger">Failed to load leagues: {String(leaguesError)}</p>
    );
  }
  if (!leagues || leagues.length === 0) {
    return <p className="text-ink-muted">No leagues configured.</p>;
  }
  return null;
}
