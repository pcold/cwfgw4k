package com.cwfgw.admin

import com.cwfgw.golfers.GolferId
import com.cwfgw.tournaments.Tournament
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator

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

/**
 * Preview of a roster TSV — what each pick *would* match against existing
 * golfers, without writing anything to the DB. The UI uses this to show the
 * operator which picks need manual resolution before they call confirmRoster.
 *
 * Counts are precomputed so the UI doesn't have to fold over the per-pick
 * statuses — useful for "12 of 78 picks need attention" headlines.
 */
@Serializable
data class RosterPreviewResult(
    val teams: List<PreviewTeam>,
    val totalPicks: Int,
    val matchedCount: Int,
    val ambiguousCount: Int,
    val unmatchedCount: Int,
)

@Serializable
data class PreviewTeam(
    val teamNumber: Int,
    val teamName: String,
    val picks: List<PreviewPick>,
)

@Serializable
data class PreviewPick(
    val round: Int,
    val playerName: String,
    val ownershipPct: Int,
    val match: PickMatch,
)

/**
 * Match result for one parsed pick. Sealed so the route response carries a
 * `kind` discriminator and the UI can render each variant without inspecting
 * which fields are populated. Adding a future variant (e.g. "FuzzySuggestion")
 * surfaces as a compile error in the consuming `when`.
 */
@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("kind")
sealed interface PickMatch {
    @Serializable
    @SerialName("matched")
    data class Matched(val golferId: GolferId, val golferName: String) : PickMatch

    @Serializable
    @SerialName("ambiguous")
    data class Ambiguous(val candidates: List<GolferCandidate>) : PickMatch

    @Serializable
    @SerialName("no_match")
    data object NoMatch : PickMatch
}

@Serializable
data class GolferCandidate(val golferId: GolferId, val name: String)
