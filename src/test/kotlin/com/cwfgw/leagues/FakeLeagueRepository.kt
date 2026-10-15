package com.cwfgw.leagues

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.testing.fakeTableOf
import java.time.Instant
import java.util.UUID

class FakeLeagueRepository(
    initial: List<League> = emptyList(),
    private val idFactory: () -> LeagueId = { LeagueId(UUID.randomUUID()) },
    private val clock: () -> Instant = Instant::now,
) : LeagueRepository {
    private val store = fakeTableOf<LeagueId, League>()

    init {
        initial.forEach { league -> store[league.id] = league }
    }

    context(ctx: TransactionContext)
    override fun findAll(): List<League> = store.values.sortedBy { it.name }

    context(ctx: WriteTransactionContext)
    override fun create(request: CreateLeagueRequest): League {
        val league = League(id = idFactory(), name = request.name, createdAt = clock())
        store[league.id] = league
        return league
    }
}
