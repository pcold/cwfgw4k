package com.cwfgw.leagues

interface LeagueRepository {
    suspend fun findAll(): List<League>

    suspend fun findById(id: LeagueId): League?

    suspend fun create(request: CreateLeagueRequest): League
}
