package com.cwfgw.golfers

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.testing.fakeTableOf
import com.cwfgw.testing.uniqueViolation
import java.time.Instant
import java.util.UUID

private val golferOrdering: Comparator<Golfer> =
    compareBy<Golfer, Int?>(nullsLast()) { it.worldRanking }
        .thenBy { it.lastName }

class FakeGolferRepository(
    initial: List<Golfer> = emptyList(),
    private val idFactory: () -> GolferId = { GolferId(UUID.randomUUID()) },
    private val clock: () -> Instant = Instant::now,
) : GolferRepository {
    private val store = fakeTableOf<GolferId, Golfer>()

    init {
        initial.forEach { golfer -> store[golfer.id] = golfer }
    }

    context(ctx: TransactionContext)
    override fun findAll(search: String?): List<Golfer> {
        val term = search?.lowercase()
        return store.values
            .filter { golfer -> golfer.matchesSearch(term) }
            .sortedWith(golferOrdering)
    }

    context(ctx: TransactionContext)
    override fun findById(id: GolferId): Golfer? = store[id]

    context(ctx: TransactionContext)
    override fun findByPgaPlayerId(pgaPlayerId: String): Golfer? =
        store.values.firstOrNull { it.pgaPlayerId == pgaPlayerId }

    context(ctx: WriteTransactionContext)
    override fun create(request: CreateGolferRequest): Golfer {
        if (request.pgaPlayerId != null && findByPgaPlayerId(request.pgaPlayerId) != null) {
            throw uniqueViolation(UNIQUE_PGA_PLAYER_ID)
        }
        val golfer =
            Golfer(
                id = idFactory(),
                pgaPlayerId = request.pgaPlayerId,
                firstName = request.firstName,
                lastName = request.lastName,
                country = request.country,
                worldRanking = request.worldRanking,
                updatedAt = clock(),
            )
        store[golfer.id] = golfer
        return golfer
    }

    context(ctx: WriteTransactionContext)
    override fun findOrCreate(request: CreateGolferRequest): Golfer =
        request.pgaPlayerId?.let { findByPgaPlayerId(it) } ?: create(request)

    private fun Golfer.matchesSearch(term: String?): Boolean {
        if (term == null) return true
        return firstName.lowercase().contains(term) || lastName.lowercase().contains(term)
    }
}
