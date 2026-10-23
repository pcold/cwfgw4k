import type {
  ConfirmRosterRequest,
  ConfirmSeasonScheduleRequest,
  DataHealthReport,
  RosterConfirmResult,
  RosterPreview,
  SeasonImportResult,
  SeasonSchedulePreviewResult,
  UploadSeasonRequest,
} from '../types';
import { getJson, postJson, postText } from './core';

export const adminApi = {
  previewSeasonSchedule: (seasonId: string, body: UploadSeasonRequest): Promise<SeasonSchedulePreviewResult> =>
    postJson<SeasonSchedulePreviewResult>(`/admin/seasons/${encodeURIComponent(seasonId)}/upload/preview`, body),
  confirmSeasonSchedule: (seasonId: string, body: ConfirmSeasonScheduleRequest): Promise<SeasonImportResult> =>
    postJson<SeasonImportResult>(`/admin/seasons/${encodeURIComponent(seasonId)}/upload/confirm`, body),
  // The backend's previewRoster route consumes the raw TSV/CSV body via
  // receiveText() rather than a JSON envelope, so post the text as-is.
  previewRoster: (roster: string): Promise<RosterPreview> => postText<RosterPreview>('/admin/roster/preview', roster),
  confirmRoster: (body: ConfirmRosterRequest): Promise<RosterConfirmResult> =>
    postJson<RosterConfirmResult>('/admin/roster/confirm', body),
  dataHealth: (): Promise<DataHealthReport> => getJson<DataHealthReport>('/admin/data-health'),
};
