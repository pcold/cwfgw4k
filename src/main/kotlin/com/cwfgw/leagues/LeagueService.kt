package com.cwfgw.leagues

import com.cwfgw.db.Transactor

class LeagueService(
    private val repository: LeagueRepository,
    private val tx: Transactor,
) {
    suspend fun list(): List<League> = tx.read { repository.findAll() }

    suspend fun get(id: LeagueId): League? = tx.read { repository.findById(id) }

    suspend fun create(request: CreateLeagueRequest): League = tx.update { repository.create(request) }
}
