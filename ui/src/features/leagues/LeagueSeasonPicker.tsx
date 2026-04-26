import { useLeagueSeason } from '@/features/leagues/LeagueSeasonContext';
import { seasonLabel } from '@/shared/util/season';

function LeagueSeasonPicker() {
  const ctx = useLeagueSeason();

  if (!ctx.leagues || ctx.leagues.length === 0) return null;

  return (
    <div className="bg-gray-800/60 border-b border-gray-700">
      <div className="max-w-6xl mx-auto px-3 sm:px-4 py-2 flex flex-col sm:flex-row sm:flex-wrap sm:items-center gap-2 sm:gap-4 text-xs">
        <label className="flex items-center gap-2 w-full sm:w-auto">
          <span className="text-gray-500 font-medium uppercase tracking-wider w-16 shrink-0">
            League
          </span>
          <select
            value={ctx.leagueId ?? ''}
            onChange={(e) => ctx.setLeagueId(e.target.value)}
            className="bg-gray-700 border border-gray-600 rounded px-2 py-2 flex-1 sm:flex-initial"
          >
            {ctx.leagues.map((lg) => (
              <option key={lg.id} value={lg.id}>
                {lg.name}
              </option>
            ))}
          </select>
        </label>

        <label className="flex items-center gap-2 w-full sm:w-auto">
          <span className="text-gray-500 font-medium uppercase tracking-wider w-16 shrink-0">
            Season
          </span>
          <select
            value={ctx.seasonId ?? ''}
            onChange={(e) => ctx.setSeasonId(e.target.value)}
            disabled={!ctx.seasons || ctx.seasons.length === 0}
            className="bg-gray-700 border border-gray-600 rounded px-2 py-2 flex-1 sm:flex-initial disabled:opacity-50"
          >
            {(ctx.seasons ?? []).map((s) => (
              <option key={s.id} value={s.id}>
                {seasonLabel(s)}
              </option>
            ))}
          </select>
        </label>

        <label className="flex items-center gap-2 cursor-pointer w-full sm:w-auto sm:ml-auto py-1">
          <input
            type="checkbox"
            checked={ctx.live}
            onChange={(e) => ctx.setLive(e.target.checked)}
            className="accent-green-500 w-4 h-4"
          />
          <span className="text-gray-400">Live overlay</span>
        </label>
      </div>
    </div>
  );
}

export default LeagueSeasonPicker;
