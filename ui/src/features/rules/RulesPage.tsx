import type { ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { SeasonRules, Tournament } from '@/shared/api/types';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState } from '@/shared/components/QueryState';
import PageTitle from '@/shared/components/PageTitle';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import { formatUnsignedDollarsAndCents } from '@/shared/util/money';
import { ordinal } from './rulesModel';

function RulesPage(): ReactNode {
  const { leaguesLoading, leaguesError, seasonId } = useLeagueSeason();

  const rulesQuery = useQuery({
    queryKey: queryKeys.seasonRules.bySeason(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.seasonRules(seasonId),
  });

  const tournamentsQuery = useQuery({
    queryKey: queryKeys.tournaments.bySeason(seasonId),
    queryFn: seasonId === null ? skipToken : () => api.tournaments(seasonId),
  });

  function renderBody(): ReactNode {
    if (leaguesLoading) return <p className="text-ink-muted">Loading leagues…</p>;
    if (leaguesError)
      return <p className="text-semantic-danger">Failed to load leagues: {String(leaguesError)}</p>;

    return (
      <>
        <div className="bg-surface-card rounded-lg p-6">
          <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
            The Draft
          </h3>
          <ul className="text-ink-secondary space-y-1 text-sm list-disc list-inside">
            <li>Snake draft, each team drafts PGA Tour players</li>
            <li>
              Players can be split between teams with ownership percentages (earnings divided
              proportionally)
            </li>
          </ul>
        </div>

        <QueryState query={rulesQuery} label="season rules">
          {(rules) => <RulesView rules={rules} />}
        </QueryState>

        <div className="bg-surface-card rounded-lg p-6">
          <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
            Season Schedule
          </h3>
          <p className="text-ink-secondary text-sm mb-3">
            Tournaments on the season calendar. A highlighted multiplier means payouts are
            boosted for that week (e.g. 2x for majors).
          </p>
          <QueryState query={tournamentsQuery} label="schedule">
            {(tournaments) => <ScheduleList tournaments={tournaments} />}
          </QueryState>
        </div>
      </>
    );
  }

  return (
    <section className="space-y-6">
      <PageTitle title="Season Rules" actions={<LeagueSeasonPicker />} />
      {renderBody()}
    </section>
  );
}

interface RulesViewProps {
  rules: SeasonRules;
}

function RulesView({ rules }: RulesViewProps): ReactNode {
  return (
    <>
      <div className="bg-surface-card rounded-lg p-6">
        <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
          Weekly Payouts
        </h3>
        <p className="text-ink-muted text-sm mb-3">
          Each PGA tournament, the top {rules.payouts.length} finishers among all drafted
          players earn payouts:
        </p>
        <table className="w-full max-w-xs text-sm">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr className="border-b border-hairline">
              <th className="text-left py-1">Position</th>
              <th className="text-right py-1">Payout</th>
            </tr>
          </thead>
          <tbody className="text-ink-secondary">
            {rules.payouts.map((amount, i) => (
              // eslint-disable-next-line react/no-array-index-key -- a payout row is its finishing position.
              <tr key={i}>
                <td className="py-0.5">{ordinal(i + 1)}</td>
                <td className="text-right">{formatUnsignedDollarsAndCents(amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="bg-surface-card rounded-lg p-6">
        <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
          Tie Handling
        </h3>
        <ul className="text-ink-secondary space-y-1 text-sm list-disc list-inside">
          <li>Tied positions average the payouts across all tied slots</li>
          <li>
            Minimum payout of{' '}
            <span className="text-semantic-success font-semibold">
              {formatUnsignedDollarsAndCents(rules.tieFloor)}
            </span>{' '}
            per player
            for any tie that overlaps the payout zone
          </li>
        </ul>
      </div>

      <div className="bg-surface-card rounded-lg p-6">
        <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
          Zero-Sum Scoring
        </h3>
        <ul className="text-ink-secondary space-y-1 text-sm list-disc list-inside">
          <li>Each top-N finish earns that payout from every other team</li>
          <li>
            Formula:{' '}
            <code className="bg-surface-raised px-1 rounded">
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
        <div className="bg-surface-card rounded-lg p-6">
          <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
            Side Bets (Rounds {rules.sideBetRounds.join(', ')})
          </h3>
          <ul className="text-ink-secondary space-y-1 text-sm list-disc list-inside">
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
              <span className="text-semantic-success font-semibold">
                {formatUnsignedDollarsAndCents(rules.sideBetAmount)}
              </span>{' '}
              from every other team
            </li>
            <li>Side bet is not active if all entries are at $0</li>
          </ul>
        </div>
      ) : (
        <div className="bg-surface-card rounded-lg p-6">
          <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
            Side Bets
          </h3>
          {/* States the field, not whether bets are on: that also turns on the amount, and is the server's to say. */}
          <p className="text-ink-soft text-sm">No draft round carries a side bet this season.</p>
        </div>
      )}
    </>
  );
}

interface ScheduleListProps {
  tournaments: readonly Tournament[];
}

function ScheduleList({ tournaments }: ScheduleListProps): ReactNode {
  if (tournaments.length === 0) {
    return <p className="text-ink-soft text-sm">No tournaments scheduled for this season.</p>;
  }
  const schedule = [...tournaments].sort((a, b) => a.startDate.localeCompare(b.startDate));
  return (
    <ul className="text-ink-secondary text-sm space-y-0.5">
      {schedule.map((t) => (
        <li key={t.id} className="flex items-baseline gap-2">
          <span className="text-ink-soft tabular-nums w-16 shrink-0">
            {t.week ? `Wk ${t.week}` : ''}
          </span>
          <span className="flex-1">{t.name}</span>
          {t.payoutMultiplier !== 1 ? (
            <span className="text-semantic-warning font-semibold tabular-nums">
              {t.payoutMultiplier}x
            </span>
          ) : null}
        </li>
      ))}
    </ul>
  );
}

export default RulesPage;
