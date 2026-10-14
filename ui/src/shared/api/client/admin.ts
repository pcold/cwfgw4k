import type {
  ConfirmedTournamentEntry,
  RosterConfirmResult,
  RosterConfirmTeamInput,
  RosterPreview,
  SeasonImportResult,
  SeasonSchedulePreviewResult,
} from '../types';
import { postJson, postText } from './core';

export const adminApi = {
  previewSeasonSchedule: (input: {
    seasonId: string;
    startDate: string;
    endDate: string;
  }): Promise<SeasonSchedulePreviewResult> =>
    postJson<SeasonSchedulePreviewResult>(
      `/admin/seasons/${encodeURIComponent(input.seasonId)}/upload/preview`,
      { startDate: input.startDate, endDate: input.endDate },
    ),
  confirmSeasonSchedule: (input: {
    seasonId: string;
    entries: ConfirmedTournamentEntry[];
  }): Promise<SeasonImportResult> =>
    postJson<SeasonImportResult>(
      `/admin/seasons/${encodeURIComponent(input.seasonId)}/upload/confirm`,
      { entries: input.entries },
    ),
  // The backend's previewRoster route consumes the raw TSV/CSV body via
  // receiveText() rather than a JSON envelope, so post the text as-is.
  previewRoster: (roster: string): Promise<RosterPreview> =>
    postText<RosterPreview>('/admin/roster/preview', roster),
  confirmRoster: (input: { seasonId: string; teams: RosterConfirmTeamInput[] }): Promise<RosterConfirmResult> =>
    postJson<RosterConfirmResult>('/admin/roster/confirm', input),
};
