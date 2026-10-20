import { Fragment, useMemo, useState, type ReactNode } from 'react';
import type { LiveLeaderboardEntry, WeeklyReport, WonLostTotals } from '@/shared/api/types';
import { formatDollarsAndCents, moneyToneClass } from '@/shared/util/money';
import Chip from '@/shared/components/Chip';
import ListCard, { CARD_LIST_CLASS } from '@/shared/components/ListCard';
import MoneyCell from '@/shared/components/MoneyCell';
import PositionCell from '@/shared/components/PositionCell';
import ColumnHeaderFilter, {
  type ColumnHeaderFilterOption,
} from '@/shared/components/ColumnHeaderFilter';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import {
  formatScoreToPar,
  hasPlayerOn,
  hasUndraftedPlayer,
  leaderboardTeamChips,
  leaderboardTeamNames,
  playerNames,
  scoreboardTeams,
  type ScoreboardTeam,
} from './scoreboardModel';

const ALL = '';
const UNDRAFTED = '__undrafted__';

const GOLFER_BUTTON_CLASS = `text-ink-secondary hover:underline cursor-pointer bg-transparent border-0 p-0 ${TAP_TARGET_CLASS}`;

// Spelled as on the standings' card rows, which keep theirs private.
const CARD_CELL_CLASS = 'px-3 py-2 bg-surface-card';

// `grid-cols-1`, not the implicit track, which grows to a table's min-width and scrolls a phone's page sideways.
const TWO_COLUMN_CLASS = 'grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,5fr)_minmax(0,4fr)] xl:items-start';

interface GolferLineProps {
  team: ScoreboardTeam;
  onGolferClick?: (golferId: string) => void;
}

// Each golfer's name, finish and payout keep to one line, so a wrap falls only between golfers.
function GolferLine({ team, onGolferClick }: GolferLineProps): ReactNode {
  if (team.golferScores.length === 0) return null;
  return (
    <div className="text-sm text-ink-muted">
      {team.golferScores.map((golfer, index) => {
        const { golferId } = golfer;
        return (
          <Fragment key={golferId ?? golfer.golferName}>
            {index > 0 ? ' · ' : null}
            <span className="whitespace-nowrap">
              {onGolferClick && golferId ? (
                <button type="button" onClick={() => onGolferClick(golferId)} className={GOLFER_BUTTON_CLASS}>
                  {golfer.golferName}
                </button>
              ) : (
                <span className="text-ink-secondary">{golfer.golferName}</span>
              )}{' '}
              {golfer.tied ? 'T' : null}
              {golfer.position ?? '?'} <span className="tabular-nums">{formatDollarsAndCents(golfer.payout)}</span>
              {golfer.ownershipPct < 100 ? ` (${golfer.ownershipPct}%)` : null}
            </span>
          </Fragment>
        );
      })}
    </div>
  );
}

interface TeamListProps {
  teams: readonly ScoreboardTeam[];
  onGolferClick?: (golferId: string) => void;
}

function TeamCards({ teams, onGolferClick }: TeamListProps): ReactNode {
  return (
    <ol aria-label="Weekly results" className={`sm:hidden ${CARD_LIST_CLASS}`}>
      {teams.map((team, index) => (
        <ListCard
          key={team.teamId}
          position={index + 1}
          name={team.teamName}
          nameCase="team"
          detail={
            team.golferScores.length === 0 ? (
              'No golfers in the top 10'
            ) : (
              <GolferLine team={team} onGolferClick={onGolferClick} />
            )
          }
          total={<MoneyCell display={formatDollarsAndCents(team.weeklyTotal)} shape="card-end" />}
        />
      ))}
    </ol>
  );
}

