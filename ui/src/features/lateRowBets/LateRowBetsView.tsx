import type { ReportSideBetRules, WeeklyReport } from '@/shared/api/types';
import { formatMoney } from '@/shared/util/money';
import {
  lateRowBetRounds,
  lateRowBetTeamTotals,
  summarizeLateRowBets,
  type LateRowBetEntry,
} from './lateRowBetsModel';

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
      <div className="overflow-x-auto -mx-2 sm:mx-0">
        <table className="w-full text-xs min-w-[480px]">
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
                <td className="px-2 py-1.5 uppercase">{entry.teamName}</td>
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

const ROUND_LIST = new Intl.ListFormat('en', { style: 'long', type: 'conjunction' });

interface RulesNoteProps {
  rules: ReportSideBetRules;
}

// Worded to stay true of a tied round, where several teams collect and split
// what the teams behind them paid.
function RulesNote({ rules }: RulesNoteProps) {
  const amount = formatMoney(rules.amountPerTeam);
  const rounds = ROUND_LIST.format(rules.rounds.map(String));
  const where = rules.rounds.length === 1 ? `draft round ${rounds}` : `each of draft rounds ${rounds}`;
  return (
    <p className="text-gray-400 text-sm">
      {amount} per team in {where}. Every team behind a round&apos;s highest cumulative earner
      pays {amount}; teams tied for the highest split what was paid.
    </p>
  );
}

interface Props {
  report: WeeklyReport;
}

function LateRowBetsView({ report }: Props) {
  const rounds = lateRowBetRounds(report);
  const totals = lateRowBetTeamTotals(report);
  const summary = summarizeLateRowBets(report);

  if (rounds.length === 0) {
    return <p className="text-gray-500 text-sm">This season has no late row bets.</p>;
  }

  return (
    <section className="space-y-4">
      {report.sideBetRules ? <RulesNote rules={report.sideBetRules} /> : null}

      <div className="space-y-6">
        {rounds.map((rd) => (
          <RoundTable key={rd.round} round={rd.round} entries={rd.entries} />
        ))}
      </div>

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
                    <td className="px-2 py-1.5 uppercase">{team.teamName}</td>
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
