package com.cwfgw.drafts

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.UUIDSerializer
import com.cwfgw.teams.TeamId
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
@JvmInline
value class DraftId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

@Serializable
@JvmInline
value class DraftPickId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

@Serializable
data class Draft(
    val id: DraftId,
    val seasonId: SeasonId,
    val status: String,
    val draftType: String,
    @Serializable(with = InstantSerializer::class) val startedAt: Instant?,
    @Serializable(with = InstantSerializer::class) val completedAt: Instant?,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

@Serializable
data class DraftPick(
    val id: DraftPickId,
    val draftId: DraftId,
    val teamId: TeamId,
    val golferId: GolferId?,
    val roundNum: Int,
    val pickNum: Int,
    @Serializable(with = InstantSerializer::class) val pickedAt: Instant?,
)

@Serializable
data class CreateDraftRequest(
    val draftType: String? = null,
)

@Serializable
data class MakePickRequest(
    val teamId: TeamId,
    val golferId: GolferId,
)
