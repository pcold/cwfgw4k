import { useState, type ReactElement, type ReactNode } from 'react';
import type { HoleScore, RoundScorecard } from '@/shared/api/types';
import ChipStrip from '@/shared/components/ChipStrip';
import { COLUMN_HEADER_CLASS } from '@/shared/components/columnHeader';

type ScoreMark = 'eagle' | 'birdie' | 'bogey' | 'doubleBogey';

// Round under par and square over it, so colour is never the only cue between birdie green and bogey red.
const MARKS: Record<ScoreMark, { readonly label: string; readonly shapeClass: string }> = {
  eagle: { label: 'Eagle or better', shapeClass: 'rounded-full bg-score-eagle' },
  birdie: { label: 'Birdie', shapeClass: 'rounded-full bg-score-birdie' },
  bogey: { label: 'Bogey', shapeClass: 'rounded-sm bg-score-bogey' },
  doubleBogey: { label: 'Double bogey or worse', shapeClass: 'rounded-sm bg-score-double-bogey' },
};

const MARK_ORDER: readonly ScoreMark[] = ['eagle', 'birdie', 'bogey', 'doubleBogey'];

function markOf(toPar: number | null): ScoreMark | null {
  if (toPar === null || toPar === 0) return null;
  if (toPar <= -2) return 'eagle';
  if (toPar === -1) return 'birdie';
  if (toPar === 1) return 'bogey';
  return 'doubleBogey';
}

const LABEL_CELL_CLASS = 'w-11 pr-1 text-left font-normal';

const HOLE_CELL_CLASS = 'h-7 w-5.5 p-0';

const SUM_CELL_CLASS = 'h-7 w-7 p-0 font-semibold';

function Strokes({ hole }: { hole: HoleScore }): ReactNode {
  if (hole.strokes === null) return null;
  const mark = markOf(hole.toPar);
  if (mark === null) return hole.strokes;
  return (
    <span className={`inline-flex size-5 items-center justify-center ${MARKS[mark].shapeClass}`}>
      {hole.strokes}
      <span className="sr-only">, {MARKS[mark].label}</span>
    </span>
  );
}

interface NineTableProps {
  caption: string;
  holes: readonly HoleScore[];
  /** The nine's own sum first, then Tot on the back nine. */
  sums: readonly { readonly heading: string; readonly strokes: number | null }[];
}

function NineTable({ caption, holes, sums }: NineTableProps): ReactElement {
  return (
    <table className="text-xs tabular-nums text-center text-ink">
      <caption className="sr-only">{caption}</caption>
      <thead className={COLUMN_HEADER_CLASS}>
        <tr>
          <th scope="col" className={`${LABEL_CELL_CLASS} h-7`}>
            Hole
          </th>
          {holes.map((hole) => (
            <th key={hole.hole} scope="col" className={HOLE_CELL_CLASS}>
              {hole.hole}
            </th>
          ))}
          {sums.map((sum) => (
            <th key={sum.heading} scope="col" className={SUM_CELL_CLASS}>
              {sum.heading}
            </th>
          ))}
        </tr>
      </thead>
      <tbody>
        <tr className="text-ink-muted">
          <th scope="row" className={`${LABEL_CELL_CLASS} ${COLUMN_HEADER_CLASS}`}>
            Par
          </th>
          {holes.map((hole) => (
            <td key={hole.hole} className={HOLE_CELL_CLASS}>
              {hole.par}
            </td>
          ))}
          {sums.map((sum) => (
            <td key={sum.heading} className={SUM_CELL_CLASS} />
          ))}
        </tr>
        <tr>
          <th scope="row" className={`${LABEL_CELL_CLASS} ${COLUMN_HEADER_CLASS}`}>
            Score
          </th>
          {holes.map((hole) => (
            <td key={hole.hole} className={HOLE_CELL_CLASS}>
              <Strokes hole={hole} />
            </td>
          ))}
          {sums.map((sum) => (
            <td key={sum.heading} className={SUM_CELL_CLASS}>
              {sum.strokes}
            </td>
          ))}
        </tr>
      </tbody>
    </table>
  );
}

function MarkLegend(): ReactElement {
  return (
    <ul
      aria-label="Score marks"
      className="flex flex-wrap gap-x-3 gap-y-1 font-display text-xs uppercase tracking-wider text-ink-muted"
    >
      {MARK_ORDER.map((mark) => (
        <li key={mark} className="flex items-center gap-1.5">
          <span aria-hidden="true" className={`size-3 ${MARKS[mark].shapeClass}`} />
          {MARKS[mark].label}
        </li>
      ))}
    </ul>
  );
}

interface LeaderboardScorecardProps {
  /** In round order. */
  scorecards: readonly RoundScorecard[];
  id: string;
}

// The par row's sums stay blank: none come over the wire, and the UI doesn't add them up.
export default function LeaderboardScorecard({ scorecards, id }: LeaderboardScorecardProps): ReactElement {
  const [pickedRound, setPickedRound] = useState<number | null>(null);
  const shown = scorecards.find((scorecard) => scorecard.round === pickedRound) ?? scorecards.at(-1);

  return (
    <div id={id} className="@container space-y-2 px-3 pb-3">
      {scorecards.length > 1 && shown ? (
        <ChipStrip
          groupLabel="Round"
          chips={scorecards.map((scorecard) => ({ id: String(scorecard.round), label: `R${scorecard.round}` }))}
          selectedId={String(shown.round)}
          onSelect={(round) => setPickedRound(Number(round))}
        />
      ) : null}
      {shown ? (
        <div className="overflow-x-auto">
          {/* The two nines side by side are 36rem wide, so a narrower card stacks them rather than scroll. */}
          <div className="flex flex-col gap-2 @min-[36rem]:flex-row">
            <NineTable
              caption={`Round ${shown.round}, front nine`}
              holes={shown.holes.slice(0, 9)}
              sums={[{ heading: 'Out', strokes: shown.frontNine }]}
            />
            <NineTable
              caption={`Round ${shown.round}, back nine`}
              holes={shown.holes.slice(9)}
              sums={[
                { heading: 'In', strokes: shown.backNine },
                { heading: 'Tot', strokes: shown.total },
              ]}
            />
          </div>
        </div>
      ) : null}
      <MarkLegend />
    </div>
  );
}
