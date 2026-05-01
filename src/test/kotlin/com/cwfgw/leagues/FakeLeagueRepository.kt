package com.cwfgw.leagues

import com.cwfgw.db.TransactionContext
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

    context(ctx: TransactionContext)
    override suspend fun findAll(): List<League> = store.values.sortedBy { it.name }

    context(ctx: TransactionContext)
    override suspend fun findById(id: LeagueId): League? = store[id]

    context(ctx: TransactionContext)
    override suspend fun create(request: CreateLeagueRequest): League {
        val league = League(id = idFactory(), name = request.name, createdAt = clock())
        store[league.id] = league
        return league
    }
}
