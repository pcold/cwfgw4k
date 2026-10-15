package com.cwfgw.golfers

import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.UUIDSerializer
import com.cwfgw.serialization.toUUIDOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
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
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
    /** Off the wire: only [GolferNameIndex] reads it, and a tombstoned row still answers every read by id. */
    @Transient val tombstonedAt: Instant? = null,
)

@Serializable
data class CreateGolferRequest(
    val pgaPlayerId: String? = null,
    val firstName: String,
    val lastName: String,
    val country: String? = null,
    val worldRanking: Int? = null,
)
