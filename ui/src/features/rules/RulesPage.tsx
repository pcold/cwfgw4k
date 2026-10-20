import type { ReactNode } from 'react';
import { skipToken, useQuery } from '@tanstack/react-query';
import { api } from '@/shared/api/client';
import { queryKeys } from '@/shared/api/queryKeys';
import type { SeasonRules, Tournament } from '@/shared/api/types';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { QueryState } from '@/shared/components/QueryState';
import PageTitle from '@/shared/components/PageTitle';
import LeagueSeasonPicker from '@/features/leagues/LeagueSeasonPicker';
import { formatUnsignedDollarsAndCents } from '@/shared/util/money';
import { ordinal, payoutsByPlace } from './rulesModel';

const BULLET_LIST_CLASS = 'text-ink-secondary space-y-1 text-sm list-disc list-inside';

type TileTone = 'first-place' | 'field';

interface TileInks {
  readonly fill: string;
  readonly ordinal: string;
  readonly amount: string;
}

// Gold marks first place, as on the standings' first position box.
const TILE_INKS: Readonly<Record<TileTone, TileInks>> = {
  'first-place': { fill: 'bg-gold', ordinal: 'text-on-gold', amount: 'text-on-gold' },
  field: { fill: 'bg-surface-raised', ordinal: 'text-ink-label', amount: 'text-ink' },
};

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

    // Two slots, not one around the stack: the static cards between them hold their place while the rules load or fail.
    return (
      <>
        <QueryState query={rulesQuery} label="season rules">
          {(rules) => <PayoutsCard payouts={rules.payouts} />}
        </QueryState>
        <DraftCard />
        <ZeroSumCard />
        <QueryState query={rulesQuery} label="season rules">
          {(rules) => (
            <>
              <TieHandlingCard tieFloor={rules.tieFloor} />
              <SideBetsCard rules={rules} />
            </>
          )}
        </QueryState>
        <RulesCard title="Season Schedule">
          <p className="text-ink-secondary text-sm mb-3">
            Tournaments on the season calendar. A highlighted multiplier means payouts are
            boosted for that week (e.g. 2x for majors).
          </p>
          <QueryState query={tournamentsQuery} label="schedule">
            {(tournaments) => <ScheduleList tournaments={tournaments} />}
          </QueryState>
        </RulesCard>
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

interface RulesCardProps {
  readonly title: string;
  readonly caption?: string;
  readonly children: ReactNode;
}

/**
 * The leaderboard's band in the condensed display type: at body width, "Top 10 drafted finishers" truncates
 * beside its title below 375px.
 */
function RulesCard({ title, caption, children }: RulesCardProps): ReactNode {
  return (
    <div className="bg-surface-card rounded-lg">
      <div className="flex items-center justify-between gap-3 px-3 py-2.5 rounded-t-lg bg-header-band text-on-fill font-display">
        <h3 className="text-sm font-semibold uppercase tracking-wider">{title}</h3>
        {caption ? <span className="text-sm uppercase tracking-wider truncate">{caption}</span> : null}
      </div>
      <div className="p-6">{children}</div>
    </div>
  );
}

interface PayoutsCardProps {
  readonly payouts: readonly number[];
}

function PayoutsCard({ payouts }: PayoutsCardProps): ReactNode {
  return (
    <RulesCard title="Weekly Payouts" caption={`Top ${payouts.length} drafted finishers`}>
      <ol aria-label="Payouts by place" className="grid grid-cols-5 gap-2 max-w-md">
        {payoutsByPlace(payouts).map(({ place, amount }) => (
          <PayoutTile key={place} place={place} amount={amount} />
        ))}
      </ol>
    </RulesCard>
  );
}

interface PayoutTileProps {
  readonly place: number;
  readonly amount: number;
}

function PayoutTile({ place, amount }: PayoutTileProps): ReactNode {
  const inks = TILE_INKS[place === 1 ? 'first-place' : 'field'];
  return (
    <li className={`rounded py-2 text-center ${inks.fill}`}>
      <span className={`block font-display font-bold text-xs uppercase tracking-wider ${inks.ordinal}`}>
        {ordinal(place)}
      </span>
      <span className={`block font-display font-bold text-sm sm:text-lg tabular-nums ${inks.amount}`}>
        {formatUnsignedDollarsAndCents(amount)}
      </span>
    </li>
  );
}

function DraftCard(): ReactNode {
  return (
    <RulesCard title="The Draft">
      <ul className={BULLET_LIST_CLASS}>
        <li>Snake draft, each team drafts PGA Tour players</li>
        <li>
          Players can be split between teams with ownership percentages (earnings divided
          proportionally)
        </li>
      </ul>
    </RulesCard>
  );
}

function ZeroSumCard(): ReactNode {
  return (
    <RulesCard title="Zero-Sum Scoring">
      <ul className={BULLET_LIST_CLASS}>
        <li>Each top-N finish earns that payout from every other team</li>
      </ul>
      <p className="my-3 bg-surface-raised rounded px-3 py-2 text-sm font-semibold text-ink">
        Weekly +/− = (your earnings × teams) − total pot
      </p>
      <ul className={BULLET_LIST_CLASS}>
        <li>The league is zero-sum — every dollar won is a dollar lost by someone else</li>
        <li>
          Undrafted players who finish in the payout zone are tracked but don't affect team
          payouts
        </li>
      </ul>
    </RulesCard>
  );
}

interface TieHandlingCardProps {
  readonly tieFloor: number;
}

function TieHandlingCard({ tieFloor }: TieHandlingCardProps): ReactNode {
  return (
    <RulesCard title="Tie Handling">
      <ul className={BULLET_LIST_CLASS}>
        <li>Tied positions average the payouts across all tied slots</li>
        <li>
          Minimum payout of{' '}
          <span className="text-semantic-success font-semibold">
            {formatUnsignedDollarsAndCents(tieFloor)}
          </span>{' '}
          per player for any tie that overlaps the payout zone
        </li>
      </ul>
    </RulesCard>
  );
}

interface SideBetsCardProps {
  readonly rules: SeasonRules;
}

function SideBetsCard({ rules }: SideBetsCardProps): ReactNode {
  if (rules.sideBetRounds.length === 0) {
    return (
      <RulesCard title="Side Bets">
        {/* States the field, not whether bets are on: that also turns on the amount, and is the server's to say. */}
        <p className="text-ink-soft text-sm">No draft round carries a side bet this season.</p>
      </RulesCard>
    );
  }
  return (
    <RulesCard title="Side Bets" caption={`Rounds ${rules.sideBetRounds.join(', ')}`}>
      <ul className={BULLET_LIST_CLASS}>
        <li>
          {rules.sideBetRounds.length} separate season-long races — one for each draft round
        </li>
        <li>
          Winner = the team whose round-N pick has the highest cumulative earnings across all
          tournaments
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
    </RulesCard>
  );
}

interface ScheduleListProps {
  readonly tournaments: readonly Tournament[];
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
