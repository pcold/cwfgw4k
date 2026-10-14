import type { RosterTeam } from '../types';
import { getJson } from './core';

export const teamsApi = {
  rosters: (seasonId: string): Promise<RosterTeam[]> => getJson<RosterTeam[]>(`/seasons/${seasonId}/rosters`),
};
