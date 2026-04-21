package com.cwfgw.leagues

class LeagueService(private val repository: LeagueRepository) {
    suspend fun list(): List<League> = repository.findAll()

    suspend fun get(id: LeagueId): League? = repository.findById(id)

    suspend fun create(request: CreateLeagueRequest): League = repository.create(request)
}