function TeamTable({ teams, onGolferClick }: TeamListProps): ReactNode {
  return (
    <div className="hidden sm:block overflow-x-auto -mx-3 sm:mx-0">
      <table className="w-full min-w-[500px] text-sm border-separate border-spacing-y-1">
        <thead className={COLUMN_HEADER_CLASS}>
          <tr className="text-left">
            <th className="px-3 py-2 w-14 text-center">#</th>
            <th className="px-3 py-2">Team</th>
            <th className="px-3 py-2 text-right">Top 10 Earnings</th>
            <th className="px-3 py-2 w-36 text-right">Weekly +/-</th>
          </tr>
        </thead>
        <tbody>
          {teams.map((team, index) => (
            <tr key={team.teamId} className="h-11">
              <PositionCell position={index + 1} />
              <td className={CARD_CELL_CLASS}>
                <div className="font-display font-bold text-lg uppercase text-ink">{team.teamName}</div>
                <GolferLine team={team} onGolferClick={onGolferClick} />
              </td>
              <td className={`${CARD_CELL_CLASS} text-right tabular-nums text-ink-secondary`}>
                {formatDollarsAndCents(team.topTenEarnings)}
              </td>
              <MoneyCell display={formatDollarsAndCents(team.weeklyTotal)} />
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function MoneySummary({ totals }: { totals: WonLostTotals }): ReactNode {
  const won = formatDollarsAndCents(totals.won);
  const lost = formatDollarsAndCents(totals.lost);
  const net = formatDollarsAndCents(totals.net);
  return (
    <div className="flex flex-wrap gap-3 text-sm">
      <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
        <span className="text-ink-soft">Won</span>
        <span className={`font-semibold tabular-nums ${moneyToneClass(won)}`}>
          {won}
        </span>
      </div>
      <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
        <span className="text-ink-soft">Lost</span>
        <span className={`font-semibold tabular-nums ${moneyToneClass(lost)}`}>
          {lost}
        </span>
      </div>
      <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
        <span className="text-ink-soft">Net</span>
        <span className={`font-semibold tabular-nums ${moneyToneClass(net)}`}>
          {net}
        </span>
      </div>
    </div>
  );
}

interface TeamChipsProps {
  entry: LiveLeaderboardEntry;
}

function TeamChips({ entry }: TeamChipsProps): ReactNode {
  return (
    <div className="flex flex-wrap gap-1">
      {leaderboardTeamChips(entry).map((chip) => {
        switch (chip.kind) {
          case 'team':
            return <Chip key={chip.key} variant="neutral">{chip.teamName}</Chip>;
          case 'undrafted':
            return <Chip key={chip.key} variant="outlined">Undrafted</Chip>;
          default:
            return chip satisfies never;
        }
      })}
    </div>
  );
}

interface LeaderboardCardProps {
  leaderboard: readonly LiveLeaderboardEntry[];
  tournamentName: string | null;
}

function LeaderboardCard({ leaderboard, tournamentName }: LeaderboardCardProps): ReactNode {
  const [teamFilter, setTeamFilter] = useState<string>(ALL);

  const teamOptions = useMemo<ColumnHeaderFilterOption[]>(
    () => [
      { value: ALL, label: 'Team' },
      ...leaderboardTeamNames(leaderboard).map((name) => ({ value: name, label: name })),
      ...(leaderboard.some(hasUndraftedPlayer) ? [{ value: UNDRAFTED, label: 'Undrafted' }] : []),
    ],
    [leaderboard],
  );

  const filteredLeaderboard = useMemo(() => {
    if (teamFilter === ALL) return leaderboard;
    if (teamFilter === UNDRAFTED) return leaderboard.filter(hasUndraftedPlayer);
    return leaderboard.filter((entry) => hasPlayerOn(entry, teamFilter));
  }, [leaderboard, teamFilter]);

  return (
    <div className="bg-surface-card rounded-lg">
      <div className="flex items-center justify-between gap-3 px-3 py-2.5 rounded-t-lg bg-header-band text-on-fill">
        <h3 className="text-sm font-semibold uppercase tracking-wider">Leaderboard</h3>
        {tournamentName ? <span className="text-sm uppercase tracking-wider truncate">{tournamentName}</span> : null}
      </div>
      <div className="overflow-x-auto">
        <table className="w-full min-w-[480px] text-sm">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr>
              <th className="pl-3 pr-2 py-2 text-left">Pos</th>
              <th className="px-2 py-2 text-left">Player</th>
              <th className="px-2 py-2 text-left">
                <ColumnHeaderFilter
                  ariaLabel="Filter leaderboard by team"
                  value={teamFilter}
                  onChange={setTeamFilter}
                  options={teamOptions}
                />
              </th>
              <th className="px-2 py-2 text-right">Score</th>
              <th className="px-2 py-2 text-right">R1</th>
              <th className="px-2 py-2 text-right">R2</th>
              <th className="px-2 py-2 text-right">R3</th>
              <th className="px-2 py-2 text-right">R4</th>
              <th className="pl-2 pr-3 py-2 text-right">Tot</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-hairline-soft">
            {filteredLeaderboard.length === 0 ? (
              <tr>
                <td colSpan={9} className="px-3 py-6 text-center text-ink-soft italic">
                  No players match this filter.
                </td>
              </tr>
            ) : (
              filteredLeaderboard.map((entry) => (
                <tr key={playerNames(entry)}>
                  <td className="pl-3 pr-2 py-2 text-ink-muted tabular-nums">{entry.position}</td>
                  <td className="px-2 py-2 text-ink">{playerNames(entry)}</td>
                  <td className="px-2 py-2">
                    <TeamChips entry={entry} />
                  </td>
                  <td className="px-2 py-2 text-right tabular-nums font-semibold">
                    {formatScoreToPar(entry.scoreToPar)}
                  </td>
                  {[0, 1, 2, 3].map((idx) => (
                    <td key={idx} className="px-2 py-2 text-right tabular-nums text-ink-muted">
                      {entry.roundScores[idx] ?? '—'}
                    </td>
                  ))}
                  <td className="pl-2 pr-3 py-2 text-right tabular-nums font-semibold">
                    {entry.totalStrokes ?? '—'}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}

interface Props {
  report: WeeklyReport;
  finalizeSlot?: ReactNode;
  onGolferClick?: (golferId: string) => void;
}

function ScoreboardView({ report, finalizeSlot, onGolferClick }: Props): ReactNode {
  const teams = scoreboardTeams(report);
  const { tournament, weeklyTotals, liveLeaderboard: leaderboard } = report;
  const isCompleted = tournament.status === 'completed';
  const statusBanner = isCompleted
    ? 'Results finalized'
    : 'Tournament in progress — scores are live estimates';
  const bannerClass = isCompleted
    ? 'bg-semantic-success-tint/30 border-semantic-success-soft text-semantic-success-ink'
    : 'bg-semantic-warning-tint/40 border-semantic-warning-soft text-semantic-warning-ink';
  const hasLeaderboard = leaderboard.length > 0;

  return (
    <section className="space-y-6">
      <div
        className={`px-4 py-2 border rounded text-sm flex items-center justify-between gap-3 ${bannerClass}`}
      >
        <span>{statusBanner}</span>
        {!isCompleted && finalizeSlot ? finalizeSlot : null}
      </div>

      <div className={hasLeaderboard ? TWO_COLUMN_CLASS : undefined}>
        <div className="space-y-6">
          <TeamCards teams={teams} onGolferClick={onGolferClick} />
          <TeamTable teams={teams} onGolferClick={onGolferClick} />
          <MoneySummary totals={weeklyTotals} />
        </div>
        {hasLeaderboard ? <LeaderboardCard leaderboard={leaderboard} tournamentName={tournament.name} /> : null}
      </div>
    </section>
  );
}

export default ScoreboardView;
