import { useMemo, useState, type ReactNode } from 'react';
import { formatDollarsAndCents, moneyToneClass } from '@/shared/util/money';
import ColumnHeaderFilter from '@/shared/components/ColumnHeaderFilter';
import type { PlayerRankingsRow } from '@/shared/api/types';
import { TAP_TARGET_CLASS, type SelectOption } from '@/shared/components/formFields';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import ListCard, { CARD_LIST_CLASS } from '@/shared/components/ListCard';
import { MONEY_FIGURE_CLASS } from '@/shared/components/MoneyCell';
import PhoneFilter from '@/shared/components/PhoneFilter';
import YouTag from '@/shared/components/YouTag';
import { isMyTeam } from '@/features/myTeam/myTeamModel';

interface Props {
  players: PlayerRankingsRow[];
  /** @see isMyTeam */
  myTeamName: string | null;
  onGolferClick?: (golferId: string) => void;
}

const ALL = '';
const UNDRAFTED = '__undrafted__';
const FILTERED_EMPTY = 'No players match this filter.';

interface PlayerListProps {
  readonly players: readonly PlayerRankingsRow[];
  readonly myTeamName: string | null;
  readonly onGolferClick: ((golferId: string) => void) | undefined;
}

/** The phone select shows no label, so a picked round has to name itself. */
function phoneRoundOption(option: SelectOption): SelectOption {
  return option.value === UNDRAFTED ? option : { ...option, label: `Rd ${option.label}` };
}

interface GolferNameProps {
  readonly player: PlayerRankingsRow;
  readonly onGolferClick: ((golferId: string) => void) | undefined;
}

/** `truncate` on the button too: the name line's ellipsis can't reach inside an inline-block. */
function GolferName({ player, onGolferClick }: GolferNameProps): ReactNode {
  const golferId = player.golferId;
  if (!golferId || !onGolferClick) return player.name;
  return (
    <button
      type="button"
      className={`max-w-full truncate text-left hover:text-primary-ink hover:underline ${TAP_TARGET_CLASS}`}
      onClick={() => onGolferClick(golferId)}
    >
      {player.name}
    </button>
  );
}

interface DraftingTeamProps {
  readonly teamName: string;
  readonly myTeamName: string | null;
}

/** The tag alone: the row is a golfer's, so a team's fill and ring would mark the wrong thing. */
function DraftingTeam({ teamName, myTeamName }: DraftingTeamProps): ReactNode {
  return (
    <>
      <span className="uppercase">{teamName}</span>
      {isMyTeam(teamName, myTeamName) ? <YouTag /> : null}
    </>
  );
}

interface PlayerDetailProps {
  readonly player: PlayerRankingsRow;
  readonly myTeamName: string | null;
}

function PlayerDetail({ player, myTeamName }: PlayerDetailProps): ReactNode {
  return (
    <>
      {player.teamName ? <DraftingTeam teamName={player.teamName} myTeamName={myTeamName} /> : 'Undrafted'}
      {player.draftRound !== null && ` · Rd ${player.draftRound}`}
      {` · ${player.topTens} ${player.topTens === 1 ? 'top 10' : 'top 10s'}`}
    </>
  );
}

function PlayerCards({ players, myTeamName, onGolferClick }: PlayerListProps): ReactNode {
  if (players.length === 0) return <p className="text-ink-soft italic">{FILTERED_EMPTY}</p>;
  return (
    <ol aria-label="Players" className={CARD_LIST_CLASS}>
      {players.map((player, index) => {
        const totalEarnings = formatDollarsAndCents(player.totalEarnings);
        return (
          <ListCard
            key={player.key}
            position={index + 1}
            name={<GolferName player={player} onGolferClick={onGolferClick} />}
            nameCase="golfer"
            detail={<PlayerDetail player={player} myTeamName={myTeamName} />}
            total={
              <div className={`px-3 shrink-0 flex items-center ${MONEY_FIGURE_CLASS} ${moneyToneClass(totalEarnings)}`}>
                {totalEarnings}
              </div>
            }
          />
        );
      })}
    </ol>
  );
}

