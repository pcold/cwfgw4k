import type {
  TournamentCompetitorListing,
  TournamentPlayerOverride,
  UpsertTournamentPlayerOverrideRequest,
} from '../types';
import { deleteNoContent, getJson, postJson } from './core';

export const tournamentLinksApi = {
  tournamentCompetitors: (tournamentId: string): Promise<TournamentCompetitorListing> =>
    getJson<TournamentCompetitorListing>(`/admin/tournaments/${encodeURIComponent(tournamentId)}/competitors`),
  upsertTournamentPlayerOverride: (
    tournamentId: string,
    body: UpsertTournamentPlayerOverrideRequest,
  ): Promise<TournamentPlayerOverride> =>
    postJson<TournamentPlayerOverride>(
      `/admin/tournaments/${encodeURIComponent(tournamentId)}/player-overrides`,
      body,
    ),
  // 404 is treated as "already cleared" by the panel — the caller maps it.
  deleteTournamentPlayerOverride: (tournamentId: string, espnCompetitorId: string): Promise<void> =>
    deleteNoContent(
      `/admin/tournaments/${encodeURIComponent(tournamentId)}/player-overrides/${encodeURIComponent(espnCompetitorId)}`,
    ),
};
