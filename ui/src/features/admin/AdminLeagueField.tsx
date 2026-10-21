import type { ReactNode } from 'react';
import type { League } from '@/shared/api/types';
import FieldShell from '@/shared/components/FieldShell';
import { FIELD_CLASS } from '@/shared/components/formControls';

interface AdminLeagueFieldProps {
  id: string;
  leagues: readonly League[];
  leagueId: string;
  onChange: (id: string) => void;
}

/** Offers no unset option, so `leagueId` must name one of `leagues` once they load. */
export default function AdminLeagueField({ id, leagues, leagueId, onChange }: AdminLeagueFieldProps): ReactNode {
  return (
    <FieldShell id={id} label="League" error={undefined} width="sm:w-64">
      <select id={id} value={leagueId} onChange={(e) => onChange(e.target.value)} className={FIELD_CLASS}>
        {leagues.map((lg) => (
          <option key={lg.id} value={lg.id}>
            {lg.name}
          </option>
        ))}
      </select>
    </FieldShell>
  );
}
