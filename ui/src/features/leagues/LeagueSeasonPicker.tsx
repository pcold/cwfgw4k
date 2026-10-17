import { useId, type ReactNode } from 'react';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { seasonLabel } from '@/shared/util/season';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';

const FIELD_CLASS = 'flex items-center gap-2 w-full sm:w-auto';
const LABEL_CLASS = 'text-xs text-ink-soft font-medium uppercase tracking-wider w-14 sm:w-auto shrink-0';
const SELECT_CLASS =
  `bg-surface-raised border border-control-border rounded px-2 py-2 text-base ${TAP_TARGET_CLASS} min-w-0 flex-1 sm:flex-initial`;

function LeagueSeasonPicker(): ReactNode {
  const ctx = useLeagueSeason();
  const leagueSelectId = useId();
  const seasonSelectId = useId();

  if (!ctx.leagues || ctx.leagues.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-x-4 gap-y-2 w-full sm:w-auto">
      <div className={FIELD_CLASS}>
        <label htmlFor={leagueSelectId} className={LABEL_CLASS}>
          League
        </label>
        <select
          id={leagueSelectId}
          value={ctx.leagueId ?? ''}
          onChange={(e) => ctx.setLeagueId(e.target.value)}
          className={SELECT_CLASS}
        >
          {ctx.leagues.map((lg) => (
            <option key={lg.id} value={lg.id}>
              {lg.name}
            </option>
          ))}
        </select>
      </div>

      <div className={FIELD_CLASS}>
        <label htmlFor={seasonSelectId} className={LABEL_CLASS}>
          Season
        </label>
        <select
          id={seasonSelectId}
          value={ctx.seasonId ?? ''}
          onChange={(e) => ctx.setSeasonId(e.target.value)}
          disabled={!ctx.seasons || ctx.seasons.length === 0}
          className={`${SELECT_CLASS} disabled:opacity-50`}
        >
          {(ctx.seasons ?? []).map((s) => (
            <option key={s.id} value={s.id}>
              {seasonLabel(s)}
            </option>
          ))}
        </select>
      </div>
    </div>
  );
}

export default LeagueSeasonPicker;
