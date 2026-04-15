import type { Season } from '@/api/types';

export function seasonLabel(s: Season): string {
  return `${s.seasonYear} ${s.name}`;
}
