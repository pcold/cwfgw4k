package com.cwfgw.admin

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.serialization.LocalDateSerializer
import com.cwfgw.teams.Team
import com.cwfgw.tournaments.Tournament
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.LocalDate

/**
 * Body of `POST /api/v1/admin/seasons/{id}/upload/preview`. Date range is
 * inclusive on both ends — any ESPN calendar entry whose start date falls in
 * the range is returned as a candidate. The seasonId comes from the path,
 * not the body. Nothing is written to the DB by the preview call.
 */
@Serializable
data class UploadSeasonRequest(
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate,
)

/**
 * Result of a season-schedule preview call. `entries` are candidate
 * tournaments the operator can drop before confirming — e.g. an ESPN
 * calendar entry like the Presidents Cup that isn't part of this league's
 * season. Week labels are computed over the full candidate set so they stay
 * chronologically consistent; dropping an entry before confirm closes the
 * gap because [AdminService.confirmSeasonSchedule] recomputes labels over
 * only the entries the operator kept.
 */
@Serializable
data class SeasonSchedulePreviewResult(
    val entries: List<PreviewedTournament>,
    val skipped: List<SkippedEntry>,
)

@Serializable
data class PreviewedTournament(
    val espnEventId: String,
    val name: String,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate,
    val week: String,
)

/**
 * Body of `POST /api/v1/admin/seasons/{id}/upload/confirm`. `entries` is
 * the operator-reviewed subset of a prior [SeasonSchedulePreviewResult] —
 * typically the same list minus any rows the operator dropped. Week labels
 * are not carried over from the preview; the service recomputes them from
 * [entries] alone so removed rows don't leave a numbering gap.
 */
@Serializable
data class ConfirmSeasonScheduleRequest(
    val entries: List<ConfirmedTournamentEntry>,
)

@Serializable
data class ConfirmedTournamentEntry(
    val espnEventId: String,
    val name: String,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate,
)

/** Carry a preview candidate straight into a confirm call, dropping the preview-only `week`. */
fun PreviewedTournament.toConfirmedEntry(): ConfirmedTournamentEntry =
    ConfirmedTournamentEntry(espnEventId = espnEventId, name = name, startDate = startDate, endDate = endDate)

/**
 * Result of a season-schedule confirm call. The created list goes back to
 * the UI as the editable preview where operators set per-tournament
 * multipliers and submit back via the existing `PUT /api/v1/tournaments/{id}`
 * route. Skipped entries are surfaced with a reason so the operator knows
 * why a row didn't land — almost always "already linked," which covers a
 * confirm that races a second operator's import of the same event.
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
 * `type` discriminator and the UI can render each variant without inspecting
 * which fields are populated. Adding a future variant (e.g. "FuzzySuggestion")
 * surfaces as a compile error in the consuming `when`.
 */
@Serializable
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

/**
 * Operator-confirmed roster ready to write. Each pick's golfer is fully
 * resolved — either an existing golfer id (operator accepted Matched or
 * picked from Ambiguous candidates) or an explicit new-golfer record
 * (operator chose to create rather than reuse). Forcing the choice keeps
 * typo'd names from quietly multiplying golfer rows.
 */
@Serializable
data class ConfirmRosterRequest(
    val seasonId: SeasonId,
    val teams: List<ConfirmedTeam>,
)

@Serializable
data class ConfirmedTeam(
    val teamNumber: Int,
    val teamName: String,
    val picks: List<ConfirmedPick>,
)

@Serializable
data class ConfirmedPick(
    val round: Int,
    val ownershipPct: Int,
    val assignment: GolferAssignment,
)

/**
 * How to bind a pick to a golfer. `Existing` references a row already in
 * the golfers table; `New` instructs the service to create one as part of
 * the confirm. Sealed + discriminated so the route response carries a
 * `type` tag and adding a future variant fails compilation in callers.
 */
@Serializable
sealed interface GolferAssignment {
    @Serializable
    @SerialName("existing")
    data class Existing(val golferId: GolferId) : GolferAssignment

    @Serializable
    @SerialName("new")
    data class New(val firstName: String, val lastName: String) : GolferAssignment
}

/**
 * Result of a successful confirmRoster write. Counts cover what the call
 * just inserted; `teams` is the persisted Team rows with their assigned
 * ids so the UI can link directly into the season after success.
 */
@Serializable
data class RosterUploadResult(
    val teamsCreated: Int,
    val golfersCreated: Int,
    val teams: List<Team>,
)
