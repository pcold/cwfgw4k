import type { ReactNode } from 'react';

/** @see statusChipVariant */
export type StatusChipVariant = 'live' | 'final' | 'upcoming';

/** @see statusChipVariant */
export interface StatusChipSource {
  readonly live: boolean | null;
  readonly tournament?: { readonly status: string | null };
}

/**
 * `live` is true only when ESPN answered the overlay, so a page asking for it never shows LIVE on stored
 * scores. A response without a tournament status (rankings, a season report) is never final or upcoming.
 */
export function statusChipVariant(source: StatusChipSource | undefined): StatusChipVariant | null {
  if (source === undefined) return null;
  if (source.live === true) return 'live';
  switch (source.tournament?.status) {
    case 'completed':
      return 'final';
    case 'upcoming':
      return 'upcoming';
    default:
      return null;
  }
}

const CHIP_CLASS =
  'inline-flex items-center px-2.5 py-1 rounded border font-display font-bold text-xs uppercase tracking-wider';

const VARIANT_CLASS: Readonly<Record<StatusChipVariant, string>> = {
  live: 'bg-primary border-primary text-on-primary',
  final: 'bg-chrome border-chrome text-on-chrome',
  upcoming: 'border-ink-strong text-ink-strong',
};

const VARIANT_LABEL: Readonly<Record<StatusChipVariant, string>> = {
  live: 'Live',
  final: 'Final',
  upcoming: 'Upcoming',
};

interface StatusChipProps {
  readonly variant: StatusChipVariant | null;
}

/** @see statusChipVariant */
function StatusChip({ variant }: StatusChipProps): ReactNode {
  if (variant === null) return null;
  return <span className={`${CHIP_CLASS} ${VARIANT_CLASS[variant]}`}>{VARIANT_LABEL[variant]}</span>;
}

export default StatusChip;
