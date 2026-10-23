import { Fragment, useId, useState, type ReactNode } from 'react';
import { m } from 'motion/react';
import type { LiveLeaderboardEntry, WeeklyReport } from '@/shared/api/types';
import { formatDollarsAndCents } from '@/shared/util/money';
import Chip from '@/shared/components/Chip';
import { glideAt } from '@/shared/components/glide';
import ListCard, { CARD_LIST_CLASS, CARD_ROW_CLASS } from '@/shared/components/ListCard';
import MoneyCell from '@/shared/components/MoneyCell';
import PositionCell from '@/shared/components/PositionCell';
import Pulse, { PulseDollars } from '@/shared/components/Pulse';
import WonLostNetCards from '@/shared/components/WonLostNetCards';
import YouTag from '@/shared/components/YouTag';
import ColumnHeaderFilter, { type ColumnHeaderFilterOption } from '@/shared/components/ColumnHeaderFilter';
import PhoneFilter from '@/shared/components/PhoneFilter';
import { TAP_TARGET_CLASS, type SelectOption } from '@/shared/components/formControls';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import { isMyTeam } from '@/features/myTeam/myTeamModel';
import LeaderboardScorecard from './LeaderboardScorecard';
import {
  formatScoreToPar,
  hasPlayerOn,
  hasUndraftedPlayer,
  leaderboardRoundLine,
  leaderboardTeamChips,
  leaderboardTeamNames,
  playerNames,
  playingLabel,
  scoreboardPhase,
  scoreboardTeams,
  type ScoreboardPhase,
  type ScoreboardTeam,
} from './scoreboardModel';

const STATUS_BANNERS: Record<ScoreboardPhase, { text: string; className: string }> = {
  finalized: {
    text: 'Results finalized',
    className: 'bg-semantic-success-tint/30 border-semantic-success-soft text-semantic-success-ink',
  },
  preStart: {
    text: "Play hasn't started — no scores yet",
    className: 'bg-surface-card border-hairline-soft text-ink-secondary',
  },
  live: {
    text: 'Tournament in progress — scores are live estimates',
    className: 'bg-semantic-warning-tint/40 border-semantic-warning-soft text-semantic-warning-ink',
  },
};

const ALL = '';
const UNDRAFTED = '__undrafted__';
const FILTERED_EMPTY = 'No players match this filter.';

const GOLFER_BUTTON_CLASS = `text-ink-secondary hover:underline cursor-pointer bg-transparent border-0 p-0 ${TAP_TARGET_CLASS}`;

// Spelled as on the standings' card rows, which keep theirs private.
const CARD_CELL_CLASS = 'px-3 py-2';

// `grid-cols-1`, not the implicit track, which grows to a table's min-width and scrolls a phone's page sideways.
const TWO_COLUMN_CLASS = 'grid grid-cols-1 gap-6 xl:grid-cols-[minmax(0,5fr)_minmax(0,4fr)] xl:items-start';

interface GolferLineProps {
  team: ScoreboardTeam;
  onGolferClick?: (golferId: string) => void;
}

interface PulsingGolferLineProps extends GolferLineProps {
  /** @see usePulse */
  pulseScope: string;
}

// Each golfer's name, finish and payout keep to one line, so a wrap falls only between golfers.
function GolferLine({ team, onGolferClick, pulseScope }: PulsingGolferLineProps): ReactNode {
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
              <Pulse
                display={`${golfer.tied ? 'T' : ''}${golfer.position ?? '?'}`}
                measure={golfer.position}
                better="lower"
                scope={pulseScope}
              />{' '}
              <span className="tabular-nums">
                <PulseDollars amount={golfer.payout} scope={pulseScope} />
              </span>
              {golfer.ownershipPct < 100 ? ` (${golfer.ownershipPct}%)` : null}
            </span>
          </Fragment>
        );
      })}
    </div>
  );
}

function TeamName({ teamName, mine }: { teamName: string; mine: boolean }): ReactNode {
  return (
    <div className="font-display font-bold text-lg uppercase text-ink">
      {teamName}
      {mine ? <YouTag /> : null}
    </div>
  );
}

interface TeamListProps {
  teams: readonly ScoreboardTeam[];
  myTeamName: string | null;
  onGolferClick?: (golferId: string) => void;
}

interface RankedTeamListProps extends TeamListProps {
  /** @see LiveOverlayQuery.selectionKey */
  selectionKey: string;
}

