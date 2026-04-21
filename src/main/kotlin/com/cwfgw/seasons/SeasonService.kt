package com.cwfgw.seasons

import com.cwfgw.leagues.LeagueId

class SeasonService(private val repository: SeasonRepository) {
    suspend fun list(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): List<Season> = repository.findAll(leagueId, seasonYear)

    suspend fun get(id: SeasonId): Season? = repository.findById(id)

    suspend fun create(request: CreateSeasonRequest): Season = repository.create(request)

    suspend fun update(
        id: SeasonId,
        request: UpdateSeasonRequest,
    ): Season? = repository.update(id, request)

    suspend fun getRules(id: SeasonId): SeasonRules? = repository.getRules(id)
}
