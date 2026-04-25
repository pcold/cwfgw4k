package com.cwfgw.admin

import com.cwfgw.tournaments.Tournament
import kotlinx.serialization.Serializable

/**
 * Result of a season-import call. The created list goes back to the UI as the
 * editable preview where operators set per-tournament multipliers and submit
 * back via the existing `PUT /api/v1/tournaments/{id}` route. Skipped entries
 * are surfaced with a reason so the operator knows why a calendar row didn't
 * land — almost always "already linked," which is the safe re-run case.
 */
@Serializable
data class SeasonImportResult(
    val created: List<Tournament>,
    val skipped: List<SkippedEntry>,
)

@Serializable
data class SkippedEntry(
    val espnEventId: String,
    val espnEventName: String,
    val reason: String,
)
