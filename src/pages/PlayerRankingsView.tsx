import { formatMoney } from '@/util/money';
import type { PlayerRankingsRow } from './playerRankingsModel';

interface Props {
  players: PlayerRankingsRow[];
  live?: boolean;
  onGolferClick?: (golferId: string) => void;
}

function PlayerRankingsView({ players, live, onGolferClick }: Props) {
  if (players.length === 0) {
    return <p className="text-gray-400">No players with a top 10 finish yet.</p>;
  }

  return (
    <section className="space-y-4">
      <header className="border-b border-gray-700 pb-3">
        <h2 className="text-xl font-semibold text-green-400">Player Rankings</h2>
        <p className="text-xs text-gray-400">
          {players.length} players with at least one top 10
          {live ? ' · live overlay on' : ''}
        </p>
      </header>

      <div className="overflow-x-auto">
        <table className="min-w-full text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-gray-500">
              <th className="px-3 py-2 w-12">#</th>
              <th className="px-3 py-2">Player</th>
              <th className="px-3 py-2 text-right">Top 10s</th>
              <th className="px-3 py-2 text-right">Total $</th>
              <th className="px-3 py-2">Team</th>
              <th className="px-3 py-2 text-right">Round</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-800">
            {players.map((player, index) => {
              const clickable = player.golferId && onGolferClick;
              return (
                <tr key={player.key} className="hover:bg-gray-800/60">
                  <td className="px-3 py-2 text-gray-500 tabular-nums">{index + 1}</td>
                  <td className="px-3 py-2 font-medium text-gray-100">
                    {clickable ? (
                      <button
                        type="button"
                        className="text-left hover:text-green-400 hover:underline"
                        onClick={() => onGolferClick!(player.golferId!)}
                      >
                        {player.name}
                      </button>
                    ) : (
                      player.name
                    )}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums">{player.topTens}</td>
                  <td className="px-3 py-2 text-right tabular-nums text-green-400">
                    {formatMoney(player.totalEarnings, 2)}
                  </td>
                  <td className="px-3 py-2 text-gray-300">
                    {player.teamName ?? <span className="text-gray-500 italic">undrafted</span>}
                  </td>
                  <td className="px-3 py-2 text-right tabular-nums text-gray-400">
                    {player.draftRound ?? '—'}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}

export default PlayerRankingsView;