function TeamCards({ teams, myTeamName, onGolferClick, selectionKey }: RankedTeamListProps): ReactNode {
  return (
    <ol key={selectionKey} aria-label="Weekly results" className={`sm:hidden ${CARD_LIST_CLASS}`}>
      {teams.map((team, index) => (
        <ListCard
          key={team.teamId}
          position={index + 1}
          name={team.teamName}
          nameCase="team"
          fill={isMyTeam(team.teamName, myTeamName) ? 'my-team' : 'plain'}
          detail={
            team.golferScores.length === 0 ? (
              'No golfers in the top 10'
            ) : (
              <GolferLine team={team} onGolferClick={onGolferClick} pulseScope={selectionKey} />
            )
          }
          total={
            <MoneyCell display={formatDollarsAndCents(team.weeklyTotal)} shape="card-end" pulseScope={selectionKey} />
          }
          glideIndex={index}
        />
      ))}
    </ol>
  );
}

function TeamTable({ teams, myTeamName, onGolferClick, selectionKey }: RankedTeamListProps): ReactNode {
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
        <tbody key={selectionKey}>
          {teams.map((team, index) => {
            const mine = isMyTeam(team.teamName, myTeamName);
            const look = CARD_ROW_CLASS[mine ? 'my-team' : 'plain'];
            return (
              <m.tr key={team.teamId} {...glideAt(index)} className={look.row}>
                <PositionCell position={index + 1} />
                <td className={`${CARD_CELL_CLASS} ${look.cell}`}>
                  <TeamName teamName={team.teamName} mine={mine} />
                  <GolferLine team={team} onGolferClick={onGolferClick} pulseScope={selectionKey} />
                </td>
                <td className={`${CARD_CELL_CLASS} ${look.cell} text-right tabular-nums text-ink-secondary`}>
                  <PulseDollars amount={team.topTenEarnings} scope={selectionKey} />
                </td>
                <MoneyCell display={formatDollarsAndCents(team.weeklyTotal)} pulseScope={selectionKey} />
              </m.tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// Before tee-off nothing is paid, so the team list names who is in the field instead of money and finishes.
function FieldLine({ team, onGolferClick }: GolferLineProps): ReactNode {
  return (
    <div className="text-sm text-ink-muted">
      {playingLabel(team)}
      {team.fieldGolfers.map(({ golferId, golferName }) => (
        <Fragment key={golferId}>
          {' · '}
          <span className="whitespace-nowrap">
            {onGolferClick ? (
              <button type="button" onClick={() => onGolferClick(golferId)} className={GOLFER_BUTTON_CLASS}>
                {golferName}
              </button>
            ) : (
              <span className="text-ink-secondary">{golferName}</span>
            )}
          </span>
        </Fragment>
      ))}
    </div>
  );
}

function FieldCards({ teams, myTeamName, onGolferClick }: TeamListProps): ReactNode {
  return (
    <ol aria-label="Weekly results" className={`sm:hidden ${CARD_LIST_CLASS}`}>
      {teams.map((team, index) => (
        <ListCard
          key={team.teamId}
          position={index + 1}
          name={team.teamName}
          nameCase="team"
          fill={isMyTeam(team.teamName, myTeamName) ? 'my-team' : 'plain'}
          detail={<FieldLine team={team} onGolferClick={onGolferClick} />}
          total={null}
        />
      ))}
    </ol>
  );
}

function FieldTable({ teams, myTeamName, onGolferClick }: TeamListProps): ReactNode {
  return (
    <div className="hidden sm:block overflow-x-auto -mx-3 sm:mx-0">
      <table className="w-full min-w-[500px] text-sm border-separate border-spacing-y-1">
        <thead className={COLUMN_HEADER_CLASS}>
          <tr className="text-left">
            <th className="px-3 py-2 w-14 text-center">#</th>
            <th className="px-3 py-2">Team</th>
          </tr>
        </thead>
        <tbody>
          {teams.map((team, index) => {
            const mine = isMyTeam(team.teamName, myTeamName);
            const look = CARD_ROW_CLASS[mine ? 'my-team' : 'plain'];
            return (
              <tr key={team.teamId} className={look.row}>
                <PositionCell position={index + 1} />
                <td className={`${CARD_CELL_CLASS} ${look.cell}`}>
                  <TeamName teamName={team.teamName} mine={mine} />
                  <FieldLine team={team} onGolferClick={onGolferClick} />
                </td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

interface TeamChipsProps {
  entry: LiveLeaderboardEntry;
  /** @see isMyTeam */
  myTeamName: string | null;
}

function TeamChips({ entry, myTeamName }: TeamChipsProps): ReactNode {
  return (
    <div className="flex flex-wrap gap-1">
      {leaderboardTeamChips(entry).map((chip) => {
        switch (chip.kind) {
          case 'team':
            return (
              <Chip key={chip.key} variant={isMyTeam(chip.teamName, myTeamName) ? 'my-team' : 'neutral'}>
                {chip.teamName}
              </Chip>
            );
          case 'undrafted':
            return (
              <Chip key={chip.key} variant="outlined">
                Undrafted
              </Chip>
            );
          default:
            return chip satisfies never;
        }
      })}
    </div>
  );
}

interface LeaderboardListProps {
  entries: readonly LiveLeaderboardEntry[];
  /** The selection's key and the team filter's, so a filter change remounts the rows rather than gliding them. */
  listKey: string;
  /** @see isMyTeam */
  myTeamName: string | null;
  /** @see LeaderboardCard */
  openPlayer: string | null;
  onToggle: (player: string) => void;
}

interface LeaderboardFigureProps {
  entry: LiveLeaderboardEntry;
  /** @see usePulse */
  listKey: string;
}

function LeaderboardPosition({ entry, listKey }: LeaderboardFigureProps): ReactNode {
  return <Pulse display={String(entry.position ?? '—')} measure={entry.position} better="lower" scope={listKey} />;
}

function ScoreToPar({ entry, listKey }: LeaderboardFigureProps): ReactNode {
  return (
    <Pulse display={formatScoreToPar(entry.scoreToPar)} measure={entry.scoreToPar} better="lower" scope={listKey} />
  );
}

const EXPANDABLE_ROW_CLASS = 'cursor-pointer hover:bg-surface-raised/50';

// `h-11` and not `TAP_TARGET_CLASS`: a table row ignores `min-height`, and takes its `height` as the minimum.
const EXPANDABLE_TABLE_ROW_CLASS = `h-11 ${EXPANDABLE_ROW_CLASS}`;

interface ScorecardRow {
  player: string;
  open: boolean;
  /** Null for a row with no scorecard to open. */
  toggle: (() => void) | null;
  scorecardId: string;
}

function scorecardRow(
  entry: LiveLeaderboardEntry,
  { openPlayer, onToggle }: Pick<LeaderboardListProps, 'openPlayer' | 'onToggle'>,
  scorecardId: string,
): ScorecardRow {
  const player = playerNames(entry);
  const expandable = entry.scorecards.length > 0;
  return {
    player,
    open: expandable && openPlayer === player,
    toggle: expandable ? () => onToggle(player) : null,
    scorecardId,
  };
}

// No click handler of its own: its click bubbles to the row's, so the row and the name toggle once either way.
function PlayerName({ row }: { row: ScorecardRow }): ReactNode {
  if (row.toggle === null) return <div className="truncate text-ink">{row.player}</div>;
  return (
    <button
      type="button"
      aria-expanded={row.open}
      aria-controls={row.open ? row.scorecardId : undefined}
      className="flex max-w-full items-baseline gap-1.5 bg-transparent border-0 p-0 text-left text-ink cursor-pointer"
    >
      <span className="truncate">{row.player}</span>
      <span aria-hidden="true" className="shrink-0 text-xs text-ink-muted">
        {row.open ? '▾' : '▸'}
      </span>
    </button>
  );
}

function LeaderboardRows({ entries, listKey, myTeamName, ...toggling }: LeaderboardListProps): ReactNode {
  const idPrefix = useId();
  if (entries.length === 0)
    return <p className="px-3 py-6 text-center text-sm text-ink-soft italic">{FILTERED_EMPTY}</p>;
  return (
    <ol key={listKey} aria-label="Leaderboard" className="text-sm divide-y divide-hairline-soft">
      {entries.map((entry, index) => {
        const roundLine = leaderboardRoundLine(entry);
        const row = scorecardRow(entry, toggling, `${idPrefix}-${index}`);
        return (
          <m.li key={row.player} {...glideAt(index)}>
            {/* eslint-disable-next-line jsx-a11y/click-events-have-key-events, jsx-a11y/no-static-element-interactions
                -- a wider tap area for the name's button, which is the keyboard and screen-reader path */}
            <div
              onClick={row.toggle ?? undefined}
              className={`flex items-baseline gap-3 px-3 py-2 ${row.toggle ? `${TAP_TARGET_CLASS} ${EXPANDABLE_ROW_CLASS}` : ''}`}
            >
              <span className="w-8 shrink-0 text-ink-muted tabular-nums">
                <LeaderboardPosition entry={entry} listKey={listKey} />
              </span>
              <div className="min-w-0 flex-1">
                <PlayerName row={row} />
                <div className="mt-1 flex flex-wrap items-center gap-x-2 gap-y-1">
                  <TeamChips entry={entry} myTeamName={myTeamName} />
                  {roundLine ? <span className="text-xs text-ink-muted tabular-nums">{roundLine}</span> : null}
                </div>
              </div>
              <span className="shrink-0 font-semibold tabular-nums">
                <ScoreToPar entry={entry} listKey={listKey} />
              </span>
            </div>
            {row.open ? <LeaderboardScorecard scorecards={entry.scorecards} id={row.scorecardId} /> : null}
          </m.li>
        );
      })}
    </ol>
  );
}

interface LeaderboardTableProps extends LeaderboardListProps {
  teamFilter: string;
  onTeamFilterChange: (value: string) => void;
  teamOptions: readonly ColumnHeaderFilterOption[];
}

function LeaderboardTable({
  entries,
  listKey,
  myTeamName,
  teamFilter,
  onTeamFilterChange,
  teamOptions,
  ...toggling
}: LeaderboardTableProps): ReactNode {
  const idPrefix = useId();
  return (
    <div className="hidden sm:block overflow-x-auto">
      <table aria-label="Leaderboard" className="w-full min-w-[480px] text-sm">
        <thead className={COLUMN_HEADER_CLASS}>
          <tr>
            <th className="pl-3 pr-2 py-2 text-left">Pos</th>
            <th className="px-2 py-2 text-left">Player</th>
            <th className="px-2 py-2 text-left">
              <ColumnHeaderFilter
                ariaLabel="Filter leaderboard by team"
                value={teamFilter}
                onChange={onTeamFilterChange}
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
        <tbody key={listKey} className="divide-y divide-hairline-soft">
          {entries.length === 0 ? (
            <tr>
              <td colSpan={9} className="px-3 py-6 text-center text-ink-soft italic">
                {FILTERED_EMPTY}
              </td>
            </tr>
          ) : (
            entries.map((entry, index) => {
              const row = scorecardRow(entry, toggling, `${idPrefix}-${index}`);
              return (
                <Fragment key={row.player}>
                  <m.tr
                    {...glideAt(index)}
                    onClick={row.toggle ?? undefined}
                    className={`${row.toggle ? EXPANDABLE_TABLE_ROW_CLASS : ''} ${row.open ? 'border-b-0' : ''}`}
                  >
                    <td className="pl-3 pr-2 py-2 text-ink-muted tabular-nums">
                      <LeaderboardPosition entry={entry} listKey={listKey} />
                    </td>
                    <td className="px-2 py-2">
                      <PlayerName row={row} />
                    </td>
                    <td className="px-2 py-2">
                      <TeamChips entry={entry} myTeamName={myTeamName} />
                    </td>
                    <td className="px-2 py-2 text-right tabular-nums font-semibold">
                      <ScoreToPar entry={entry} listKey={listKey} />
                    </td>
                    {[0, 1, 2, 3].map((idx) => (
                      <td key={idx} className="px-2 py-2 text-right tabular-nums text-ink-muted">
                        {entry.roundScores[idx] ?? '—'}
                      </td>
                    ))}
                    <td className="pl-2 pr-3 py-2 text-right tabular-nums font-semibold">
                      {entry.totalStrokes ?? '—'}
                    </td>
                  </m.tr>
                  {row.open ? (
                    <tr>
                      <td colSpan={9} className="p-0">
                        <LeaderboardScorecard scorecards={entry.scorecards} id={row.scorecardId} />
                      </td>
                    </tr>
                  ) : null}
                </Fragment>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}

function filterLeaderboard(
  leaderboard: readonly LiveLeaderboardEntry[],
  teamFilter: string,
): readonly LiveLeaderboardEntry[] {
  if (teamFilter === ALL) return leaderboard;
  if (teamFilter === UNDRAFTED) return leaderboard.filter(hasUndraftedPlayer);
  return leaderboard.filter((entry) => hasPlayerOn(entry, teamFilter));
}

interface LeaderboardCardProps {
  leaderboard: readonly LiveLeaderboardEntry[];
  tournamentName: string | null;
  /** @see isMyTeam */
  myTeamName: string | null;
  /** @see LiveOverlayQuery.selectionKey */
  selectionKey: string;
}

// The open player is held with the selection it was opened under: the minute refresh keeps the selection, so it stays
// open, and a new tournament or season starts closed.
function LeaderboardCard({ leaderboard, tournamentName, myTeamName, selectionKey }: LeaderboardCardProps): ReactNode {
  const [teamFilter, setTeamFilter] = useState<string>(ALL);
  const [expanded, setExpanded] = useState<{ selectionKey: string; player: string } | null>(null);
  const openPlayer = expanded?.selectionKey === selectionKey ? expanded.player : null;
  const onToggle = (player: string): void => setExpanded(openPlayer === player ? null : { selectionKey, player });

  const teamChoices: readonly SelectOption[] = [
    ...leaderboardTeamNames(leaderboard).map((name) => ({ value: name, label: name })),
    ...(leaderboard.some(hasUndraftedPlayer) ? [{ value: UNDRAFTED, label: 'Undrafted' }] : []),
  ];
  const filteredLeaderboard = filterLeaderboard(leaderboard, teamFilter);
  const listKey = `${selectionKey}|${teamFilter}`;

  return (
    <div className="bg-surface-card rounded-lg">
      <div className="flex items-center justify-between gap-3 px-3 py-2.5 rounded-t-lg bg-header-band text-on-fill">
        <h3 className="text-sm font-semibold uppercase tracking-wider">Leaderboard</h3>
        {tournamentName ? <span className="text-sm uppercase tracking-wider truncate">{tournamentName}</span> : null}
      </div>
      <div className="sm:hidden">
        <div className="p-3">
          <PhoneFilter
            label="Team"
            value={teamFilter}
            options={[{ value: ALL, label: 'All teams' }, ...teamChoices]}
            onChange={setTeamFilter}
          />
        </div>
        <LeaderboardRows
          entries={filteredLeaderboard}
          listKey={listKey}
          myTeamName={myTeamName}
          openPlayer={openPlayer}
          onToggle={onToggle}
        />
      </div>
      <LeaderboardTable
        entries={filteredLeaderboard}
        listKey={listKey}
        myTeamName={myTeamName}
        teamFilter={teamFilter}
        onTeamFilterChange={setTeamFilter}
        teamOptions={[{ value: ALL, label: 'Team' }, ...teamChoices]}
        openPlayer={openPlayer}
        onToggle={onToggle}
      />
    </div>
  );
}

interface Props {
  report: WeeklyReport;
  /** @see isMyTeam */
  myTeamName: string | null;
  finalizeSlot?: ReactNode;
  onGolferClick?: (golferId: string) => void;
  /** @see LiveOverlayQuery.selectionKey */
  selectionKey: string;
}

function ScoreboardView({ report, myTeamName, finalizeSlot, onGolferClick, selectionKey }: Props): ReactNode {
  const teams = scoreboardTeams(report);
  const { tournament, weeklyTotals, liveLeaderboard: leaderboard } = report;
  const phase = scoreboardPhase(report);
  const banner = STATUS_BANNERS[phase];
  const hasLeaderboard = leaderboard.length > 0;

  return (
    <section className="space-y-6">
      <div
        className={`px-4 py-2 border rounded text-sm flex flex-wrap items-center justify-between gap-3 ${banner.className}`}
      >
        <span>{banner.text}</span>
        {finalizeSlot}
      </div>

      <div className={hasLeaderboard ? TWO_COLUMN_CLASS : undefined}>
        <div className="space-y-6">
          {phase === 'preStart' ? (
            <>
              <FieldCards teams={teams} myTeamName={myTeamName} onGolferClick={onGolferClick} />
              <FieldTable teams={teams} myTeamName={myTeamName} onGolferClick={onGolferClick} />
            </>
          ) : (
            <>
              <TeamCards
                teams={teams}
                myTeamName={myTeamName}
                onGolferClick={onGolferClick}
                selectionKey={selectionKey}
              />
              <TeamTable
                teams={teams}
                myTeamName={myTeamName}
                onGolferClick={onGolferClick}
                selectionKey={selectionKey}
              />
            </>
          )}
          <WonLostNetCards
            won={formatDollarsAndCents(weeklyTotals.won)}
            lost={formatDollarsAndCents(weeklyTotals.lost)}
            net={formatDollarsAndCents(weeklyTotals.net)}
          />
        </div>
        {hasLeaderboard ? (
          <LeaderboardCard
            leaderboard={leaderboard}
            tournamentName={tournament.name}
            myTeamName={myTeamName}
            selectionKey={selectionKey}
          />
        ) : null}
      </div>
    </section>
  );
}

export default ScoreboardView;
