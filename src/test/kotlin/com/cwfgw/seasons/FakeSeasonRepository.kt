package com.cwfgw.seasons

import com.cwfgw.leagues.LeagueId
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private val store = ConcurrentHashMap<SeasonId, Season>()
    private val rulesStore = ConcurrentHashMap<SeasonId, SeasonRules>()

    init {
        initial.forEach { season -> store[season.id] = season }
        rulesStore.putAll(customRules)
    }

    override suspend fun findAll(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): List<Season> =
        store.values
            .filter { season ->
                (leagueId == null || season.leagueId == leagueId) &&
                    (seasonYear == null || season.seasonYear == seasonYear)
            }
            .sortedWith(seasonOrdering)

    override suspend fun findById(id: SeasonId): Season? = store[id]

    override suspend fun create(request: CreateSeasonRequest): Season {
        val now = clock()
        val season =
            Season(
                id = idFactory(),
                leagueId = request.leagueId,
                name = request.name,
                seasonYear = request.seasonYear,
                seasonNumber = request.seasonNumber ?: 1,
                status = "draft",
                tieFloor = request.tieFloor ?: SeasonRules.DEFAULT_TIE_FLOOR,
                sideBetAmount = request.sideBetAmount ?: SeasonRules.DEFAULT_SIDE_BET_AMOUNT,
                maxTeams = request.maxTeams ?: DEFAULT_MAX_TEAMS,
                createdAt = now,
                updatedAt = now,
            )
        store[season.id] = season
        return season
    }

    override suspend fun update(
        id: SeasonId,
        request: UpdateSeasonRequest,
    ): Season? {
        val current = store[id] ?: return null
        val touched = request.hasAnyChange()
        val updated =
            current.copy(
                name = request.name ?: current.name,
                status = request.status ?: current.status,
                maxTeams = request.maxTeams ?: current.maxTeams,
                tieFloor = request.tieFloor ?: current.tieFloor,
                sideBetAmount = request.sideBetAmount ?: current.sideBetAmount,
                updatedAt = if (touched) clock() else current.updatedAt,
            )
        store[id] = updated
        return updated
    }

    override suspend fun getRules(id: SeasonId): SeasonRules? {
        val season = store[id] ?: return null
        val custom = rulesStore[id]
        return SeasonRules(
            payouts = custom?.payouts ?: SeasonRules.DEFAULT_PAYOUTS,
            tieFloor = season.tieFloor,
            sideBetRounds = custom?.sideBetRounds ?: SeasonRules.DEFAULT_SIDE_BET_ROUNDS,
            sideBetAmount = season.sideBetAmount,
        )
    }

    private fun UpdateSeasonRequest.hasAnyChange(): Boolean =
        name != null ||
            status != null ||
            maxTeams != null ||
            tieFloor != null ||
            sideBetAmount != null

    companion object {
        private const val DEFAULT_MAX_TEAMS = 10
    }
}
