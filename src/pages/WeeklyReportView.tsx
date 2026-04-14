import type { ReportRow, ReportTeamColumn, WeeklyReport } from '../api/types';
import { formatMoney } from '../util/money';
import {
  isSeasonReport,
  sideBetWinnersByRound,
  summarizeWeeklyReport,
} from './weeklyReportModel';

const ROUNDS = [1, 2, 3, 4, 5, 6, 7, 8] as const;

function signText(value: number): string {
  if (value > 0) return 'text-green-400';
  if (value < 0) return 'text-red-400';
  return 'text-gray-500';
}

function roundCellBg(hasEarnings: boolean, isSideBetWinner: boolean): string {
  if (isSideBetWinner) return 'bg-red-800';
  if (hasEarnings) return 'bg-yellow-300';
  return '';
}

function roundTextColor(hasEarnings: boolean, isSideBetWinner: boolean): string {
  return hasEarnings && !isSideBetWinner ? 'text-black' : 'text-gray-200';
}

interface RoundCellProps {
  row: ReportRow | undefined;
  isSideBetWinner: boolean;
  showSeasonFooter: boolean;
}

function RoundCell({ row, isSideBetWinner, showSeasonFooter }: RoundCellProps) {
  if (!row || !row.golferName) {
    return (
      <td className="border border-gray-700 px-1.5 py-1 text-center align-top">
        <span className="text-gray-600">—</span>
      </td>
    );
  }

  const hasEarnings = row.earnings > 0;
  const bg = roundCellBg(hasEarnings, isSideBetWinner);
  const text = roundTextColor(hasEarnings, isSideBetWinner);
  const earningsColor = hasEarnings
    ? isSideBetWinner
      ? 'text-green-400'
      : 'text-green-700'
    : 'text-gray-500';
  const ownership = row.ownershipPct < 100 ? ` (${row.ownershipPct}%)` : '';
  const earningsLabel = hasEarnings
    ? `${row.positionStr ?? ''} - ${formatMoney(row.earnings)}`
    : '-';
  const seasonEarningsLabel =
    (row.seasonTopTens ?? 0) > 0
      ? `${formatMoney(row.seasonEarnings ?? 0)} (${row.seasonTopTens ?? 0})`
      : '$0';

  return (
    <td className={`border border-gray-700 px-1.5 py-1 text-center align-top ${bg}`}>
      <div className={`font-semibold truncate ${text}`}>
        {row.golferName}
        {ownership}
      </div>
      <div className={`font-mono ${earningsColor}`}>{earningsLabel}</div>
      {showSeasonFooter ? (
        <div className={`font-semibold ${text}`}>{seasonEarningsLabel}</div>
      ) : null}
    </td>
  );
}

function teamRowsByRound(team: ReportTeamColumn): Map<number, ReportRow> {
  const byRound = new Map<number, ReportRow>();
  for (const row of team.rows) byRound.set(row.round, row);
  return byRound;
}

interface Props {
  report: WeeklyReport;
}

