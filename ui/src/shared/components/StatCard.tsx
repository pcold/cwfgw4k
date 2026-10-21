import type { ReactNode } from 'react';
import { moneyToneClass } from '@/shared/util/money';

interface StatCardProps {
  readonly label: string;
  readonly amount: string;
}

/**
 * Takes the formatted text rather than a number and a format, so the whole-dollar or cents choice stays with the
 * caller and the tone is read at the precision it prints.
 */
function StatCard({ label, amount }: StatCardProps): ReactNode {
  return (
    <div className="bg-surface-card rounded px-3 py-2 flex items-center gap-2">
      <span className="text-ink-soft">{label}</span>
      <span className={`font-semibold tabular-nums ${moneyToneClass(amount)}`}>{amount}</span>
    </div>
  );
}

export default StatCard;
