import type { ReactNode } from 'react';
import type { ReportCell, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
import {
  formatDollarsAndCents,
  formatUnsignedDollarsAndCents,
  formatWholeDollars,
  moneyToneClass,
} from '@/shared/util/money';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import MoneyCell from '@/shared/components/MoneyCell';
import ReportGridCell from '@/shared/components/ReportGridCell';
import ReportGridHead from '@/shared/components/ReportGridHead';
import ReportGridRoundLabel from '@/shared/components/ReportGridRoundLabel';
import { REPORT_GRID_TABLE_CLASS } from '@/shared/components/reportGrid';
import {
  ROUNDS,
  isSeasonReport,
  sideBetAmountByRound,
  sideBetRowLabel,
  sideBetWinnersByRound,
  teamCellsByRound,
} from './weeklyReportModel';

const GOLFER_BUTTON_CLASS = 'truncate bg-transparent border-0 p-0 hover:underline cursor-pointer';

interface RoundCellProps {
  cell: ReportCell | undefined;
  isSideBetWinner: boolean;
  showSeasonFooter: boolean;
  onGolferClick?: (golferId: string) => void;
}

function RoundCell({ cell, isSideBetWinner, showSeasonFooter, onGolferClick }: RoundCellProps) {
  if (!cell || !cell.golferName) {
    return (
      <ReportGridCell>
        <span className="text-ink-faint">—</span>
      </ReportGridCell>
    );
  }

  const hasEarnings = cell.earnings > 0;
  const nameTone = hasEarnings ? 'font-bold text-ink' : 'text-ink-muted';
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
        className={`${GOLFER_BUTTON_CLASS} ${nameTone} ${TAP_TARGET_CLASS}`}
      >
        {cell.golferName}
        {ownership}
      </button>
    ) : (
      <div className={`truncate ${nameTone}`}>
        {cell.golferName}
        {ownership}
      </div>
    );

  return (
    <ReportGridCell fill={isSideBetWinner ? 'late-row-leader' : 'plain'}>
      {nameContent}
      <div className="font-mono text-ink-muted">{earningsLabel}</div>
      {showSeasonFooter ? <div className="font-mono text-ink-muted">{seasonEarningsLabel}</div> : null}
    </ReportGridCell>
  );
}

interface SummaryRowProps {
  teams: ReportTeamColumn[];
  label: ReactNode;
  labelClass: string;
  figureClass?: string;
  cellContent: (team: ReportTeamColumn) => ReactNode;
}

function SummaryRow({ teams, label, labelClass, figureClass = '', cellContent }: SummaryRowProps) {
  return (
    <tr>
      <SummaryLabel className={labelClass}>{label}</SummaryLabel>
      {teams.map((team) => (
        <ReportGridCell key={team.teamId}>
          <div className={`text-right font-mono ${figureClass}`}>{cellContent(team)}</div>
        </ReportGridCell>
      ))}
    </tr>
  );
}

function SummaryLabel({ className, children }: { className: string; children: ReactNode }) {
  return (
    <td className={`px-1 py-1.5 text-right align-middle sticky left-0 z-10 bg-canvas ${className}`}>{children}</td>
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
  const gridTeams = teams.map((team, i) => ({ teamId: team.teamId, number: i + 1, name: team.teamName }));

  return (
    <section className="space-y-4">
      <div className="overflow-x-auto -mx-3 sm:mx-0">
        <table className={REPORT_GRID_TABLE_CLASS}>
          <ReportGridHead cornerLabel="TEAM #" teams={gridTeams} />
          <tbody>
            {ROUNDS.map((round) => {
              const winners = winnersByRound.get(round) ?? new Set<string>();
              const sideBetAmount = sideBetAmounts.get(round);
              return (
                <tr key={round}>
                  <ReportGridRoundLabel round={round} lateRow={sideBetAmount !== undefined}>
                    {sideBetAmount === undefined ? null : (
                      <div className="text-ink-muted">({formatUnsignedDollarsAndCents(sideBetAmount)})</div>
                    )}
                  </ReportGridRoundLabel>
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

            <tr aria-hidden="true">
              <td colSpan={teams.length + 1} className="h-2" />
            </tr>

            <SummaryRow
              teams={teams}
              label="TOP TENS"
              labelClass="font-bold text-ink"
              figureClass="font-bold text-ink"
              cellContent={(t) => formatDollarsAndCents(t.topTenEarnings)}
            />

            <SummaryRow
              teams={teams}
              label={seasonMode ? '**SEASON' : '**WEEKLY'}
              labelClass="font-bold text-ink"
              figureClass="font-bold text-ink"
              cellContent={(t) => formatDollarsAndCents(t.weeklyTotal)}
            />

            <SummaryRow
              teams={teams}
              label="PREVIOUS"
              labelClass="text-ink-muted"
              figureClass="text-ink-muted italic"
              cellContent={(t) => formatDollarsAndCents(t.previous)}
            />

            <SummaryRow
              teams={teams}
              label="SUBTOTAL"
              labelClass="font-bold text-ink"
              figureClass="text-ink"
              cellContent={(t) => (
                <>
                  <div className="font-bold">{formatDollarsAndCents(t.subtotal)}</div>
                  <div className="text-ink-muted">
                    {formatDollarsAndCents(t.topTenMoney)} ({t.topTenCount})
                  </div>
                </>
              )}
            />

            <SummaryRow
              teams={teams}
              label={sideBetRowLabel(report.sideBetRules)}
              labelClass="text-ink-muted"
              figureClass="text-ink"
              cellContent={(t) => formatDollarsAndCents(t.sideBets)}
            />

            <tr>
              <SummaryLabel className="font-bold text-sm text-ink">
                TOTAL
                <br />
                CASH
              </SummaryLabel>
              {teams.map((team) => (
                <MoneyCell key={team.teamId} display={formatWholeDollars(team.totalCash)} shape="standalone" />
              ))}
            </tr>
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
