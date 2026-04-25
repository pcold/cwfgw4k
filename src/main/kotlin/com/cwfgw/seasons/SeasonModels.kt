package com.cwfgw.seasons

import com.cwfgw.leagues.LeagueId
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
value class SeasonId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

fun UUID.toSeasonId(): SeasonId = SeasonId(this)

fun String.toSeasonId(): SeasonId? = toUUIDOrNull()?.toSeasonId()

@Serializable
data class Season(
    val id: SeasonId,
    val leagueId: LeagueId,
    val name: String,
    val seasonYear: Int,
    val seasonNumber: Int,
    val status: String,
    @Serializable(with = BigDecimalSerializer::class) val tieFloor: BigDecimal,
    @Serializable(with = BigDecimalSerializer::class) val sideBetAmount: BigDecimal,
    val maxTeams: Int,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
    @Serializable(with = InstantSerializer::class) val updatedAt: Instant,
)

@Serializable
data class CreateSeasonRequest(
    val leagueId: LeagueId,
    val name: String,
    val seasonYear: Int,
    val seasonNumber: Int? = null,
    val maxTeams: Int? = null,
    @Serializable(with = BigDecimalSerializer::class) val tieFloor: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val sideBetAmount: BigDecimal? = null,
)

@Serializable
data class UpdateSeasonRequest(
    val name: String? = null,
    val status: String? = null,
    val maxTeams: Int? = null,
    @Serializable(with = BigDecimalSerializer::class) val tieFloor: BigDecimal? = null,
    @Serializable(with = BigDecimalSerializer::class) val sideBetAmount: BigDecimal? = null,
)

@Serializable
data class SeasonRules(
    val payouts: List<
        @Serializable(with = BigDecimalSerializer::class)
        BigDecimal,
        >,
    @Serializable(with = BigDecimalSerializer::class) val tieFloor: BigDecimal,
    val sideBetRounds: List<Int>,
    @Serializable(with = BigDecimalSerializer::class) val sideBetAmount: BigDecimal,
) {
    companion object {
        val DEFAULT_PAYOUTS: List<BigDecimal> =
            listOf(18, 12, 10, 8, 7, 6, 5, 4, 3, 2).map { BigDecimal(it) }
        val DEFAULT_TIE_FLOOR: BigDecimal = BigDecimal.ONE
        val DEFAULT_SIDE_BET_ROUNDS: List<Int> = listOf(5, 6, 7, 8)
        val DEFAULT_SIDE_BET_AMOUNT: BigDecimal = BigDecimal(15)

        /** Shared league defaults; used wherever a season hasn't customized its rules row. */
        fun defaults(): SeasonRules =
            SeasonRules(
                payouts = DEFAULT_PAYOUTS,
                tieFloor = DEFAULT_TIE_FLOOR,
                sideBetRounds = DEFAULT_SIDE_BET_ROUNDS,
                sideBetAmount = DEFAULT_SIDE_BET_AMOUNT,
            )
    }
}
