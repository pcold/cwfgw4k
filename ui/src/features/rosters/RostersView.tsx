import type { ReactNode } from 'react';
import type { RosterPick, RosterTeam } from '@/shared/api/types';
import { TAP_TARGET_CLASS } from '@/shared/components/formControls';
import LateRowTag from '@/shared/components/LateRowTag';
import ListCard, { CARD_LIST_CLASS, CARD_NAME_BUTTON_CLASS } from '@/shared/components/ListCard';
import ReportGridCell from '@/shared/components/ReportGridCell';
import ReportGridHead from '@/shared/components/ReportGridHead';
import ReportGridRoundLabel from '@/shared/components/ReportGridRoundLabel';
import { REPORT_GRID_TABLE_CLASS } from '@/shared/components/reportGrid';
import TeamStrip from '@/shared/components/TeamStrip';
import { isMyTeam } from '@/features/myTeam/myTeamModel';

interface Props {
  readonly teams: readonly RosterTeam[];
  readonly sideBetRounds: readonly number[];
  /** Null, or an id the season no longer has, shows the visitor's own team, else the first. */
  readonly selectedTeamId: string | null;
  /** @see isMyTeam */
  readonly myTeamName: string | null;
  readonly onSelectTeam: (teamId: string) => void;
  readonly onGolferClick?: (golferId: string) => void;
}

interface SharedPlayer {
  readonly name: string;
  readonly owners: readonly { readonly team: string; readonly pct: number }[];
}

const ROUNDS = [1, 2, 3, 4, 5, 6, 7, 8] as const;

const GOLFER_BUTTON_CLASS =
  'font-semibold text-ink-body truncate bg-transparent border-0 p-0 hover:underline cursor-pointer ' + TAP_TARGET_CLASS;

function pickFor(team: RosterTeam, round: number): RosterPick | undefined {
  return team.picks.find((p) => p.round === round);
}

function computeSharedPlayers(teams: readonly RosterTeam[]): SharedPlayer[] {
  const byName = new Map<string, { team: string; pct: number }[]>();
  for (const team of teams) {
    for (const pick of team.picks) {
      const owners = byName.get(pick.golferName) ?? [];
      owners.push({ team: team.teamName, pct: pick.ownershipPct });
      byName.set(pick.golferName, owners);
    }
  }
  return Array.from(byName.entries())
    .filter(([, owners]) => owners.length > 1)
    .map(([name, owners]) => ({ name, owners }))
    .sort((a, b) => a.name.localeCompare(b.name));
}

interface PickNameProps {
  readonly pick: RosterPick;
  readonly onGolferClick: ((golferId: string) => void) | undefined;
}

function PickName({ pick, onGolferClick }: PickNameProps): ReactNode {
  if (!onGolferClick || !pick.golferId) return pick.golferName;
  return (
    <button type="button" onClick={() => onGolferClick(pick.golferId)} className={CARD_NAME_BUTTON_CLASS}>
      {pick.golferName}
    </button>
  );
}

interface TeamPicksProps {
  readonly team: RosterTeam;
  readonly sideBetRounds: readonly number[];
  readonly onGolferClick: ((golferId: string) => void) | undefined;
}

function TeamPicks({ team, sideBetRounds, onGolferClick }: TeamPicksProps): ReactNode {
  const pickCount = team.picks.length;
  return (
    <>
      <h3 className="font-display font-bold text-sm uppercase tracking-wider text-ink-label">
        {team.teamName} · {pickCount} {pickCount === 1 ? 'pick' : 'picks'}
      </h3>
      <ol aria-label="Picks" className={CARD_LIST_CLASS}>
        {ROUNDS.map((round) => {
          const pick = pickFor(team, round);
          return (
            <ListCard
              key={round}
              position={round}
              positionKind="round"
              name={
                pick ? (
                  <PickName pick={pick} onGolferClick={onGolferClick} />
                ) : (
                  <span className="text-ink-faint">—</span>
                )
              }
              nameCase="golfer"
              detail={pick && pick.ownershipPct < 100 ? `${pick.ownershipPct}%` : null}
              total={
                sideBetRounds.includes(round) ? (
                  <div className="shrink-0 flex items-center px-3">
                    <LateRowTag variant="late-row" />
                  </div>
                ) : null
              }
            />
          );
        })}
      </ol>
    </>
  );
}

function RostersView({
  teams,
  sideBetRounds,
  selectedTeamId,
  myTeamName,
  onSelectTeam,
  onGolferClick,
}: Props): ReactNode {
  const selectedTeam =
    teams.find((team) => team.teamId === selectedTeamId) ??
    teams.find((team) => isMyTeam(team.teamName, myTeamName)) ??
    teams[0];
  if (selectedTeam === undefined) {
    return (
      <p className="text-ink-muted text-center py-12">
        No rosters uploaded yet. Use the Admin link at the bottom of the page to upload team rosters.
      </p>
    );
  }

  const sharedPlayers = computeSharedPlayers(teams);
  const gridTeams = teams.map((team, i) => ({
    teamId: team.teamId,
    number: team.teamNumber ?? i + 1,
    name: team.teamName,
  }));

  return (
    <section className="space-y-6">
      <div className="sm:hidden space-y-3">
        <TeamStrip teams={teams} selectedTeamId={selectedTeam.teamId} onSelect={onSelectTeam} />
        <TeamPicks team={selectedTeam} sideBetRounds={sideBetRounds} onGolferClick={onGolferClick} />
      </div>

      <div className="hidden sm:block overflow-x-auto -mx-3 sm:mx-0">
        <table className={REPORT_GRID_TABLE_CLASS}>
          <ReportGridHead cornerLabel="RD" teams={gridTeams} />
          <tbody>
            {ROUNDS.map((round) => (
              <tr key={round}>
                <ReportGridRoundLabel round={round} lateRow={sideBetRounds.includes(round)} />
                {teams.map((team) => {
                  const pick = pickFor(team, round);
                  return (
                    <ReportGridCell key={`${team.teamId}-${round}`}>
                      {pick ? (
                        <div>
                          {onGolferClick && pick.golferId ? (
                            <button
                              type="button"
                              onClick={() => onGolferClick(pick.golferId)}
                              className={GOLFER_BUTTON_CLASS}
                            >
                              {pick.golferName}
                            </button>
                          ) : (
                            <div className="font-semibold text-ink-body truncate">{pick.golferName}</div>
                          )}
                          {pick.ownershipPct < 100 ? (
                            <div className="text-ink-soft text-xs">{pick.ownershipPct}%</div>
                          ) : null}
                        </div>
                      ) : (
                        <span className="text-ink-faint">—</span>
                      )}
                    </ReportGridCell>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {sharedPlayers.length > 0 ? (
        <div className="text-xs text-ink-muted">
          <span className="font-semibold text-ink-secondary">Shared Players:</span>
          {sharedPlayers.map((sp) => (
            <span key={sp.name} className="ml-3">
              <span className="text-ink-body">{sp.name}</span>
              {sp.owners.map((owner, j) => (
                <span key={owner.team}>
                  {j > 0 ? <span className="text-ink-faint"> / </span> : null}
                  <span className="text-ink-muted">
                    {' '}
                    <span className="uppercase">{owner.team}</span> {owner.pct}%
                  </span>
                </span>
              ))}
            </span>
          ))}
        </div>
      ) : null}
    </section>
  );
}

export default RostersView;
