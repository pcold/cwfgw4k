package com.cwfgw.seasons

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.leagues.LeagueId
import com.cwfgw.testing.fakeTableOf
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/** Every money column on a season is numeric(10,4): Postgres answers four places whatever the request carried. */
private const val MONEY_SCALE = 4

private val seasonOrdering: Comparator<Season> =
    compareByDescending<Season> { it.seasonYear }
        .thenByDescending { it.seasonNumber }
        .thenByDescending { it.createdAt }

class FakeSeasonRepository(
    initial: List<Season> = emptyList(),
    customRules: Map<SeasonId, SeasonRules> = emptyMap(),
    private val idFactory: () -> SeasonId = { SeasonId(UUID.randomUUID()) },
    private val clock: () -> Instant = Instant::now,
) : SeasonRepository {
    private val store = fakeTableOf<SeasonId, Season>()
    private val rulesStore = fakeTableOf<SeasonId, SeasonRules>()

    init {
        initial.forEach { season -> store[season.id] = season }
        rulesStore.putAll(customRules)
    }

    context(ctx: TransactionContext)
    override fun findAll(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): List<Season> =
        store.values
            .filter { season ->
                (leagueId == null || season.leagueId == leagueId) &&
                    (seasonYear == null || season.seasonYear == seasonYear)
            }
            .sortedWith(seasonOrdering)

    context(ctx: TransactionContext)
    override fun findById(id: SeasonId): Season? = store[id]

    context(ctx: WriteTransactionContext)
    override fun create(request: CreateSeasonRequest): Season {
        val now = clock()
        val tieFloor = request.rules?.tieFloor ?: SeasonRules.DEFAULT_TIE_FLOOR
        val sideBetAmount = request.rules?.sideBetAmount ?: SeasonRules.DEFAULT_SIDE_BET_AMOUNT
        val season =
            Season(
                id = idFactory(),
                leagueId = request.leagueId,
                name = request.name,
                seasonYear = request.seasonYear,
                seasonNumber = request.seasonNumber ?: nextSeasonNumber(request.leagueId, request.seasonYear),
                tieFloor = tieFloor.toMoneyScale(),
                sideBetAmount = sideBetAmount.toMoneyScale(),
                maxTeams = request.maxTeams ?: DEFAULT_MAX_TEAMS,
                createdAt = now,
                updatedAt = now,
            )
        store[season.id] = season
        request.rules?.let { rules ->
            rulesStore[season.id] =
                rules.copy(
                    payouts = rules.payouts.map { it.toMoneyScale() },
                    sideBetRounds = rules.sideBetRounds.sorted(),
                )
        }
        return season
    }

    // Mirrors JooqSeasonRepository.nextSeasonNumber: auto-increment within a league + year.
    private fun nextSeasonNumber(
        leagueId: LeagueId,
        seasonYear: Int,
    ): Int =
        store.values
            .filter { it.leagueId == leagueId && it.seasonYear == seasonYear }
            .maxOfOrNull { it.seasonNumber }
            ?.plus(1) ?: 1

    context(ctx: TransactionContext)
    override fun getRules(id: SeasonId): SeasonRules? {
        val season = store[id] ?: return null
        val custom = rulesStore[id]
        return SeasonRules(
            payouts = custom?.payouts.orEmpty().ifEmpty { SeasonRules.DEFAULT_PAYOUTS },
            tieFloor = season.tieFloor,
            sideBetRounds = custom?.sideBetRounds.orEmpty().ifEmpty { SeasonRules.DEFAULT_SIDE_BET_ROUNDS },
            sideBetAmount = season.sideBetAmount,
        )
    }

    context(ctx: WriteTransactionContext)
    override fun delete(id: SeasonId): Boolean {
        rulesStore.remove(id)
        return store.remove(id) != null
    }

    private fun BigDecimal.toMoneyScale(): BigDecimal = setScale(MONEY_SCALE, RoundingMode.HALF_UP)

    companion object {
        private const val DEFAULT_MAX_TEAMS = 10
    }
}
