import type { ReactNode } from 'react';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';

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
