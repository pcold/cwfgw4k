import type { ReactNode } from 'react';

/** @see Chip */
export type ChipVariant = 'neutral' | 'outlined';

const CHIP_CLASS = 'inline-flex items-center whitespace-nowrap rounded border px-1.5 py-0.5 text-xs uppercase tracking-wider';

// The neutral border is transparent rather than absent, so a filled chip and an outlined one beside it share a size.
const VARIANT_CLASS: Readonly<Record<ChipVariant, string>> = {
  neutral: 'bg-surface-raised border-transparent text-ink-body',
  outlined: 'border-hairline-strong text-ink-muted',
};

interface ChipProps {
  readonly variant: ChipVariant;
  readonly children: ReactNode;
}

/**
 * Told apart by its label, never by a tone: an outline marks what is absent (an undrafted golfer), and a page's
 * status is `StatusChip`, not one of these. No `className`, so a caller can't add a fill.
 */
function Chip({ variant, children }: ChipProps): ReactNode {
  return <span className={`${CHIP_CLASS} ${VARIANT_CLASS[variant]}`}>{children}</span>;
}

export default Chip;
