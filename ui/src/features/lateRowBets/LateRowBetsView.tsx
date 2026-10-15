import type { ReactNode } from 'react';
import type { ReportSideBetRules, WeeklyReport } from '@/shared/api/types';
import { formatMoney } from '@/shared/util/money';
import { lateRowBetRounds, lateRowBetTeamTotals, type LateRowBetEntry } from './lateRowBetsModel';

function payoutClass(payout: number): string {
  if (payout > 0) return 'text-semantic-success';
  if (payout < 0) return 'text-semantic-danger';
  return 'text-ink-soft';
}

function signClass(value: number): string {
  if (value > 0) return 'text-semantic-success';
  if (value < 0) return 'text-semantic-danger';
  return 'text-ink-soft';
}

interface RoundTableProps {
  round: number;
  entries: LateRowBetEntry[];
}

function RoundTable({ round, entries }: RoundTableProps) {
  return (
    <div className="bg-surface-card rounded-lg p-4">
      <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
        Round {round}
      </h3>
      <div className="overflow-x-auto -mx-2 sm:mx-0">
        <table className="w-full text-xs min-w-[480px]">
          <thead>
            <tr className="text-ink-muted">
              <th className="text-left px-2 py-1">Team</th>
              <th className="text-left px-2 py-1">Golfer</th>
              <th className="text-right px-2 py-1">Cumulative $</th>
              <th className="text-right px-2 py-1">Late Row Bet</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.teamId} className="border-t border-hairline">
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
    <p className="text-ink-muted text-sm">
      {amount} per team in {where}. Every team behind a round&apos;s highest cumulative earner
      pays {amount}; teams tied for the highest split what was paid.
    </p>
  );
}

interface Props {
  report: WeeklyReport;
}

function LateRowBetsView({ report }: Props): ReactNode {
  const rounds = lateRowBetRounds(report);
  const totals = lateRowBetTeamTotals(report);
  const { sideBetTotals } = report;

  if (rounds.length === 0) {
    return <p className="text-ink-soft text-sm">This season has no late row bets.</p>;
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
        <div className="bg-surface-card rounded-lg p-4">
          <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
            Total Late Row Bets (All Rounds)
          </h3>
          <div className="overflow-x-auto">
            <table className="w-full text-xs">
              <thead>
                <tr className="text-ink-muted">
                  <th className="text-left px-2 py-1">Team</th>
                  <th className="text-right px-2 py-1">Total</th>
                </tr>
              </thead>
              <tbody>
                {totals.map((team) => (
                  <tr key={team.teamId} className="border-t border-hairline">
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
          <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
            <span className="text-ink-soft">Won</span>
            <span className="font-semibold text-semantic-success tabular-nums">
              {formatMoney(sideBetTotals.won)}
            </span>
          </div>
          <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
            <span className="text-ink-soft">Lost</span>
            <span className="font-semibold text-semantic-danger tabular-nums">
              {formatMoney(sideBetTotals.lost)}
            </span>
          </div>
          <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
            <span className="text-ink-soft">Net</span>
            <span className={`font-semibold tabular-nums ${signClass(sideBetTotals.net)}`}>
              {formatMoney(sideBetTotals.net)}
            </span>
          </div>
        </div>
      ) : null}
    </section>
  );
}

export default LateRowBetsView;
