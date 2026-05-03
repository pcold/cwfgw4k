package com.cwfgw.leagues

import com.cwfgw.db.Transactor

class LeagueService(
    private val repository: LeagueRepository,
    private val tx: Transactor,
) {
    suspend fun list(): List<League> = tx.get { repository.findAll() }

    suspend fun get(id: LeagueId): League? = tx.get { repository.findById(id) }

    suspend fun create(request: CreateLeagueRequest): League = tx.update { repository.create(request) }
}
