import { useMemo, useState, type ReactNode } from 'react';
import { formatDollarsAndCents } from '@/shared/util/money';
import ColumnHeaderFilter, {
  type ColumnHeaderFilterOption,
} from '@/shared/components/ColumnHeaderFilter';
import type { PlayerRankingsRow } from '@/shared/api/types';

interface Props {
  players: PlayerRankingsRow[];
  live: boolean;
  onGolferClick?: (golferId: string) => void;
}

const ALL = '';
const UNDRAFTED = '__undrafted__';

function PlayerRankingsView({ players, live, onGolferClick }: Props): ReactNode {
  const [teamFilter, setTeamFilter] = useState<string>(ALL);
  const [roundFilter, setRoundFilter] = useState<string>(ALL);

  const teamOptions = useMemo<ColumnHeaderFilterOption[]>(() => {
    const names = new Set<string>();
    let hasUndrafted = false;
    for (const p of players) {
      if (p.teamName) names.add(p.teamName);
      else hasUndrafted = true;
    }
    const sorted = Array.from(names).sort((a, b) => a.localeCompare(b));
    return [
      { value: ALL, label: 'Team' },
      ...sorted.map((name) => ({ value: name, label: name })),
      ...(hasUndrafted ? [{ value: UNDRAFTED, label: 'Undrafted' }] : []),
    ];
  }, [players]);

  const roundOptions = useMemo<ColumnHeaderFilterOption[]>(() => {
    const rounds = new Set<number>();
    let hasUndrafted = false;
    for (const p of players) {
      if (p.draftRound !== null) rounds.add(p.draftRound);
      else hasUndrafted = true;
    }
    const sorted = Array.from(rounds).sort((a, b) => a - b);
    return [
      { value: ALL, label: 'Round' },
      ...sorted.map((r) => ({ value: String(r), label: String(r) })),
      ...(hasUndrafted ? [{ value: UNDRAFTED, label: 'Undrafted' }] : []),
    ];
  }, [players]);

  const filtered = useMemo(() => {
    const matchesTeam = (p: PlayerRankingsRow) => {
      if (teamFilter === ALL) return true;
      if (teamFilter === UNDRAFTED) return !p.teamName;
      return p.teamName === teamFilter;
    };
    const matchesRound = (p: PlayerRankingsRow) => {
      if (roundFilter === ALL) return true;
      if (roundFilter === UNDRAFTED) return p.draftRound === null;
      return String(p.draftRound) === roundFilter;
    };
    return players.filter((p) => matchesTeam(p) && matchesRound(p));
  }, [players, teamFilter, roundFilter]);

  if (players.length === 0) {
    return <p className="text-ink-muted">No players with a top 10 finish yet.</p>;
  }

  return (
    <section className="space-y-4">
      <header className="border-b border-hairline pb-3">
        <h2 className="text-xl font-semibold text-primary-ink">Player Rankings</h2>
        <p className="text-xs text-ink-muted">
          {players.length} players with at least one top 10
          {live ? ' · live overlay on' : ''}
        </p>
      </header>

      <div className="overflow-x-auto -mx-3 sm:mx-0">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-ink-soft">
              <th className="px-3 py-2 w-12">#</th>
              <th className="px-3 py-2">Player</th>
              <th className="px-3 py-2 text-right">Top 10s</th>
              <th className="px-3 py-2 text-right">Total $</th>
              <th className="px-3 py-2">
                <ColumnHeaderFilter
                  ariaLabel="Filter by team"
                  value={teamFilter}
                  onChange={setTeamFilter}
                  options={teamOptions}
                />
              </th>
              <th className="px-3 py-2 text-right">
                <ColumnHeaderFilter
                  ariaLabel="Filter by draft round"
                  value={roundFilter}
                  onChange={setRoundFilter}
                  options={roundOptions}
                />
              </th>
            </tr>
          </thead>
          <tbody className="divide-y divide-hairline-soft">
            {filtered.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-3 py-6 text-center text-ink-soft italic">
                  No players match this filter.
                </td>
              </tr>
            ) : (
              filtered.map((player, index) => {
                const golferId = player.golferId;
                const clickable = golferId && onGolferClick ? { golferId, onGolferClick } : null;
                return (
                  <tr key={player.key} className="hover:bg-surface-card/60">
                    <td className="px-3 py-2 text-ink-soft tabular-nums">{index + 1}</td>
                    <td className="px-3 py-2 font-medium text-ink">
                      {clickable ? (
                        <button
                          type="button"
                          className="text-left hover:text-primary-ink hover:underline"
                          onClick={() => clickable.onGolferClick(clickable.golferId)}
                        >
                          {player.name}
                        </button>
                      ) : (
                        player.name
                      )}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums">{player.topTens}</td>
                    <td className="px-3 py-2 text-right tabular-nums text-semantic-success">
                      {formatDollarsAndCents(player.totalEarnings)}
                    </td>
                    <td className="px-3 py-2 text-ink-secondary">
                      {player.teamName ? (
                        <span className="uppercase">{player.teamName}</span>
                      ) : (
                        <span className="text-ink-soft italic">undrafted</span>
                      )}
                    </td>
                    <td className="px-3 py-2 text-right tabular-nums text-ink-muted">
                      {player.draftRound ?? '—'}
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>
    </section>
  );
}

export default PlayerRankingsView;
