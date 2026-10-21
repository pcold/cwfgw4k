import type { CreateLeagueRequest, League } from '../types';
import { getJson, postJson } from './core';

export const leaguesApi = {
  leagues: (): Promise<League[]> => getJson<League[]>('/leagues'),
  createLeague: (name: string): Promise<League> => postJson<League>('/leagues', { name } satisfies CreateLeagueRequest),
};