function WeeklyReportView({ report }: Props) {
  const { tournament, teams, undraftedTopTens, sideBetDetail } = report;
  const seasonMode = isSeasonReport(report);
  const showSeasonFooter = !seasonMode;
  const winnersByRound = sideBetWinnersByRound(sideBetDetail);
  const summary = summarizeWeeklyReport(report);
  const teamsByRound = teams.map((t) => ({ team: t, rows: teamRowsByRound(t) }));

  return (
    <section className="space-y-4">
      <header className="flex flex-wrap items-baseline justify-between gap-3 border-b border-gray-700 pb-3">
        <div>
          <h2 className="text-xl font-semibold text-green-400">
            {tournament.name ?? 'Weekly Report'}
          </h2>
          <p className="text-xs text-gray-400">
            {seasonMode
              ? 'Season totals'
              : `${tournament.startDate ?? ''}${tournament.endDate ? ` – ${tournament.endDate}` : ''}`}
            {tournament.payoutMultiplier > 1 ? ` · ${tournament.payoutMultiplier}x payouts` : ''}
            {tournament.status && tournament.status !== 'season' ? ` · ${tournament.status}` : ''}
          </p>
        </div>
        {report.live ? (
          <span className="text-xs bg-yellow-700/50 text-yellow-300 px-2 py-0.5 rounded">
            LIVE — projected standings
          </span>
        ) : null}
      </header>

      <div className="overflow-x-auto">
        <table className="w-full text-xs border-collapse">
          <thead>
            <tr className="bg-yellow-600 text-black font-bold">
              <th className="border border-gray-600 px-1 py-1.5 w-16 text-center">TEAM #</th>
              {teams.map((team, i) => (
                <th
                  key={team.teamId}
                  className="border border-gray-600 px-1 py-1.5 text-center min-w-[90px]"
                >
                  <div>{i + 1}</div>
                  <div className="text-xs">{team.teamName}</div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ROUNDS.map((round) => {
              const winners = winnersByRound.get(round) ?? new Set<string>();
              return (
                <tr key={round} className="bg-gray-800 hover:bg-gray-800/80">
                  <td className="border border-gray-700 px-1 py-1 text-center font-bold text-gray-400">
                    {round}
                  </td>
                  {teamsByRound.map(({ team, rows }) => (
                    <RoundCell
                      key={`${team.teamId}-${round}`}
                      row={rows.get(round)}
                      isSideBetWinner={winners.has(team.teamId)}
                      showSeasonFooter={showSeasonFooter}
                    />
                  ))}
                </tr>
              );
            })}

            <tr>
              <td colSpan={teams.length + 1} className="h-1 bg-gray-900" />
            </tr>

            <tr className="bg-gray-700 font-bold">
              <td className="border border-gray-600 px-1 py-1.5 text-center text-xs">TOP TENS</td>
              {teams.map((team) => (
                <td
                  key={`tt-${team.teamId}`}
                  className={`border border-gray-600 px-1 py-1.5 text-center font-mono ${
                    team.topTens > 0 ? 'text-green-400' : 'text-gray-500'
                  }`}
                >
                  {formatMoney(team.topTens)}
                </td>
              ))}
            </tr>

            <tr className="bg-gray-700 font-bold">
              <td className="border border-gray-600 px-1 py-1.5 text-center text-xs">
                {seasonMode ? '**SEASON' : '**WEEKLY'}
              </td>
              {teams.map((team) => (
                <td
                  key={`wt-${team.teamId}`}
                  className={`border border-gray-600 px-1 py-1.5 text-center font-mono ${signText(team.weeklyTotal)}`}
                >
                  {formatMoney(team.weeklyTotal)}
                </td>
              ))}
            </tr>

            <tr className="bg-gray-800">
              <td className="border border-gray-700 px-1 py-1.5 text-center text-xs text-gray-400">
                PREVIOUS
              </td>
              {teams.map((team) => (
                <td
                  key={`prev-${team.teamId}`}
                  className="border border-gray-700 px-1 py-1.5 text-center font-mono text-gray-400 text-xs italic"
                >
                  {formatMoney(team.previous)}
                </td>
              ))}
            </tr>

            <tr className="bg-gray-700 font-bold">
              <td className="border border-gray-600 px-1 py-1.5 text-center text-xs">SUBTOTAL</td>
              {teams.map((team) => (
                <td
                  key={`sub-${team.teamId}`}
                  className={`border border-gray-600 px-1 py-1.5 text-center font-mono ${signText(team.subtotal)}`}
                >
                  <div>{formatMoney(team.subtotal)}</div>
                  <div className="text-gray-500 text-xs font-normal">
                    {formatMoney(team.topTenMoney)} ({team.topTenCount})
                  </div>
                </td>
              ))}
            </tr>

            <tr className="bg-gray-800">
              <td className="border border-gray-700 px-1 py-1.5 text-center text-xs text-gray-400">
                ROWS 5-6-7-8
              </td>
              {teams.map((team) => (
                <td
                  key={`sb-${team.teamId}`}
                  className={`border border-gray-700 px-1 py-1.5 text-center font-mono ${signText(team.sideBets)}`}
                >
                  {formatMoney(team.sideBets)}
                </td>
              ))}
            </tr>

            <tr>
              <td colSpan={teams.length + 1} className="h-1 bg-gray-900" />
            </tr>

            <tr className="bg-yellow-700/30 font-bold text-sm">
              <td className="border border-yellow-600/50 px-1 py-2 text-center text-xs">
                TOTAL
                <br />
                CASH
              </td>
              {teams.map((team) => (
                <td
                  key={`total-${team.teamId}`}
                  className={`border border-yellow-600/50 px-1 py-2 text-center font-mono ${signText(team.totalCash)}`}
                >
                  {formatMoney(team.totalCash)}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>

      {undraftedTopTens.length > 0 ? (
        <div className="text-xs text-gray-500">
          <span className="font-semibold">UNDRAFTED TOP TENS:</span>
          {undraftedTopTens.map((u) => (
            <span key={u.name} className="ml-2">
              {u.name} {formatMoney(u.payout)}
            </span>
          ))}
        </div>
      ) : null}

      {teams.length > 0 ? (
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
            <span className={`font-semibold tabular-nums ${signText(summary.net)}`}>
              {formatMoney(summary.net)}
            </span>
          </div>
        </div>
      ) : null}
    </section>
  );
}

export default WeeklyReportView;
