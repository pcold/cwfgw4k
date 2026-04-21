package com.cwfgw.seasons

import com.cwfgw.leagues.LeagueId

interface SeasonRepository {
    suspend fun findAll(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): List<Season>

    suspend fun findById(id: SeasonId): Season?

    suspend fun create(request: CreateSeasonRequest): Season

    suspend fun update(
        id: SeasonId,
        request: UpdateSeasonRequest,
    ): Season?

    suspend fun getRules(id: SeasonId): SeasonRules?
}
