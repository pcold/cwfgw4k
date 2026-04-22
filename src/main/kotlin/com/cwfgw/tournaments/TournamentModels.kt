package com.cwfgw.tournaments

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.serialization.BigDecimalSerializer
import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.LocalDateSerializer
import com.cwfgw.serialization.UUIDSerializer
import com.cwfgw.serialization.toUUIDOrNull
import kotlinx.serialization.Serializable
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

@Serializable
@JvmInline
value class TournamentResultId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
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
    val status: String,
    val purseAmount: Long?,
    @Serializable(with = BigDecimalSerializer::class) val payoutMultiplier: BigDecimal,
    val week: String?,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

@Serializable
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
)

@Serializable
data class CreateTournamentRequest(
    val pgaTournamentId: String? = null,
    val name: String,
    val seasonId: SeasonId,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate,
    val courseName: String? = null,
    val purseAmount: Long? = null,
    @Serializable(with = BigDecimalSerializer::class) val payoutMultiplier: BigDecimal? = null,
    val week: String? = null,
)

@Serializable
data class UpdateTournamentRequest(
    val name: String? = null,
    @Serializable(with = LocalDateSerializer::class) val startDate: LocalDate? = null,
    @Serializable(with = LocalDateSerializer::class) val endDate: LocalDate? = null,
    val courseName: String? = null,
    val status: String? = null,
    val purseAmount: Long? = null,
    @Serializable(with = BigDecimalSerializer::class) val payoutMultiplier: BigDecimal? = null,
)

@Serializable
data class CreateTournamentResultRequest(
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
)
