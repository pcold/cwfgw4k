package com.cwfgw.leagues

import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.UUIDSerializer
import com.cwfgw.serialization.toUUIDOrNull
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

@Serializable
@JvmInline
value class LeagueId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

fun UUID.toLeagueId(): LeagueId = LeagueId(this)

fun String.toLeagueId(): LeagueId? = toUUIDOrNull()?.toLeagueId()

@Serializable
data class League(
    val id: LeagueId,
    val name: String,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

@Serializable
data class CreateLeagueRequest(val name: String)
