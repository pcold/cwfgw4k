package com.cwfgw.leagues

import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class FakeLeagueRepository(
    initial: List<League> = emptyList(),
    private val idFactory: () -> LeagueId = { LeagueId(UUID.randomUUID()) },
    private val clock: () -> Instant = Instant::now,
) : LeagueRepository {
    private val store = ConcurrentHashMap<LeagueId, League>()

    init {
        initial.forEach { league -> store[league.id] = league }
    }

    override suspend fun findAll(): List<League> = store.values.sortedBy { it.name }

    override suspend fun findById(id: LeagueId): League? = store[id]

    override suspend fun create(request: CreateLeagueRequest): League {
        val league = League(id = idFactory(), name = request.name, createdAt = clock())
        store[league.id] = league
        return league
    }
}
