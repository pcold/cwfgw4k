import type { ReactNode } from 'react';
import type { ReportCell, ReportTeamColumn, WeeklyReport } from '@/shared/api/types';
import {
  formatDollarsAndCents,
  formatUnsignedDollarsAndCents,
  formatWholeDollars,
  moneyToneClass,
} from '@/shared/util/money';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import LateRowTag from '@/shared/components/LateRowTag';
import ListCard, { CARD_LIST_CLASS, CARD_NAME_BUTTON_CLASS } from '@/shared/components/ListCard';
import MoneyCell from '@/shared/components/MoneyCell';
import ReportGridCell from '@/shared/components/ReportGridCell';
import ReportGridHead from '@/shared/components/ReportGridHead';
import ReportGridRoundLabel from '@/shared/components/ReportGridRoundLabel';
import { REPORT_GRID_TABLE_CLASS } from '@/shared/components/reportGrid';
import TeamStrip from '@/shared/components/TeamStrip';
import {
  ROUNDS,
  cellEarningsLabel,
  isSeasonReport,
  sideBetAmountByRound,
  sideBetRowLabel,
  sideBetWinnersByRound,
  teamCellsByRound,
  topTensLabel,
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
  const earningsLabel = cellEarningsLabel(cell);
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
  readonly teams: readonly ReportTeamColumn[];
  readonly label: ReactNode;
  readonly labelClass: string;
  readonly figureClass?: string;
  readonly cellContent: (team: ReportTeamColumn) => ReactNode;
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

interface SummaryLabelProps {
  readonly className: string;
  readonly children: ReactNode;
}

function SummaryLabel({ className, children }: SummaryLabelProps) {
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

const FIGURE_LABEL_CLASS = 'text-xs font-semibold uppercase tracking-wider text-ink-label';

interface SeasonFigureProps {
  readonly label: string;
  readonly value: number;
}

function SeasonFigure({ label, value }: SeasonFigureProps): ReactNode {
  return (
    <div className="px-3 py-2">
      <dt className={FIGURE_LABEL_CLASS}>{label}</dt>
      <dd className="font-display font-bold text-lg tabular-nums text-ink">{formatDollarsAndCents(value)}</dd>
    </div>
  );
}

interface SeasonCardProps {
  readonly team: ReportTeamColumn;
  readonly seasonMode: boolean;
}

function SeasonCard({ team, seasonMode }: SeasonCardProps): ReactNode {
  return (
    <div aria-label="Team totals" role="group" className="rounded bg-surface-card">
      <div className="flex min-h-13.5">
        <div className="flex-1 min-w-0 px-3 py-2 flex flex-col justify-center">
          <div className={FIGURE_LABEL_CLASS}>Total cash</div>
          <div className="font-display font-bold text-lg text-ink uppercase truncate">{team.teamName}</div>
        </div>
        <MoneyCell display={formatWholeDollars(team.totalCash)} shape="card-end" />
      </div>
      <dl className="grid grid-cols-3 divide-x divide-hairline-soft border-t border-hairline-soft">
        <SeasonFigure label="Previous" value={team.previous} />
        <SeasonFigure label={seasonMode ? 'Season' : 'Week'} value={team.weeklyTotal} />
        <SeasonFigure label="Late row" value={team.sideBets} />
      </dl>
      <p className="border-t border-hairline-soft px-3 py-2 text-sm text-ink-muted">
        {topTensLabel(team.topTenCount)} this season · {formatDollarsAndCents(team.topTenMoney)}
      </p>
    </div>
  );
}

interface PickCardProps {
  readonly round: number;
  readonly cell: ReportCell | undefined;
  readonly lateRow: boolean;
  readonly leadsLateRow: boolean;
  readonly showSeasonLine: boolean;
  readonly onGolferClick: ((golferId: string) => void) | undefined;
}

function PickCard({ round, cell, lateRow, leadsLateRow, showSeasonLine, onGolferClick }: PickCardProps): ReactNode {
  const tag = leadsLateRow ? 'late-row-leader' : 'late-row';
  const detail = lateRow ? <LateRowTag variant={tag} /> : null;
  if (!cell || !cell.golferName) {
    return (
      <ListCard
        position={round}
        positionKind="round"
        name={<span className="text-ink-faint">—</span>}
        nameCase="golfer"
        detail={detail}
        total={null}
        fill={leadsLateRow ? 'late-row-leader' : 'plain'}
      />
    );
  }

  const label = `${cell.golferName}${cell.ownershipPct < 100 ? ` (${cell.ownershipPct}%)` : ''}`;
  const golferId = cell.golferId;
  const name =
    onGolferClick && golferId ? (
      <button type="button" onClick={() => onGolferClick(golferId)} className={CARD_NAME_BUTTON_CLASS}>
        {label}
      </button>
    ) : (
      label
    );
  return (
    <ListCard
      position={round}
      positionKind="round"
      name={cell.earnings > 0 ? name : <span className="text-ink-muted">{name}</span>}
      nameCase="golfer"
      detail={detail}
      total={
        <div className="shrink-0 px-3 py-2 flex flex-col justify-center items-end text-right tabular-nums">
          <div className="font-display font-bold text-ink">{cellEarningsLabel(cell)}</div>
          {showSeasonLine ? (
            <div className="text-sm text-ink-muted">
              {formatDollarsAndCents(cell.seasonEarnings)} · {topTensLabel(cell.seasonTopTens)}
            </div>
          ) : null}
        </div>
      }
      fill={leadsLateRow ? 'late-row-leader' : 'plain'}
    />
  );
}

interface PhoneReportProps {
  readonly teams: readonly ReportTeamColumn[];
  readonly team: ReportTeamColumn;
  readonly seasonMode: boolean;
  readonly winnersByRound: ReadonlyMap<number, ReadonlySet<string>>;
  readonly sideBetAmounts: ReadonlyMap<number, number>;
  readonly onSelectTeam: (teamId: string) => void;
  readonly onGolferClick: ((golferId: string) => void) | undefined;
}

function PhoneReport({
  teams,
  team,
  seasonMode,
  winnersByRound,
  sideBetAmounts,
  onSelectTeam,
  onGolferClick,
}: PhoneReportProps): ReactNode {
  const cells = teamCellsByRound(team);
  return (
    <div className="sm:hidden space-y-3">
      <TeamStrip teams={teams} selectedTeamId={team.teamId} onSelect={onSelectTeam} />
      <SeasonCard team={team} seasonMode={seasonMode} />
      <ol aria-label="Picks" className={CARD_LIST_CLASS}>
        {ROUNDS.map((round) => (
          <PickCard
            key={round}
            round={round}
            cell={cells.get(round)}
            lateRow={sideBetAmounts.has(round)}
            leadsLateRow={winnersByRound.get(round)?.has(team.teamId) ?? false}
            showSeasonLine={!seasonMode}
            onGolferClick={onGolferClick}
          />
        ))}
      </ol>
    </div>
  );
}

interface Props {
  readonly report: WeeklyReport;
  /** Null, or an id the report no longer has, shows the first team. */
  readonly selectedTeamId: string | null;
  readonly onSelectTeam: (teamId: string) => void;
  readonly onGolferClick?: (golferId: string) => void;
}

function WeeklyReportView({ report, selectedTeamId, onSelectTeam, onGolferClick }: Props): ReactNode {
  const { teams, undraftedTopTens, sideBetDetail, cashTotals } = report;
  const selectedTeam = teams.find((team) => team.teamId === selectedTeamId) ?? teams[0];
  const seasonMode = isSeasonReport(report);
  const showSeasonFooter = !seasonMode;
  const winnersByRound = sideBetWinnersByRound(sideBetDetail);
  const sideBetAmounts = sideBetAmountByRound(report.sideBetRules);
  const teamsByRound = teams.map((t) => ({ team: t, cells: teamCellsByRound(t) }));
  const gridTeams = teams.map((team, i) => ({ teamId: team.teamId, number: i + 1, name: team.teamName }));

  return (
    <section className="space-y-4">
      {selectedTeam === undefined ? null : (
        <PhoneReport
          teams={teams}
          team={selectedTeam}
          seasonMode={seasonMode}
          winnersByRound={winnersByRound}
          sideBetAmounts={sideBetAmounts}
          onSelectTeam={onSelectTeam}
          onGolferClick={onGolferClick}
        />
      )}

      <div className="hidden sm:block overflow-x-auto -mx-3 sm:mx-0">
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
