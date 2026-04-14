import type { WeeklyReport } from '../api/types';
import {
  lateRowBetRounds,
  lateRowBetTeamTotals,
  summarizeLateRowBets,
  type LateRowBetEntry,
} from './lateRowBetsModel';

const money = new Intl.NumberFormat('en-US', {
  style: 'currency',
  currency: 'USD',
  minimumFractionDigits: 0,
  maximumFractionDigits: 0,
});

function formatMoney(value: number): string {
  return money.format(value);
}

function payoutClass(payout: number): string {
  if (payout > 0) return 'text-green-400';
  if (payout < 0) return 'text-red-400';
  return 'text-gray-500';
}

function signClass(value: number): string {
  if (value > 0) return 'text-green-400';
  if (value < 0) return 'text-red-400';
  return 'text-gray-500';
}

interface RoundTableProps {
  round: number;
  entries: LateRowBetEntry[];
}

function RoundTable({ round, entries }: RoundTableProps) {
  return (
    <div className="bg-gray-800 rounded-lg p-4">
      <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
        Round {round}
      </h3>
      <div className="overflow-x-auto">
        <table className="w-full text-xs">
          <thead>
            <tr className="text-gray-400">
              <th className="text-left px-2 py-1">Team</th>
              <th className="text-left px-2 py-1">Golfer</th>
              <th className="text-right px-2 py-1">Cumulative $</th>
              <th className="text-right px-2 py-1">Late Row Bet</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.teamId} className="border-t border-gray-700">
                <td className="px-2 py-1.5">{entry.teamName}</td>
                <td className="px-2 py-1.5 font-mono">{entry.golferName}</td>
                <td className="px-2 py-1.5 text-right font-mono">
                  {formatMoney(entry.cumulativeEarnings)}
                </td>
                <td
                  className={`px-2 py-1.5 text-right font-mono font-bold ${payoutClass(entry.payout)}`}
                >
                  {formatMoney(entry.payout)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

interface Props {
  report: WeeklyReport;
}

function LateRowBetsView({ report }: Props) {
  const rounds = lateRowBetRounds(report);
  const totals = lateRowBetTeamTotals(report);
  const summary = summarizeLateRowBets(report);

  return (
    <section className="space-y-4">
      <p className="text-gray-400 text-sm">
        $15 per team per round. Highest cumulative earner in each draft round (5-8) wins $15
        from every other team.
      </p>

      {rounds.length === 0 ? (
        <p className="text-gray-500 text-sm">No late row bets yet.</p>
      ) : (
        <div className="space-y-6">
          {rounds.map((rd) => (
            <RoundTable key={rd.round} round={rd.round} entries={rd.entries} />
          ))}
        </div>
      )}

      {totals.length > 0 ? (
        <div className="bg-gray-800 rounded-lg p-4">
          <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
            Total Late Row Bets (All Rounds)
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="text-gray-400">
                  <th className="text-left px-2 py-1">Team</th>
                  <th className="text-right px-2 py-1">Total</th>
                </tr>
              </thead>
              <tbody>
                {totals.map((team) => (
                  <tr key={team.teamId} className="border-t border-gray-700">
                    <td className="px-2 py-1.5">{team.teamName}</td>
                    <td
                      className={`px-2 py-1.5 text-right font-mono font-bold ${signClass(team.sideBets)}`}
                    >
                      {formatMoney(team.sideBets)}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        </div>
      ) : null}

      {report.teams.length > 0 ? (
        <div className="flex flex-wrap gap-3 text-sm">
          <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
            <span className="text-gray-500">Won</span>
            <span className="font-semibold text-green-400 tabular-nums">
              {formatMoney(summary.totalWon)}
            </span>
          </div>
          <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
            <span className="text-gray-500">Lost</span>
            <span className="font-semibold text-red-400 tabular-nums">
              {formatMoney(summary.totalLost)}
            </span>
          </div>
          <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
            <span className="text-gray-500">Net</span>
            <span className={`font-semibold tabular-nums ${signClass(summary.net)}`}>
              {formatMoney(summary.net)}
            </span>
          </div>
        </div>
      ) : null}
    </section>
  );
}

export default LateRowBetsView;
