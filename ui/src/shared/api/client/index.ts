import { adminApi } from './admin';
import { authApi } from './auth';
import { golfersApi } from './golfers';
import { leaguesApi } from './leagues';
import { reportsApi } from './reports';
import { seasonsApi } from './seasons';
import { teamsApi } from './teams';
import { tournamentLinksApi } from './tournamentLinks';
import { tournamentsApi } from './tournaments';

export { ApiError, camelizeKeys, snakeifyKeys } from './core';

/**
 * Every HTTP call goes through this object (ui/CLAUDE.md → Data Fetching); a call site never imports
 * a slice or `./core`. A key two slices both declare resolves silently by spread order.
 */
export const api = {
  ...adminApi,
  ...authApi,
  ...golfersApi,
  ...leaguesApi,
  ...reportsApi,
  ...seasonsApi,
  ...teamsApi,
  ...tournamentLinksApi,
  ...tournamentsApi,
};
