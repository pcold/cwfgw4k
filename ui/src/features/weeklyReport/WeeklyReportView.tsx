import type { ReactNode } from 'react';
import type { ReportCell, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
import {
  formatDollarsAndCents,
  formatUnsignedDollarsAndCents,
  formatWholeDollars,
  moneyToneClass,
} from '@/shared/util/money';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import {
  ROUNDS,
  isSeasonReport,
  roundCellBg,
  roundTextColor,
  sideBetAmountByRound,
  sideBetRowLabel,
  sideBetWinnersByRound,
  teamCellsByRound,
} from './weeklyReportModel';

interface RoundCellProps {
  cell: ReportCell | undefined;
  isSideBetWinner: boolean;
  showSeasonFooter: boolean;
  onGolferClick?: (golferId: string) => void;
}

function RoundCell({ cell, isSideBetWinner, showSeasonFooter, onGolferClick }: RoundCellProps) {
  if (!cell || !cell.golferName) {
    return (
      <td className="border border-hairline px-1.5 py-1 text-center align-top">
        <span className="text-ink-faint">—</span>
      </td>
    );
  }

  const hasEarnings = cell.earnings > 0;
  const bg = roundCellBg(hasEarnings, isSideBetWinner);
  const text = roundTextColor(hasEarnings, isSideBetWinner);
  const earningsColor = hasEarnings
    ? isSideBetWinner
      ? 'text-semantic-success'
      : 'text-on-gold-earnings'
    : 'text-ink-soft';
  const ownership = cell.ownershipPct < 100 ? ` (${cell.ownershipPct}%)` : '';
  const earningsLabel = hasEarnings
    ? `${cell.positionStr ?? ''} - ${formatDollarsAndCents(cell.earnings)}`
    : '-';
  const seasonEarningsLabel =
    cell.seasonTopTens > 0
      ? `${formatDollarsAndCents(cell.seasonEarnings)} (${cell.seasonTopTens})`
      : formatDollarsAndCents(0);

  const golferId = cell.golferId;
  const nameContent =
    onGolferClick && golferId ? (
      <button
        type="button"
        onClick={() => onGolferClick(golferId)}
        className={`font-semibold truncate ${text} bg-transparent border-0 p-0 hover:underline cursor-pointer ${TAP_TARGET_CLASS}`}
      >
        {cell.golferName}
        {ownership}
      </button>
    ) : (
      <div className={`font-semibold truncate ${text}`}>
        {cell.golferName}
        {ownership}
      </div>
    );

  return (
    <td className={`border border-hairline px-1.5 py-1 text-center align-top ${bg}`}>
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

function StatCard({ label, value }: { label: string; value: number }) {
  const amount = formatWholeDollars(value);
  return (
    <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
      <span className="text-ink-soft">{label}</span>
      <span className={`font-semibold tabular-nums ${moneyToneClass(amount)}`}>{amount}</span>
    </div>
  );
}

interface Props {
  report: WeeklyReport;
  onGolferClick?: (golferId: string) => void;
}

function WeeklyReportView({ report, onGolferClick }: Props): ReactNode {
  const { teams, undraftedTopTens, sideBetDetail, cashTotals } = report;
  const seasonMode = isSeasonReport(report);
  const showSeasonFooter = !seasonMode;
  const winnersByRound = sideBetWinnersByRound(sideBetDetail);
  const sideBetAmounts = sideBetAmountByRound(report.sideBetRules);
  const teamsByRound = teams.map((t) => ({ team: t, cells: teamCellsByRound(t) }));

  const highlightRow = 'bg-surface-raised font-bold';
  const subRow = 'bg-surface-card';
  const highlightBorder = 'border border-hairline-strong';
  const subBorder = 'border border-hairline';

  return (
    <section className="space-y-4">
      <div className="overflow-x-auto -mx-3 sm:mx-0">
        <table className="w-full text-xs border-collapse">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr className="bg-gold text-on-gold">
              <th className="border border-hairline-strong px-1 py-1.5 w-12 sm:w-16 text-center sticky left-0 z-20 bg-gold">
                TEAM #
              </th>
              {teams.map((team, i) => (
                <th
                  key={team.teamId}
                  className="border border-hairline-strong px-1 py-1.5 text-center min-w-[90px]"
                >
                  <div>{i + 1}</div>
                  <div>{team.teamName}</div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ROUNDS.map((round) => {
              const winners = winnersByRound.get(round) ?? new Set<string>();
              const sideBetAmount = sideBetAmounts.get(round);
              return (
                <tr key={round} className="bg-surface-card hover:bg-surface-card/80">
                  <td className="border border-hairline px-1 py-1 text-center font-bold text-ink-muted sticky left-0 z-10 bg-surface-card">
                    <div>{round}</div>
                    {sideBetAmount === undefined ? null : (
                      <div className="text-xs font-normal">({formatUnsignedDollarsAndCents(sideBetAmount)})</div>
                    )}
                  </td>
                  {teamsByRound.map(({ team, cells }) => (
                    <RoundCell
                      key={`${team.teamId}-${round}`}
                      cell={cells.get(round)}
                      isSideBetWinner={winners.has(team.teamId)}
                      showSeasonFooter={showSeasonFooter}
                      onGolferClick={onGolferClick}
                    />
                  ))}
                </tr>
              );
            })}

            <tr>
              <td colSpan={teams.length + 1} className="h-1 bg-canvas" />
            </tr>

            <SummaryRow
              teams={teams}
              label="TOP TENS"
              rowClass={highlightRow}
              borderClass={highlightBorder}
              labelBgClass="bg-surface-raised"
              cellContent={(t) => formatDollarsAndCents(t.topTenEarnings)}
            />

            <SummaryRow
              teams={teams}
              label={seasonMode ? '**SEASON' : '**WEEKLY'}
              rowClass={highlightRow}
              borderClass={highlightBorder}
              labelBgClass="bg-surface-raised"
              cellContent={(t) => formatDollarsAndCents(t.weeklyTotal)}
            />

            <SummaryRow
              teams={teams}
              label="PREVIOUS"
              rowClass={subRow}
              borderClass={subBorder}
              labelBgClass="bg-surface-card"
              labelClass="text-ink-muted"
              cellClass={() => 'text-ink-muted text-xs italic'}
              cellContent={(t) => formatDollarsAndCents(t.previous)}
            />

            <SummaryRow
              teams={teams}
              label="SUBTOTAL"
              rowClass={highlightRow}
              borderClass={highlightBorder}
              labelBgClass="bg-surface-raised"
              cellContent={(t) => (
                <>
                  <div>{formatDollarsAndCents(t.subtotal)}</div>
                  <div className="text-ink-soft text-xs font-normal">
                    {formatDollarsAndCents(t.topTenMoney)} ({t.topTenCount})
                  </div>
                </>
              )}
            />

            <SummaryRow
              teams={teams}
              label={sideBetRowLabel(report.sideBetRules)}
              rowClass={subRow}
              borderClass={subBorder}
              labelBgClass="bg-surface-card"
              labelClass="text-ink-muted"
              cellContent={(t) => formatDollarsAndCents(t.sideBets)}
            />

            <tr>
              <td colSpan={teams.length + 1} className="h-1 bg-canvas" />
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
              rowClass="bg-gold-soft/30 font-bold text-sm"
              borderClass="border border-gold/50"
              labelBgClass="bg-gold-tint"
              py="py-2"
              cellContent={(t) => {
                const totalCash = formatWholeDollars(t.totalCash);
                return <span className={moneyToneClass(totalCash)}>{totalCash}</span>;
              }}
            />
          </tbody>
        </table>
      </div>

      {undraftedTopTens.length > 0 ? (
        <div className="text-xs text-ink-soft">
          <span className="font-semibold">UNDRAFTED TOP TENS:</span>
          {undraftedTopTens.map((u) => (
            <span key={u.name} className="ml-2">
              {u.name} {formatDollarsAndCents(u.payout)}
            </span>
          ))}
        </div>
      ) : null}

      {teams.length > 0 ? (
        <div className="flex flex-wrap gap-3 text-sm">
          <StatCard label="Won" value={cashTotals.won} />
          <StatCard label="Lost" value={cashTotals.lost} />
          <StatCard label="Net" value={cashTotals.net} />
        </div>
      ) : null}
    </section>
  );
}

export default WeeklyReportView;
