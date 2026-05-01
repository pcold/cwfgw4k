package com.cwfgw.seasons

import com.cwfgw.db.Transactor
import com.cwfgw.leagues.LeagueId

class SeasonService(
    private val repository: SeasonRepository,
    private val tx: Transactor,
) {
    suspend fun list(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): List<Season> = tx.read { repository.findAll(leagueId, seasonYear) }

    suspend fun get(id: SeasonId): Season? = tx.read { repository.findById(id) }

    suspend fun create(request: CreateSeasonRequest): Season = tx.update { repository.create(request) }

    suspend fun update(
        id: SeasonId,
        request: UpdateSeasonRequest,
    ): Season? = tx.update { repository.update(id, request) }

    suspend fun getRules(id: SeasonId): SeasonRules? = tx.read { repository.getRules(id) }

    suspend fun delete(id: SeasonId): Boolean = tx.update { repository.delete(id) }
}
