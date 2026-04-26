import type { Season } from '@/shared/api/types';

export function seasonLabel(s: Season): string {
  return `${s.seasonYear} ${s.name}`;
}
