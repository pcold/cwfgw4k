import type { ReactElement } from 'react';

/** @see LateRowTag */
export type LateRowTagVariant = 'late-row' | 'late-row-leader';

const TAG_CLASS =
  'inline-flex items-center whitespace-nowrap rounded border px-1.5 py-0.5 font-display font-bold text-xs uppercase tracking-wider';

// The outline is for a white card; the leader's card is pale gold, and the gold tag replaces it there.
const VARIANT_CLASS: Readonly<Record<LateRowTagVariant, string>> = {
  'late-row': 'border-hairline-strong text-ink-muted',
  'late-row-leader': 'bg-gold border-gold text-on-gold',
};

const VARIANT_LABEL: Readonly<Record<LateRowTagVariant, string>> = {
  'late-row': 'Late row',
  'late-row-leader': 'Leads late row',
};

interface LateRowTagProps {
  readonly variant: LateRowTagVariant;
}

/** No `className`, so a caller can't add a second fill. */
export default function LateRowTag({ variant }: LateRowTagProps): ReactElement {
  return <span className={`${TAG_CLASS} ${VARIANT_CLASS[variant]}`}>{VARIANT_LABEL[variant]}</span>;
}
