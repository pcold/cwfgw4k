import type { CleanSeasonResult, CreateSeasonRequest, Season, SeasonRules } from '../types';
import { deleteNoContent, getJson, postJson } from './core';

export const seasonsApi = {
  seasons: (leagueId: string): Promise<Season[]> =>
    getJson<Season[]>(`/seasons?league_id=${encodeURIComponent(leagueId)}`),
  seasonRules: (seasonId: string): Promise<SeasonRules> => getJson<SeasonRules>(`/seasons/${seasonId}/rules`),
  defaultSeasonRules: (): Promise<SeasonRules> => getJson<SeasonRules>('/seasons/default-rules'),
  createSeason: (body: CreateSeasonRequest): Promise<Season> => postJson<Season>('/seasons', body),
  cleanSeasonResults: (seasonId: string): Promise<CleanSeasonResult> =>
    postJson<CleanSeasonResult>(`/seasons/${seasonId}/clean-results`),
  deleteSeason: (seasonId: string): Promise<void> => deleteNoContent(`/seasons/${encodeURIComponent(seasonId)}`),
};
