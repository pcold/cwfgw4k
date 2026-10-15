package com.cwfgw.golfers

import com.cwfgw.db.WriteTransactionContext

/**
 * Either kind takes [pgaPlayerId]'s unique-index entry, so a transaction holding it uncommitted makes the other wait.
 * That is why every claim goes through [findOrCreateAll], the one place they are ordered.
 */
internal sealed interface PgaPlayerIdClaim {
    val pgaPlayerId: String
    val name: GolferName

    /**
     * A backfill answering null, as when a concurrent writer gave [golferId] an id first, falls back to what [Create]
     * does: that writer's row comes back, or one inserted beside a row that turned out to be a namesake.
     */
    data class Backfill(
        val golferId: GolferId,
        override val pgaPlayerId: String,
        override val name: GolferName,
    ) : PgaPlayerIdClaim

    /** @see PgaPlayerIdClaim */
    data class Create(
        override val pgaPlayerId: String,
        override val name: GolferName,
    ) : PgaPlayerIdClaim
}

/**
 * Locks first, in one golfer-id order: each backfill's row as its UPDATE will, and each of [referenced] as the result
 * or roster row the caller inserts next will. Then writes in [PgaPlayerIdClaim.pgaPlayerId] order, one write per id
 * and a backfill over a create. An extension, so no implementation or decorator can reorder either. A holder a create
 * finds is left unlocked unless in [referenced]: it had its id when found, so no backfill's lock can stand on it.
 */
context(ctx: WriteTransactionContext)
internal fun GolferRepository.findOrCreateAll(
    claims: List<PgaPlayerIdClaim>,
    referenced: Set<GolferId> = emptySet(),
): Map<String, Golfer> {
    lockInOrder(claims, referenced)
    return claims.sortedWith(compareBy<PgaPlayerIdClaim> { it.pgaPlayerId }.thenBy { it is PgaPlayerIdClaim.Create })
        .distinctBy(PgaPlayerIdClaim::pgaPlayerId)
        .associate { claim -> claim.pgaPlayerId to write(claim) }
}

context(ctx: WriteTransactionContext)
private fun GolferRepository.lockInOrder(
    claims: List<PgaPlayerIdClaim>,
    referenced: Set<GolferId>,
) {
    val backfilled = claims.mapNotNull { (it as? PgaPlayerIdClaim.Backfill)?.golferId }.toSet()
    (backfilled + referenced).sortedBy(GolferId::value).forEach { id ->
        lockRow(id, if (id in backfilled) GolferRowLock.Backfill else GolferRowLock.Reference)
    }
}

context(ctx: WriteTransactionContext)
private fun GolferRepository.write(claim: PgaPlayerIdClaim): Golfer =
    when (claim) {
        is PgaPlayerIdClaim.Backfill ->
            backfillPgaPlayerId(claim.golferId, claim.pgaPlayerId) ?: findOrCreate(claim.toCreateRequest())
        is PgaPlayerIdClaim.Create -> findOrCreate(claim.toCreateRequest())
    }

private fun PgaPlayerIdClaim.toCreateRequest(): CreateGolferRequest =
    CreateGolferRequest(pgaPlayerId = pgaPlayerId, firstName = name.firstName, lastName = name.lastName)
