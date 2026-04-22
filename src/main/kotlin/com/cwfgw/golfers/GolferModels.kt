package com.cwfgw.golfers

import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.UUIDSerializer
import com.cwfgw.serialization.toUUIDOrNull
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
@JvmInline
value class GolferId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

fun UUID.toGolferId(): GolferId = GolferId(this)

fun String.toGolferId(): GolferId? = toUUIDOrNull()?.toGolferId()

@Serializable
data class Golfer(
    val id: GolferId,
    val pgaPlayerId: String?,
    val firstName: String,
    val lastName: String,
    val country: String?,
    val worldRanking: Int?,
    val active: Boolean,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)

@Serializable
data class CreateGolferRequest(
    val pgaPlayerId: String? = null,
    val firstName: String,
    val lastName: String,
    val country: String? = null,
    val worldRanking: Int? = null,
)

@Serializable
data class UpdateGolferRequest(
    val pgaPlayerId: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val country: String? = null,
    val worldRanking: Int? = null,
    val active: Boolean? = null,
)
