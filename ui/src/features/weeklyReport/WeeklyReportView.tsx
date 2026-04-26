import type { ReactNode } from 'react';
import type { ReportRow, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
import { formatMoney } from '@/shared/util/money';
import {
  ROUNDS,
  isSeasonReport,
  roundCellBg,
  roundTextColor,
  sideBetWinnersByRound,
  signTextClass,
  summarizeWeeklyReport,
  teamRowsByRound,
} from './weeklyReportModel';

interface RoundCellProps {
  row: ReportRow | undefined;
  isSideBetWinner: boolean;
  showSeasonFooter: boolean;
  onGolferClick?: (golferId: string) => void;
}

function RoundCell({ row, isSideBetWinner, showSeasonFooter, onGolferClick }: RoundCellProps) {
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

  const nameContent =
    onGolferClick && row.golferId ? (
      <button
        type="button"
        onClick={() => onGolferClick(row.golferId!)}
        className={`font-semibold truncate ${text} bg-transparent border-0 p-0 hover:underline cursor-pointer`}
      >
        {row.golferName}
        {ownership}
      </button>
    ) : (
      <div className={`font-semibold truncate ${text}`}>
        {row.golferName}
        {ownership}
      </div>
    );

  return (
    <td className={`border border-gray-700 px-1.5 py-1 text-center align-top ${bg}`}>
      {nameContent}
      <div className={`font-mono ${earningsColor}`}>{earningsLabel}</div>
      {showSeasonFooter ? (
        <div className={`font-semibold ${text}`}>{seasonEarningsLabel}</div>
      ) : null}
    </td>
  );
}

interface SummaryRowProps {
  teams: ReportTeamColumn[];
  label: ReactNode;
  rowClass: string;
  borderClass: string;
  labelBgClass: string;
  labelClass?: string;
  cellClass?: (team: ReportTeamColumn) => string;
  cellContent: (team: ReportTeamColumn) => ReactNode;
  py?: string;
}

function SummaryRow({
  teams,
  label,
  rowClass,
  borderClass,
  labelBgClass,
  labelClass = '',
  cellClass,
  cellContent,
  py = 'py-1.5',
}: SummaryRowProps) {
  return (
    <tr className={rowClass}>
      <td
        className={`${borderClass} px-1 ${py} text-center text-xs sticky left-0 z-10 ${labelBgClass} ${labelClass}`}
      >
        {label}
      </td>
      {teams.map((team) => (
        <td
          key={team.teamId}
          className={`${borderClass} px-1 ${py} text-center font-mono ${cellClass?.(team) ?? ''}`}
        >
          {cellContent(team)}
        </td>
      ))}
    </tr>
  );
}

function StatCard({ label, value, color }: { label: string; value: number; color: string }) {
  return (
    <div className="bg-gray-800 rounded px-3 py-2 flex items-center gap-2">
      <span className="text-gray-500">{label}</span>
      <span className={`font-semibold tabular-nums ${color}`}>{formatMoney(value)}</span>
    </div>
  );
}

interface Props {
  report: WeeklyReport;
  onGolferClick?: (golferId: string) => void;
}

function WeeklyReportView({ report, onGolferClick }: Props) {
  const { tournament, teams, undraftedTopTens, sideBetDetail } = report;
  const seasonMode = isSeasonReport(report);
  const showSeasonFooter = !seasonMode;
  const winnersByRound = sideBetWinnersByRound(sideBetDetail);
  const summary = summarizeWeeklyReport(report);
  const teamsByRound = teams.map((t) => ({ team: t, rows: teamRowsByRound(t) }));

  const highlightRow = 'bg-gray-700 font-bold';
  const subRow = 'bg-gray-800';
  const highlightBorder = 'border border-gray-600';
  const subBorder = 'border border-gray-700';

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

      <div className="overflow-x-auto -mx-3 sm:mx-0">
        <table className="w-full text-xs border-collapse">
          <thead>
            <tr className="bg-yellow-600 text-black font-bold">
              <th className="border border-gray-600 px-1 py-1.5 w-12 sm:w-16 text-center sticky left-0 z-20 bg-yellow-600">
                TEAM #
              </th>
              {teams.map((team, i) => (
                <th
                  key={team.teamId}
                  className="border border-gray-600 px-1 py-1.5 text-center min-w-[90px]"
                >
                  <div>{i + 1}</div>
                  <div className="text-xs uppercase">{team.teamName}</div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ROUNDS.map((round) => {
              const winners = winnersByRound.get(round) ?? new Set<string>();
              return (
                <tr key={round} className="bg-gray-800 hover:bg-gray-800/80">
                  <td className="border border-gray-700 px-1 py-1 text-center font-bold text-gray-400 sticky left-0 z-10 bg-gray-800">
                    {round}
                  </td>
                  {teamsByRound.map(({ team, rows }) => (
                    <RoundCell
                      key={`${team.teamId}-${round}`}
                      row={rows.get(round)}
                      isSideBetWinner={winners.has(team.teamId)}
                      showSeasonFooter={showSeasonFooter}
                      onGolferClick={onGolferClick}
                    />
                  ))}
                </tr>
              );
            })}

            <tr>
              <td colSpan={teams.length + 1} className="h-1 bg-gray-900" />
            </tr>

            <SummaryRow
              teams={teams}
              label="TOP TENS"
              rowClass={highlightRow}
              borderClass={highlightBorder}
              labelBgClass="bg-gray-700"
              cellClass={(t) => (t.topTenEarnings > 0 ? 'text-green-400' : 'text-gray-500')}
              cellContent={(t) => formatMoney(t.topTenEarnings)}
            />

            <SummaryRow
              teams={teams}
              label={seasonMode ? '**SEASON' : '**WEEKLY'}
              rowClass={highlightRow}
              borderClass={highlightBorder}
              labelBgClass="bg-gray-700"
              cellClass={(t) => signTextClass(t.weeklyTotal)}
              cellContent={(t) => formatMoney(t.weeklyTotal)}
            />

            <SummaryRow
              teams={teams}
              label="PREVIOUS"
              rowClass={subRow}
              borderClass={subBorder}
              labelBgClass="bg-gray-800"
              labelClass="text-gray-400"
              cellClass={() => 'text-gray-400 text-xs italic'}
              cellContent={(t) => formatMoney(t.previous)}
            />

            <SummaryRow
              teams={teams}
              label="SUBTOTAL"
              rowClass={highlightRow}
              borderClass={highlightBorder}
              labelBgClass="bg-gray-700"
              cellClass={(t) => signTextClass(t.subtotal)}
              cellContent={(t) => (
                <>
                  <div>{formatMoney(t.subtotal)}</div>
                  <div className="text-gray-500 text-xs font-normal">
                    {formatMoney(t.topTenMoney)} ({t.topTenCount})
                  </div>
                </>
              )}
            />

            <SummaryRow
              teams={teams}
              label="ROWS 5-6-7-8"
              rowClass={subRow}
              borderClass={subBorder}
              labelBgClass="bg-gray-800"
              labelClass="text-gray-400"
              cellClass={(t) => signTextClass(t.sideBets)}
              cellContent={(t) => formatMoney(t.sideBets)}
            />

            <tr>
              <td colSpan={teams.length + 1} className="h-1 bg-gray-900" />
            </tr>

            <SummaryRow
              teams={teams}
              label={
                <>
                  TOTAL
                  <br />
                  CASH
                </>
              }
              rowClass="bg-yellow-700/30 font-bold text-sm"
              borderClass="border border-yellow-600/50"
              labelBgClass="bg-yellow-900"
              py="py-2"
              cellClass={(t) => signTextClass(t.totalCash)}
              cellContent={(t) => formatMoney(t.totalCash)}
            />
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
          <StatCard label="Won" value={summary.totalWon} color="text-green-400" />
          <StatCard label="Lost" value={summary.totalLost} color="text-red-400" />
          <StatCard label="Net" value={summary.net} color={signTextClass(summary.net)} />
        </div>
      ) : null}
    </section>
  );
}

export default WeeklyReportView;
