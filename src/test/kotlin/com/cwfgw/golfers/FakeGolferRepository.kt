package com.cwfgw.golfers

import com.cwfgw.db.TransactionContext
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val golferOrdering: Comparator<Golfer> =
    compareBy<Golfer, Int?>(nullsLast()) { it.worldRanking }
        .thenBy { it.lastName }

class FakeGolferRepository(
    initial: List<Golfer> = emptyList(),
    private val idFactory: () -> GolferId = { GolferId(UUID.randomUUID()) },
    private val clock: () -> Instant = Instant::now,
) : GolferRepository {
    private val store = ConcurrentHashMap<GolferId, Golfer>()

    init {
        initial.forEach { golfer -> store[golfer.id] = golfer }
    }

    context(ctx: TransactionContext)
    override suspend fun findAll(
        activeOnly: Boolean,
        search: String?,
    ): List<Golfer> {
        val term = search?.lowercase()
        return store.values
            .filter { golfer -> golfer.matchesFilter(activeOnly, term) }
            .sortedWith(golferOrdering)
    }

    context(ctx: TransactionContext)
    override suspend fun findById(id: GolferId): Golfer? = store[id]

    context(ctx: TransactionContext)
    override suspend fun findByPgaPlayerId(pgaPlayerId: String): Golfer? =
        store.values.firstOrNull { it.pgaPlayerId == pgaPlayerId }

    context(ctx: TransactionContext)
    override suspend fun create(request: CreateGolferRequest): Golfer {
        val golfer =
            Golfer(
                id = idFactory(),
                pgaPlayerId = request.pgaPlayerId,
                firstName = request.firstName,
                lastName = request.lastName,
                country = request.country,
                worldRanking = request.worldRanking,
                active = true,
                updatedAt = clock(),
            )
        store[golfer.id] = golfer
        return golfer
    }

    context(ctx: TransactionContext)
    override suspend fun update(
        id: GolferId,
        request: UpdateGolferRequest,
    ): Golfer? {
        val current = store[id] ?: return null
        val touched = request.hasAnyChange()
        val updated =
            current.copy(
                pgaPlayerId = request.pgaPlayerId ?: current.pgaPlayerId,
                firstName = request.firstName ?: current.firstName,
                lastName = request.lastName ?: current.lastName,
                country = request.country ?: current.country,
                worldRanking = request.worldRanking ?: current.worldRanking,
                active = request.active ?: current.active,
                updatedAt = if (touched) clock() else current.updatedAt,
            )
        store[id] = updated
        return updated
    }

    private fun Golfer.matchesFilter(
        activeOnly: Boolean,
        term: String?,
    ): Boolean {
        if (activeOnly && !active) return false
        if (term == null) return true
        return firstName.lowercase().contains(term) || lastName.lowercase().contains(term)
    }

    private fun UpdateGolferRequest.hasAnyChange(): Boolean =
        pgaPlayerId != null ||
            firstName != null ||
            lastName != null ||
            country != null ||
            worldRanking != null ||
            active != null
}
