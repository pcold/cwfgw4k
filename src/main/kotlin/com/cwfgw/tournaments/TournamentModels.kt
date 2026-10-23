package com.cwfgw.tournaments

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.serialization.BigDecimalSerializer
import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.LocalDateSerializer
import com.cwfgw.serialization.UUIDSerializer
import com.cwfgw.serialization.toUUIDOrNull
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonClassDiscriminator
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@Serializable
@JvmInline
value class TournamentId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

fun UUID.toTournamentId(): TournamentId = TournamentId(this)

fun String.toTournamentId(): TournamentId? = toUUIDOrNull()?.toTournamentId()

@JvmInline
value class TournamentResultId(
    val value: UUID,
)

@Serializable
data class Tournament(
    val id: TournamentId,
    val pgaTournamentId: String?,
    val name: String,
    val seasonId: SeasonId,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate,
    val courseName: String?,
    val status: TournamentStatus,
    val purseAmount: Long?,
    @Serializable(with = BigDecimalSerializer::class) val payoutMultiplier: BigDecimal,
    val week: String?,
    val isTeamEvent: Boolean,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

/**
 * A variant per case, not a nullable id beside an `allFinalized` flag, which could claim a finalized season with no
 * tournament in it.
 */
@OptIn(ExperimentalSerializationApi::class)
@Serializable
@JsonClassDiscriminator("type")
sealed interface CurrentTournament {
    /** The earliest tournament not yet finalized, in schedule order. */
    @Serializable
    @SerialName("unfinalized")
    data class Unfinalized(val tournamentId: TournamentId) : CurrentTournament

    /** Every tournament is finalized; [tournamentId] is the season's last. */
    @Serializable
    @SerialName("season_finalized")
    data class SeasonFinalized(val tournamentId: TournamentId) : CurrentTournament

    /** Answered for an unknown season id too, as `GET /tournaments?season_id=` answers it with an empty list. */
    @Serializable
    @SerialName("no_tournaments")
    data object NoTournaments : CurrentTournament
}

data class TournamentResult(
    val id: TournamentResultId,
    val tournamentId: TournamentId,
    val golferId: GolferId,
    val position: Int?,
    val scoreToPar: Int?,
    val totalStrokes: Int?,
    val earnings: Long?,
    val round1: Int?,
    val round2: Int?,
    val round3: Int?,
    val round4: Int?,
    val madeCut: Boolean,
    val pairKey: String?,
)

/** [numTied] counts every result row at [position] in the tournament's whole field, as `tieSplitPayout` takes it. */
data class GolferFinish(
    val tournamentId: TournamentId,
    val position: Int,
    val numTied: Int,
)

data class NewTournament(
    val pgaTournamentId: String? = null,
    val name: String,
    val seasonId: SeasonId,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val courseName: String? = null,
    val purseAmount: Long? = null,
    val payoutMultiplier: BigDecimal? = null,
    val week: String? = null,
)

@Serializable
data class UpdateTournamentRequest(
    val name: String? = null,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate? = null,
    val courseName: String? = null,
    val status: TournamentStatus? = null,
    val purseAmount: Long? = null,
    @Serializable(with = BigDecimalSerializer::class) val payoutMultiplier: BigDecimal? = null,
    val isTeamEvent: Boolean? = null,
)

data class NewTournamentResult(
    val golferId: GolferId,
    val position: Int? = null,
    val scoreToPar: Int? = null,
    val totalStrokes: Int? = null,
    val earnings: Long? = null,
    val round1: Int? = null,
    val round2: Int? = null,
    val round3: Int? = null,
    val round4: Int? = null,
    val madeCut: Boolean = true,
    val pairKey: String? = null,
    val scorecards: List<RoundScorecard> = emptyList(),
)
