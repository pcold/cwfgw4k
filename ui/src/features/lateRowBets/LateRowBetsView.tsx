import { useId, type ReactNode } from 'react';
import type { ReportSideBetRules, WeeklyReport } from '@/shared/api/types';
import ChipStrip, { type StripChip } from '@/shared/components/ChipStrip';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';
import { CARD_LIST_CLASS } from '@/shared/components/ListCard';
import MoneyCell, { MONEY_FIGURE_CLASS } from '@/shared/components/MoneyCell';
import WonLostNetCards from '@/shared/components/WonLostNetCards';
import {
  formatDollarsAndCents,
  formatUnsignedDollarsAndCents,
  moneyToneClass,
} from '@/shared/util/money';
import {
  lateRowBetRounds,
  lateRowBetTeamTotals,
  type LateRowBetEntry,
  type LateRowBetRound,
  type LateRowBetTeamTotal,
} from './lateRowBetsModel';

/** @see LateRowBetsView */
export type LateRowTab = { readonly kind: 'round'; readonly round: number } | { readonly kind: 'totals' };

const TOTALS_TAB: LateRowTab = { kind: 'totals' };

interface RoundTableProps {
  readonly round: number;
  readonly entries: readonly LateRowBetEntry[];
}

function RoundTable({ round, entries }: RoundTableProps) {
  return (
    <div className="bg-surface-card rounded-lg p-4">
      <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
        Round {round}
      </h3>
      <div className="overflow-x-auto -mx-2 sm:mx-0">
        <table className="w-full text-xs min-w-[480px]">
          <thead className={COLUMN_HEADER_CLASS}>
            <tr>
              <th className="text-left px-2 py-1">Team</th>
              <th className="text-left px-2 py-1">Golfer</th>
              <th className="text-right px-2 py-1">Cumulative $</th>
              <th className="text-right px-2 py-1">Late Row Bet</th>
            </tr>
          </thead>
          <tbody>
            {entries.map((entry) => (
              <tr key={entry.teamId} className="border-t border-hairline">
                <td className="px-2 py-1.5 uppercase">{entry.teamName}</td>
                <td className="px-2 py-1.5 font-mono">{entry.golferName}</td>
                <td className="px-2 py-1.5 text-right font-mono">
                  {formatDollarsAndCents(entry.cumulativeEarnings)}
                </td>
                <td className="px-2 py-1.5 text-right font-mono font-bold">
                  {formatDollarsAndCents(entry.payout)}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}

const ROUND_LIST = new Intl.ListFormat('en', { style: 'long', type: 'conjunction' });

interface RulesNoteProps {
  readonly rules: ReportSideBetRules;
}

// Worded to stay true of a tied round, where several teams collect and split
// what the teams behind them paid.
function RulesNote({ rules }: RulesNoteProps) {
  const amount = formatUnsignedDollarsAndCents(rules.amountPerTeam);
  const rounds = ROUND_LIST.format(rules.rounds.map(String));
  const where = rules.rounds.length === 1 ? `draft round ${rounds}` : `each of draft rounds ${rounds}`;
  return (
    <p className="text-ink-muted text-sm">
      {amount} per team in {where}. Every team behind a round&apos;s highest cumulative earner
      pays {amount}; teams tied for the highest split what was paid.
    </p>
  );
}

const LABEL_CLASS = 'font-display font-bold text-sm uppercase tracking-wider text-ink-label';

const CARD_NAME_CLASS = 'font-display font-bold text-lg text-ink truncate';

/** A selected tab resolved against the report, so a round tab holds that round's entries. */
type ShownTab = { readonly kind: 'round'; readonly detail: LateRowBetRound } | { readonly kind: 'totals' };

const SHOWN_TOTALS: ShownTab = { kind: 'totals' };

interface TabChip extends StripChip {
  readonly tab: LateRowTab;
}

function tabId(tab: LateRowTab): string {
  switch (tab.kind) {
    case 'round':
      return `round-${tab.round}`;
    case 'totals':
      return 'totals';
    default:
      return tab satisfies never;
  }
}

function tabChips(rounds: readonly LateRowBetRound[]): readonly TabChip[] {
  const roundChips = rounds.map(({ round }): TabChip => {
    const tab: LateRowTab = { kind: 'round', round };
    return { id: tabId(tab), label: `Rd ${round}`, tab };
  });
  return [...roundChips, { id: tabId(TOTALS_TAB), label: 'Totals', tab: TOTALS_TAB }];
}

interface LeaderCardProps {
  readonly round: number;
  readonly entry: LateRowBetEntry;
}

function LeaderCard({ round, entry }: LeaderCardProps): ReactNode {
  return (
    <li className="flex min-h-13.5 rounded border-l-4 border-gold bg-late-row-leader">
      <div className="flex-1 min-w-0 px-3 py-2 flex flex-col justify-center">
        <div className={LABEL_CLASS}>Leading round {round}</div>
        <div className={CARD_NAME_CLASS}>
          <span className="uppercase">{entry.teamName}</span> · {entry.golferName}
        </div>
        <div className="text-sm text-ink-muted">
          {formatDollarsAndCents(entry.cumulativeEarnings)} season earnings
        </div>
      </div>
      <MoneyCell display={formatDollarsAndCents(entry.payout)} shape="card-end" />
    </li>
  );
}

interface EntryListProps {
  readonly heading: string;
  readonly entries: readonly LateRowBetEntry[];
}

function EntryList({ heading, entries }: EntryListProps): ReactNode {
  const headingId = useId();
  return (
    <div className="space-y-1">
      <h3 id={headingId} className={LABEL_CLASS}>
        {heading}
      </h3>
      <ol aria-labelledby={headingId} className="rounded bg-surface-card divide-y divide-hairline-soft">
        {entries.map((entry) => (
          <li key={entry.teamId} className="flex items-center gap-3 min-h-13.5 px-3 py-2">
            <div className="flex-1 min-w-0">
              <div className={`${CARD_NAME_CLASS} uppercase`}>{entry.teamName}</div>
              <div className="text-sm text-ink-muted truncate">{entry.golferName}</div>
            </div>
            <div className={`shrink-0 text-ink ${MONEY_FIGURE_CLASS}`}>
              {formatDollarsAndCents(entry.cumulativeEarnings)}
            </div>
          </li>
        ))}
      </ol>
    </div>
  );
}

interface RoundCardsProps {
  readonly round: LateRowBetRound;
  readonly rules: ReportSideBetRules | null;
}

// Leadership is the server's `outcome`, never the top payout or rank: a round every team ties is all `won` at $0.
function RoundCards({ round, rules }: RoundCardsProps): ReactNode {
  const leaders = round.entries.filter((entry) => entry.outcome === 'won');
  const chasers = round.entries.filter((entry) => entry.outcome !== 'won');
  if (leaders.length === 0) {
    return (
      <>
        <p className="text-sm text-ink-muted">No team has earned in round {round.round} yet.</p>
        <EntryList heading={`Round ${round.round}`} entries={round.entries} />
      </>
    );
  }

  const eachPays = rules === null ? '' : ` · each pays ${formatUnsignedDollarsAndCents(rules.amountPerTeam)}`;
  return (
    <>
      <ul aria-label="Leaders" className={CARD_LIST_CLASS}>
        {leaders.map((entry) => (
          <LeaderCard key={entry.teamId} round={round.round} entry={entry} />
        ))}
      </ul>
      {chasers.length > 0 ? <EntryList heading={`Chasing${eachPays}`} entries={chasers} /> : null}
    </>
  );
}

interface TotalsCardsProps {
  readonly totals: readonly LateRowBetTeamTotal[];
}

function TotalsCards({ totals }: TotalsCardsProps): ReactNode {
  return (
    <ol aria-label="Totals" className={CARD_LIST_CLASS}>
      {totals.map((team) => (
        <li key={team.teamId} className="flex min-h-13.5 rounded bg-surface-card">
          <div className={`flex-1 min-w-0 px-3 py-2 self-center uppercase ${CARD_NAME_CLASS}`}>{team.teamName}</div>
          <MoneyCell display={formatDollarsAndCents(team.sideBets)} shape="card-end" />
        </li>
      ))}
    </ol>
  );
}

interface PhoneBetsProps {
  readonly rounds: readonly LateRowBetRound[];
  readonly shown: ShownTab;
  readonly totals: readonly LateRowBetTeamTotal[];
  readonly rules: ReportSideBetRules | null;
  readonly onSelectTab: (tab: LateRowTab) => void;
}

function PhoneBets({ rounds, shown, totals, rules, onSelectTab }: PhoneBetsProps): ReactNode {
  const chips = tabChips(rounds);
  const selectedId = tabId(shown.kind === 'round' ? { kind: 'round', round: shown.detail.round } : TOTALS_TAB);
  function selectChip(id: string): void {
    const chip = chips.find((candidate) => candidate.id === id);
    if (chip) onSelectTab(chip.tab);
  }

  return (
    <div className="sm:hidden space-y-3">
      <ChipStrip groupLabel="Late row round" chips={chips} selectedId={selectedId} onSelect={selectChip} />
      {shown.kind === 'round' ? <RoundCards round={shown.detail} rules={rules} /> : <TotalsCards totals={totals} />}
    </div>
  );
}

interface Props {
  readonly report: WeeklyReport;
  /** Null, or a round the report no longer has, shows the first round. */
  readonly selectedTab: LateRowTab | null;
  readonly onSelectTab: (tab: LateRowTab) => void;
}

function shownTabFor(
  selectedTab: LateRowTab | null,
  rounds: readonly LateRowBetRound[],
  firstRound: LateRowBetRound,
): ShownTab {
  if (selectedTab?.kind === 'totals') return SHOWN_TOTALS;
  const detail = rounds.find((rd) => rd.round === selectedTab?.round) ?? firstRound;
  return { kind: 'round', detail };
}

function LateRowBetsView({ report, selectedTab, onSelectTab }: Props): ReactNode {
  const rounds = lateRowBetRounds(report);
  const totals = lateRowBetTeamTotals(report);
  const { sideBetTotals } = report;
  const won = formatDollarsAndCents(sideBetTotals.won);
  const lost = formatDollarsAndCents(sideBetTotals.lost);
  const net = formatDollarsAndCents(sideBetTotals.net);

  const firstRound = rounds[0];
  if (firstRound === undefined) {
    return <p className="text-ink-soft text-sm">This season has no late row bets.</p>;
  }

  return (
    <section className="space-y-4">
      {report.sideBetRules ? <RulesNote rules={report.sideBetRules} /> : null}

      <PhoneBets
        rounds={rounds}
        shown={shownTabFor(selectedTab, rounds, firstRound)}
        totals={totals}
        rules={report.sideBetRules}
        onSelectTab={onSelectTab}
      />

      <div className="hidden sm:block space-y-4">
        <div className="space-y-6">
          {rounds.map((rd) => (
            <RoundTable key={rd.round} round={rd.round} entries={rd.entries} />
          ))}
        </div>

        {totals.length > 0 ? (
          <div className="bg-surface-card rounded-lg p-4">
            <h3 className="text-sm font-semibold text-ink-label uppercase tracking-wider mb-3">
              Total Late Row Bets (All Rounds)
            </h3>
            <div className="overflow-x-auto">
              <table className="w-full text-xs">
                <thead className={COLUMN_HEADER_CLASS}>
                  <tr>
                    <th className="text-left px-2 py-1">Team</th>
                    <th className="text-right px-2 py-1">Total</th>
                  </tr>
                </thead>
                <tbody>
                  {totals.map((team) => {
                    const total = formatDollarsAndCents(team.sideBets);
                    return (
                      <tr key={team.teamId} className="border-t border-hairline">
                        <td className="px-2 py-1.5 uppercase">{team.teamName}</td>
                        <td className={`px-2 py-1.5 text-right font-mono font-bold ${moneyToneClass(total)}`}>
                          {total}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            </div>
          </div>
        ) : null}
      </div>

      {report.teams.length > 0 ? (
        <WonLostNetCards won={won} lost={lost} net={net} />
      ) : null}
    </section>
  );
}

export default LateRowBetsView;
