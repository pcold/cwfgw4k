package com.cwfgw.teams

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.serialization.BigDecimalSerializer
import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.UUIDSerializer
import com.cwfgw.serialization.toUUIDOrNull
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Serializable
@JvmInline
value class TeamId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

fun UUID.toTeamId(): TeamId = TeamId(this)

fun String.toTeamId(): TeamId? = toUUIDOrNull()?.toTeamId()

@Serializable
@JvmInline
value class RosterEntryId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

fun UUID.toRosterEntryId(): RosterEntryId = RosterEntryId(this)

fun String.toRosterEntryId(): RosterEntryId? = toUUIDOrNull()?.toRosterEntryId()

@Serializable
data class Team(
    val id: TeamId,
    val seasonId: SeasonId,
    val ownerName: String,
    val teamName: String,
    val teamNumber: Int?,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)

@Serializable
data class CreateTeamRequest(
    val ownerName: String,
    val teamName: String,
    val teamNumber: Int? = null,
)

@Serializable
data class UpdateTeamRequest(
    val ownerName: String? = null,
    val teamName: String? = null,
)

@Serializable
data class RosterEntry(
    val id: RosterEntryId,
    val teamId: TeamId,
    val golferId: GolferId,
    val acquiredVia: String,
    val draftRound: Int?,
    @Serializable(with = BigDecimalSerializer::class) val ownershipPct: BigDecimal,
    @Serializable(with = InstantSerializer::class) val acquiredAt: Instant,
    @Serializable(with = InstantSerializer::class) val droppedAt: Instant?,
    val isActive: Boolean,
)

@Serializable
data class AddToRosterRequest(
    val golferId: GolferId,
    val acquiredVia: String? = null,
    val draftRound: Int? = null,
    @Serializable(with = BigDecimalSerializer::class) val ownershipPct: BigDecimal? = null,
)

@Serializable
data class RosterViewTeam(
    val teamId: TeamId,
    val teamName: String,
    val picks: List<RosterViewPick>,
)

@Serializable
data class RosterViewPick(
    val round: Int,
    val golferName: String,
    @Serializable(with = BigDecimalSerializer::class) val ownershipPct: BigDecimal,
    val golferId: GolferId,
)
