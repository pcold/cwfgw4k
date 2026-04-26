import { useQuery } from '@tanstack/react-query';
import { api, ApiError } from '@/api/client';
import { useLeagueSeason } from '@/context/LeagueSeasonContext';
import { DEFAULT_RULES, ordinal } from './rulesModel';

function RulesPage() {
  const { leaguesLoading, leaguesError, seasonId } = useLeagueSeason();

  const rulesQuery = useQuery({
    queryKey: ['seasonRules', seasonId],
    queryFn: () => api.seasonRules(seasonId!),
    enabled: !!seasonId,
  });

  const tournamentsQuery = useQuery({
    queryKey: ['tournaments', seasonId],
    queryFn: () => api.tournaments(seasonId!),
    enabled: !!seasonId,
  });

  if (leaguesLoading) return <p className="text-gray-400">Loading leagues…</p>;
  if (leaguesError)
    return <p className="text-red-400">Failed to load leagues: {String(leaguesError)}</p>;

  const rules = rulesQuery.data ?? DEFAULT_RULES;
  const schedule = [...(tournamentsQuery.data ?? [])].sort((a, b) =>
    a.startDate.localeCompare(b.startDate),
  );

  return (
    <section className="space-y-6">
      <h2 className="text-xl font-bold">Season Rules</h2>

      {rulesQuery.error && !(rulesQuery.error instanceof ApiError) ? (
        <p className="text-yellow-400 text-sm">
          Could not load season rules; showing defaults.
        </p>
      ) : null}

      <div className="bg-gray-800 rounded-lg p-6">
        <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
          The Draft
        </h3>
        <ul className="text-gray-300 space-y-1 text-sm list-disc list-inside">
          <li>Snake draft, each team drafts PGA Tour players</li>
          <li>
            Players can be split between teams with ownership percentages (earnings divided
            proportionally)
          </li>
        </ul>
      </div>

      <div className="bg-gray-800 rounded-lg p-6">
        <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
          Weekly Payouts
        </h3>
        <p className="text-gray-400 text-sm mb-3">
          Each PGA tournament, the top {rules.payouts.length} finishers among all drafted
          players earn payouts:
        </p>
        <table className="w-full max-w-xs text-sm">
          <thead>
            <tr className="text-gray-400 border-b border-gray-700">
              <th className="text-left py-1">Position</th>
              <th className="text-right py-1">Payout</th>
            </tr>
          </thead>
          <tbody className="text-gray-300">
            {rules.payouts.map((amount, i) => (
              <tr key={i}>
                <td className="py-0.5">{ordinal(i + 1)}</td>
                <td className="text-right">${amount}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="bg-gray-800 rounded-lg p-6">
        <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
          Season Schedule
        </h3>
        <p className="text-gray-300 text-sm mb-3">
          Tournaments on the season calendar. A highlighted multiplier means payouts are
          boosted for that week (e.g. 2x for majors).
        </p>
        {tournamentsQuery.isLoading ? (
          <p className="text-gray-500 text-sm">Loading schedule…</p>
        ) : schedule.length > 0 ? (
          <ul className="text-gray-300 text-sm space-y-0.5">
            {schedule.map((t) => (
              <li key={t.id} className="flex items-baseline gap-2">
                <span className="text-gray-500 tabular-nums w-16 shrink-0">
                  {t.week ? `Wk ${t.week}` : ''}
                </span>
                <span className="flex-1">{t.name}</span>
                {t.payoutMultiplier !== 1 ? (
                  <span className="text-yellow-400 font-semibold tabular-nums">
                    {t.payoutMultiplier}x
                  </span>
                ) : null}
              </li>
            ))}
          </ul>
        ) : (
          <p className="text-gray-500 text-sm">No tournaments scheduled for this season.</p>
        )}
      </div>

      <div className="bg-gray-800 rounded-lg p-6">
        <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
          Tie Handling
        </h3>
        <ul className="text-gray-300 space-y-1 text-sm list-disc list-inside">
          <li>Tied positions average the payouts across all tied slots</li>
          <li>
            Minimum payout of{' '}
            <span className="text-green-400 font-semibold">${rules.tieFloor}</span> per player
            for any tie that overlaps the payout zone
          </li>
        </ul>
      </div>

      <div className="bg-gray-800 rounded-lg p-6">
        <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
          Zero-Sum Scoring
        </h3>
        <ul className="text-gray-300 space-y-1 text-sm list-disc list-inside">
          <li>Each top-N finish earns that payout from every other team</li>
          <li>
            Formula:{' '}
            <code className="bg-gray-700 px-1 rounded">
              weekly +/- = (your earnings × num_teams) − total pot
            </code>
          </li>
          <li>
            The league is zero-sum — every dollar won is a dollar lost by someone else
          </li>
          <li>
            Undrafted players who finish in the payout zone are tracked but don't affect team
            payouts
          </li>
        </ul>
      </div>

      {rules.sideBetRounds.length > 0 ? (
        <div className="bg-gray-800 rounded-lg p-6">
          <h3 className="text-sm font-semibold text-gray-300 uppercase tracking-wider mb-3">
            Side Bets (Rounds {rules.sideBetRounds.join(', ')})
          </h3>
          <ul className="text-gray-300 space-y-1 text-sm list-disc list-inside">
            <li>
              {rules.sideBetRounds.length} separate season-long races — one for each draft
              round
            </li>
            <li>
              Winner = the team whose round-N pick has the highest cumulative earnings across
              all tournaments
            </li>
            <li>
              Winner collects{' '}
              <span className="text-green-400 font-semibold">${rules.sideBetAmount}</span>{' '}
              from every other team
            </li>
            <li>Side bet is not active if all entries are at $0</li>
          </ul>
        </div>
      ) : null}
    </section>
  );
}

export default RulesPage;
