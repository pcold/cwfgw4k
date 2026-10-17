import type { ReactNode } from 'react';
import type { RosterPick, RosterTeam } from '@/shared/api/types';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';

interface Props {
  teams: RosterTeam[];
  sideBetRounds: readonly number[];
  onGolferClick?: (golferId: string) => void;
}

interface SharedPlayer {
  name: string;
  owners: { team: string; pct: number }[];
}

const ROUNDS = [1, 2, 3, 4, 5, 6, 7, 8] as const;

const GOLFER_BUTTON_CLASS =
  `font-semibold text-ink-body truncate bg-transparent border-0 p-0 hover:underline cursor-pointer ${TAP_TARGET_CLASS}`;

function pickFor(team: RosterTeam, round: number): RosterPick | undefined {
  return team.picks.find((p) => p.round === round);
}

function computeSharedPlayers(teams: RosterTeam[]): SharedPlayer[] {
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

function RostersView({ teams, sideBetRounds, onGolferClick }: Props): ReactNode {
  if (teams.length === 0) {
    return (
      <p className="text-ink-muted text-center py-12">
        No rosters uploaded yet. Use the Admin link at the bottom of the page to upload team rosters.
      </p>
    );
  }

  const sharedPlayers = computeSharedPlayers(teams);

  return (
    <section className="space-y-6">
      <div className="overflow-x-auto -mx-3 sm:mx-0">
        <table className="w-full text-xs border-collapse">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr className="bg-gold text-on-gold">
              <th className="border border-hairline-strong px-1 py-1.5 w-10 sm:w-12 text-center sticky left-0 z-20 bg-gold">
                RD
              </th>
              {teams.map((team, i) => (
                <th
                  key={team.teamId}
                  className="border border-hairline-strong px-1 py-1.5 text-center min-w-[100px]"
                >
                  <div>{team.teamNumber ?? i + 1}</div>
                  <div>{team.teamName}</div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ROUNDS.map((round) => {
              const rowBg = sideBetRounds.includes(round) ? 'bg-surface-card/70' : 'bg-surface-card';
              return (
              <tr key={round} className={rowBg}>
                <td className="border border-hairline px-1 py-1.5 text-center font-bold text-ink-muted sticky left-0 z-10 bg-surface-card">
                  {round}
                </td>
                {teams.map((team) => {
                  const pick = pickFor(team, round);
                  return (
                    <td
                      key={`${team.teamId}-${round}`}
                      className="border border-hairline px-2 py-1.5 text-center"
                    >
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
                            <div className="font-semibold text-ink-body truncate">
                              {pick.golferName}
                            </div>
                          )}
                          {pick.ownershipPct < 100 ? (
                            <div className="text-ink-soft text-xs">{pick.ownershipPct}%</div>
                          ) : null}
                        </div>
                      ) : (
                        <span className="text-ink-faint">—</span>
                      )}
                    </td>
                  );
                })}
              </tr>
              );
            })}
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
