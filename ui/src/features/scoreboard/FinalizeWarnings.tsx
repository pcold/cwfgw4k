import type { ReactNode } from 'react';
import type {
  ContestedCompetitor,
  EspnImportWarnings,
  Golfer,
  RosteredSurnameTwin,
  UnmatchedCompetitor,
} from '@/shared/api/types';

// DESIGN.md's needing-attention banner. It has no danger pair, so the surname twin is set apart by going first, by
// role="alert", and by its bold heading rather than by a colour no token documents.
const ATTENTION_BANNER_CLASS =
  'px-3 py-2 border rounded bg-semantic-warning-tint/30 border-semantic-warning-soft text-semantic-warning-ink';

interface FinalizeWarningsProps {
  readonly warnings: readonly EspnImportWarnings[];
}

function golferName(golfer: Golfer): string {
  return `${golfer.firstName} ${golfer.lastName}`;
}

function espnLabel(name: string, espnCompetitorId: string): string {
  return name === ''
    ? `an unnamed competitor (ESPN ${espnCompetitorId})`
    : `${name} (ESPN ${espnCompetitorId})`;
}

function TwinItem({ twin }: { readonly twin: RosteredSurnameTwin }): ReactNode {
  return (
    <li>
      {golferName(twin.created)} was created as a new golfer and shares a surname with rostered{' '}
      {twin.rostered.map(golferName).join(' / ')}. If they are the same player, the results went to the new
      golfer and the roster pick scored nothing.
    </li>
  );
}

function ContestedItem({ competitor }: { readonly competitor: ContestedCompetitor }): ReactNode {
  return (
    <li>
      {espnLabel(competitor.name, competitor.espnCompetitorId)} could be{' '}
      {competitor.candidates.map(golferName).join(' or ')}.
    </li>
  );
}

function UnmatchedItem({ competitor }: { readonly competitor: UnmatchedCompetitor }): ReactNode {
  return <li>{espnLabel(competitor.name, competitor.espnCompetitorId)}</li>;
}

function TournamentWarnings({ warnings }: { readonly warnings: EspnImportWarnings }): ReactNode {
  const { rosteredSurnameTwins, contestedCompetitors, unmatchedCompetitors, createdGolfers } = warnings;
  return (
    <div className="space-y-2">
      <h3 className="text-sm font-semibold">ESPN import warnings: {warnings.tournamentName}</h3>
      {rosteredSurnameTwins.length > 0 ? (
        <div role="alert" className={ATTENTION_BANNER_CLASS}>
          <p className="font-bold">Possible duplicate of a rostered golfer</p>
          <ul className="list-disc pl-5">
            {rosteredSurnameTwins.map((twin) => (
              <TwinItem key={twin.created.id} twin={twin} />
            ))}
          </ul>
        </div>
      ) : null}
      {contestedCompetitors.length > 0 ? (
        <div className={ATTENTION_BANNER_CLASS}>
          <p className="font-semibold">No result recorded: the name fits more than one golfer</p>
          <ul className="list-disc pl-5">
            {contestedCompetitors.map((competitor) => (
              <ContestedItem key={competitor.espnCompetitorId} competitor={competitor} />
            ))}
          </ul>
        </div>
      ) : null}
      {unmatchedCompetitors.length > 0 ? (
        <div className={ATTENTION_BANNER_CLASS}>
          <p className="font-semibold">No result recorded: matched to no golfer</p>
          <ul className="list-disc pl-5">
            {unmatchedCompetitors.map((competitor) => (
              <UnmatchedItem key={competitor.espnCompetitorId} competitor={competitor} />
            ))}
          </ul>
        </div>
      ) : null}
      {createdGolfers.length > 0 ? (
        <p className="text-ink-muted">New golfers created: {createdGolfers.map(golferName).join(', ')}</p>
      ) : null}
    </div>
  );
}

function FinalizeWarnings({ warnings }: FinalizeWarningsProps): ReactNode {
  if (warnings.length === 0) {
    return (
      <p role="status" className="text-xs text-ink-muted">
        The ESPN import raised no warnings.
      </p>
    );
  }
  return (
    <section aria-label="ESPN import warnings" className="space-y-3 text-xs">
      {warnings.map((tournamentWarnings) => (
        <TournamentWarnings key={tournamentWarnings.tournamentId} warnings={tournamentWarnings} />
      ))}
    </section>
  );
}

export default FinalizeWarnings;
