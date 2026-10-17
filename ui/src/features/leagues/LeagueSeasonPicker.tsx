import type { ReactNode } from 'react';
import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { seasonLabel } from '@/shared/util/season';
import { TAP_TARGET_CLASS } from '@/shared/components/formFields';

const SELECT_CLASS =
  `bg-surface-raised border border-control-border rounded px-2 py-2 text-base ${TAP_TARGET_CLASS} flex-1 sm:flex-initial`;

function LeagueSeasonPicker(): ReactNode {
  const ctx = useLeagueSeason();

  if (!ctx.leagues || ctx.leagues.length === 0) return null;

  return (
    <div className="bg-surface-card/60 border-b border-hairline">
      <div
        className="max-w-screen-2xl mx-auto px-3 sm:px-4 py-2 flex flex-col sm:flex-row sm:flex-wrap sm:items-center gap-2 sm:gap-4 text-xs"
      >
        <label className="flex items-center gap-2 w-full sm:w-auto">
          <span className="text-ink-soft font-medium uppercase tracking-wider w-16 shrink-0">
            League
          </span>
          <select
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
        </label>

        <label className="flex items-center gap-2 w-full sm:w-auto">
          <span className="text-ink-soft font-medium uppercase tracking-wider w-16 shrink-0">
            Season
          </span>
          <select
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
        </label>
      </div>
    </div>
  );
}

export default LeagueSeasonPicker;
