import type { WeeklyReport } from '../api/types';
import { formatMoney } from '../util/money';
import { deriveScoreboard, formatScoreToPar } from './scoreboardModel';

function formatSigned(value: number): string {
  if (value > 0) return `+${formatMoney(value)}`;
  return formatMoney(value);
}

function rowTint(weeklyTotal: number): string {
  if (weeklyTotal > 0) return 'bg-green-900/10';
  if (weeklyTotal < 0) return 'bg-red-900/10';
  return '';
}

function signColor(value: number): string {
  if (value > 0) return 'text-green-400';
  if (value < 0) return 'text-red-400';
  return 'text-gray-400';
}

interface Props {
  report: WeeklyReport;
}

function ScoreboardView({ report }: Props) {
  const scoreboard = deriveScoreboard(report);
  const tournament = report.tournament;
  const statusBanner = tournament.status === 'completed' ? 'Results finalized' : 'Tournament in progress — scores are live estimates';
  const bannerClass =
    tournament.status === 'completed'
      ? 'bg-green-900/30 border-green-700 text-green-300'
      : 'bg-yellow-900/40 border-yellow-700 text-yellow-300';

  return (
    <section className="space-y-6">
      <header className="border-b border-gray-700 pb-3">
        <h2 className="text-xl font-semibold text-green-400">
          {tournament.name ?? 'Scoreboard'}
        </h2>
        <p className="text-xs text-gray-400">
          Week {tournament.week ?? '?'}
          {tournament.payoutMultiplier !== 1 ? ` · ${tournament.payoutMultiplier}x payout` : ''}
        </p>
      </header>

      <div className={`px-4 py-2 border rounded text-sm ${bannerClass}`}>{statusBanner}</div>

      <div className="overflow-x-auto">
        <table className="w-full text-sm min-w-[500px]">
          <thead className="bg-gray-800 text-gray-300 text-xs uppercase tracking-wider">
            <tr>
              <th className="px-4 py-3 text-left">#</th>
              <th className="px-4 py-3 text-left">Team</th>
              <th className="px-4 py-3 text-right">Top 10 Earnings</th>
              <th className="px-4 py-3 text-right">Weekly +/-</th>
              <th className="px-4 py-3 text-left">Golfers in Top 10</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-gray-800">
            {scoreboard.teams.map((team, i) => (
              <tr key={team.teamId} className={`hover:bg-gray-800/60 ${rowTint(team.weeklyTotal)}`}>
                <td className="px-4 py-3 text-gray-500">{i + 1}</td>
                <td className="px-4 py-3 font-medium text-gray-100">{team.teamName}</td>
                <td className="px-4 py-3 text-right tabular-nums text-gray-300">
                  {formatMoney(team.topTenEarnings)}
                </td>
                <td
                  className={`px-4 py-3 text-right tabular-nums font-semibold ${signColor(team.weeklyTotal)}`}
                >
                  {formatSigned(team.weeklyTotal)}
                </td>
                <td className="px-4 py-3">
                  {team.golferScores.length === 0 ? (
                    <span className="text-gray-600">—</span>
                  ) : (
                    team.golferScores.map((g) => (
                      <span key={`${team.teamId}-${g.golferId ?? g.golferName}`} className="inline-block mr-3 text-xs">
                        <span className="text-gray-300">{g.golferName}</span>{' '}
                        <span className="text-gray-500">T</span>
                        <span className="text-gray-400">{g.position ?? '?'}</span>{' '}
                        <span className="text-green-400 tabular-nums">{formatMoney(g.payout)}</span>
                        {g.ownershipPct < 100 ? (
                          <span className="text-gray-500"> ({g.ownershipPct}%)</span>
                        ) : null}
                      </span>
                    ))
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-wrap gap-3 text-sm">
        <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
          <span className="text-gray-500">Won</span>
          <span className="font-semibold text-green-400 tabular-nums">
            {formatMoney(scoreboard.totalWon)}
          </span>
        </div>
        <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
          <span className="text-gray-500">Lost</span>
          <span className="font-semibold text-red-400 tabular-nums">
            {formatMoney(scoreboard.totalLost)}
          </span>
        </div>
        <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
          <span className="text-gray-500">Net</span>
          <span className={`font-semibold tabular-nums ${signColor(scoreboard.net)}`}>
            {formatMoney(scoreboard.net)}
          </span>
        </div>
      </div>

      {scoreboard.leaderboard.length > 0 ? (
        <div>
          <h3 className="text-xs font-semibold text-gray-500 uppercase tracking-wider mb-2">
            Leaderboard (Top 20)
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full text-sm min-w-[400px]">
              <thead className="bg-gray-800 text-gray-300 text-xs uppercase tracking-wider">
                <tr>
                  <th className="px-4 py-2 text-left">Pos</th>
                  <th className="px-4 py-2 text-left">Player</th>
                  <th className="px-4 py-2 text-right">Score</th>
                  <th className="px-4 py-2 text-left">Team</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-800">
                {scoreboard.leaderboard.map((entry) => (
                  <tr
                    key={entry.name}
                    className={entry.rostered ? 'bg-green-900/10' : ''}
                  >
                    <td className="px-4 py-2 text-gray-400">{entry.position ?? '—'}</td>
                    <td className="px-4 py-2 text-gray-100">{entry.name}</td>
                    <td className="px-4 py-2 text-right tabular-nums">
                      {formatScoreToPar(entry.scoreToPar)}
                    </td>
                    <td
                      className={`px-4 py-2 text-xs ${entry.teamName ? 'text-green-400' : 'text-gray-600'}`}
                    >
                      {entry.teamName ?? 'undrafted'}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}
    </section>
  );
}

export default ScoreboardView;