function PlayerRows({ players, myTeamName, onGolferClick }: PlayerListProps): ReactNode {
  return (
    <tbody className="divide-y divide-hairline-soft">
      {players.length === 0 ? (
        <tr>
          <td colSpan={6} className="px-3 py-6 text-center text-ink-soft italic">
            {FILTERED_EMPTY}
          </td>
        </tr>
      ) : (
        players.map((player, index) => {
          const golferId = player.golferId;
          const clickable = golferId && onGolferClick ? { golferId, onGolferClick } : null;
          const totalEarnings = formatDollarsAndCents(player.totalEarnings);
          return (
            <tr key={player.key} className="hover:bg-surface-card/60">
              <td className="px-3 py-2 text-ink-soft tabular-nums">{index + 1}</td>
              <td className="px-3 py-2 font-medium text-ink">
                {clickable ? (
                  <button
                    type="button"
                    className={`text-left hover:text-primary-ink hover:underline ${TAP_TARGET_CLASS}`}
                    onClick={() => clickable.onGolferClick(clickable.golferId)}
                  >
                    {player.name}
                  </button>
                ) : (
                  player.name
                )}
              </td>
              <td className="px-3 py-2 text-right tabular-nums">{player.topTens}</td>
              <td className={`px-3 py-2 text-right tabular-nums ${moneyToneClass(totalEarnings)}`}>
                {totalEarnings}
              </td>
              <td className="px-3 py-2 text-ink-secondary">
                {player.teamName ? (
                  <DraftingTeam teamName={player.teamName} myTeamName={myTeamName} />
                ) : (
                  <span className="text-ink-soft italic">undrafted</span>
                )}
              </td>
              <td className="px-3 py-2 text-right tabular-nums text-ink-muted">
                {player.draftRound ?? '—'}
              </td>
            </tr>
          );
        })
      )}
    </tbody>
  );
}

function PlayerRankingsView({ players, myTeamName, onGolferClick }: Props): ReactNode {
  const [teamFilter, setTeamFilter] = useState<string>(ALL);
  const [roundFilter, setRoundFilter] = useState<string>(ALL);

  const teamChoices = useMemo<readonly SelectOption[]>(() => {
    const names = new Set<string>();
    let hasUndrafted = false;
    for (const p of players) {
      if (p.teamName) names.add(p.teamName);
      else hasUndrafted = true;
    }
    const sorted = Array.from(names).sort((a, b) => a.localeCompare(b));
    return [
      ...sorted.map((name) => ({ value: name, label: name })),
      ...(hasUndrafted ? [{ value: UNDRAFTED, label: 'Undrafted' }] : []),
    ];
  }, [players]);

  const roundChoices = useMemo<readonly SelectOption[]>(() => {
    const rounds = new Set<number>();
    let hasUndrafted = false;
    for (const p of players) {
      if (p.draftRound !== null) rounds.add(p.draftRound);
      else hasUndrafted = true;
    }
    const sorted = Array.from(rounds).sort((a, b) => a - b);
    return [
      ...sorted.map((r) => ({ value: String(r), label: String(r) })),
      ...(hasUndrafted ? [{ value: UNDRAFTED, label: 'Undrafted' }] : []),
    ];
  }, [players]);

  const filtered = useMemo(() => {
    const matchesTeam = (p: PlayerRankingsRow) => {
      if (teamFilter === ALL) return true;
      if (teamFilter === UNDRAFTED) return !p.teamName;
      return p.teamName === teamFilter;
    };
    const matchesRound = (p: PlayerRankingsRow) => {
      if (roundFilter === ALL) return true;
      if (roundFilter === UNDRAFTED) return p.draftRound === null;
      return String(p.draftRound) === roundFilter;
    };
    return players.filter((p) => matchesTeam(p) && matchesRound(p));
  }, [players, teamFilter, roundFilter]);

  if (players.length === 0) {
    return <p className="text-ink-muted">No players with a top 10 finish yet.</p>;
  }

  return (
    <section className="space-y-4">
      <div className="sm:hidden space-y-3">
        <div className="grid grid-cols-2 gap-2">
          <PhoneFilter
            label="Team"
            value={teamFilter}
            options={[{ value: ALL, label: 'All teams' }, ...teamChoices]}
            onChange={setTeamFilter}
          />
          <PhoneFilter
            label="Draft round"
            value={roundFilter}
            options={[{ value: ALL, label: 'All rounds' }, ...roundChoices.map(phoneRoundOption)]}
            onChange={setRoundFilter}
          />
        </div>
        <PlayerCards players={filtered} myTeamName={myTeamName} onGolferClick={onGolferClick} />
      </div>
      <div className="hidden sm:block overflow-x-auto -mx-3 sm:mx-0">
        <table className="min-w-full text-sm">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr className="text-left">
              <th className="px-3 py-2 w-12">#</th>
              <th className="px-3 py-2">Player</th>
              <th className="px-3 py-2 text-right">Top 10s</th>
              <th className="px-3 py-2 text-right">Total $</th>
              <th className="px-3 py-2">
                <ColumnHeaderFilter
                  ariaLabel="Filter by team"
                  value={teamFilter}
                  onChange={setTeamFilter}
                  options={[{ value: ALL, label: 'Team' }, ...teamChoices]}
                />
              </th>
              <th className="px-3 py-2 text-right">
                <ColumnHeaderFilter
                  ariaLabel="Filter by draft round"
                  value={roundFilter}
                  onChange={setRoundFilter}
                  options={[{ value: ALL, label: 'Round' }, ...roundChoices]}
                />
              </th>
            </tr>
          </thead>
          <PlayerRows players={filtered} myTeamName={myTeamName} onGolferClick={onGolferClick} />
        </table>
      </div>
    </section>
  );
}

export default PlayerRankingsView;
