import type { RosterPick, RosterTeam } from '@/api/types';

interface Props {
  teams: RosterTeam[];
  onGolferClick?: (golferId: string) => void;
}

interface SharedPlayer {
  name: string;
  owners: { team: string; pct: number }[];
}

const ROUNDS = [1, 2, 3, 4, 5, 6, 7, 8] as const;

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

function RostersView({ teams, onGolferClick }: Props) {
  if (teams.length === 0) {
    return (
      <p className="text-gray-400 text-center py-12">
        No rosters uploaded yet. Use the Admin tab to upload team rosters.
      </p>
    );
  }

  const sharedPlayers = computeSharedPlayers(teams);

  return (
    <section className="space-y-6">
      <header className="border-b border-gray-700 pb-3">
        <h2 className="text-xl font-semibold text-green-400">Team Rosters</h2>
      </header>

      <div className="overflow-x-auto">
        <table className="w-full text-xs border-collapse">
          <thead>
            <tr className="bg-yellow-600 text-black font-bold">
              <th className="border border-gray-600 px-1 py-1.5 w-12 text-center">RD</th>
              {teams.map((team, i) => (
                <th
                  key={team.teamId}
                  className="border border-gray-600 px-1 py-1.5 text-center min-w-[100px]"
                >
                  <div>{i + 1}</div>
                  <div className="text-xs">{team.teamName}</div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {ROUNDS.map((round) => (
              <tr
                key={round}
                className={round >= 5 ? 'bg-gray-800/70' : 'bg-gray-800'}
              >
                <td className="border border-gray-700 px-1 py-1.5 text-center font-bold text-gray-400">
                  {round}
                </td>
                {teams.map((team) => {
                  const pick = pickFor(team, round);
                  return (
                    <td
                      key={`${team.teamId}-${round}`}
                      className="border border-gray-700 px-2 py-1.5 text-center"
                    >
                      {pick ? (
                        <div>
                          {onGolferClick && pick.golferId ? (
                            <button
                              type="button"
                              onClick={() => onGolferClick(pick.golferId)}
                              className="font-semibold text-gray-200 truncate bg-transparent border-0 p-0 hover:underline cursor-pointer"
                            >
                              {pick.golferName}
                            </button>
                          ) : (
                            <div className="font-semibold text-gray-200 truncate">
                              {pick.golferName}
                            </div>
                          )}
                          {pick.ownershipPct < 100 ? (
                            <div className="text-gray-500 text-xs">{pick.ownershipPct}%</div>
                          ) : null}
                        </div>
                      ) : (
                        <span className="text-gray-600">—</span>
                      )}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {sharedPlayers.length > 0 ? (
        <div className="text-xs text-gray-400">
          <span className="font-semibold text-gray-300">Shared Players:</span>
          {sharedPlayers.map((sp) => (
            <span key={sp.name} className="ml-3">
              <span className="text-gray-200">{sp.name}</span>
              {sp.owners.map((owner, j) => (
                <span key={owner.team}>
                  {j > 0 ? <span className="text-gray-600"> / </span> : null}
                  <span className="text-gray-400">
                    {' '}
                    {owner.team} {owner.pct}%
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
