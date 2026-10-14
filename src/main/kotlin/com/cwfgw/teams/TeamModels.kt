package com.cwfgw.teams

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.serialization.BigDecimalSerializer
import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.UUIDSerializer
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Serializable
@JvmInline
value class TeamId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

@JvmInline
value class RosterEntryId(val value: UUID)

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

data class CreateTeamRequest(
    val ownerName: String,
    val teamName: String,
    val teamNumber: Int? = null,
)

data class RosterEntry(
    val id: RosterEntryId,
    val teamId: TeamId,
    val golferId: GolferId,
    val acquiredVia: String,
    val draftRound: Int?,
    val ownershipPct: BigDecimal,
    val acquiredAt: Instant,
    val isActive: Boolean,
)

data class AddToRosterRequest(
    val golferId: GolferId,
    val acquiredVia: String? = null,
    val draftRound: Int? = null,
    val ownershipPct: BigDecimal? = null,
)

@Serializable
data class RosterViewTeam(
    val teamId: TeamId,
    val teamNumber: Int?,
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
