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
 * Writes in [PgaPlayerIdClaim.pgaPlayerId] order, one write per id and a backfill over a create, so two transactions
 * sharing athletes take their id locks in one order. An extension, so no implementation or decorator can reorder it.
 * Neither key-share locks on named golfers nor the row lock two backfills of one row share are ordered by this.
 */
context(ctx: WriteTransactionContext)
internal fun GolferRepository.findOrCreateAll(claims: List<PgaPlayerIdClaim>): Map<String, Golfer> =
    claims.sortedWith(compareBy<PgaPlayerIdClaim> { it.pgaPlayerId }.thenBy { it is PgaPlayerIdClaim.Create })
        .distinctBy(PgaPlayerIdClaim::pgaPlayerId)
        .associate { claim -> claim.pgaPlayerId to write(claim) }

context(ctx: WriteTransactionContext)
private fun GolferRepository.write(claim: PgaPlayerIdClaim): Golfer =
    when (claim) {
        is PgaPlayerIdClaim.Backfill ->
            backfillPgaPlayerId(claim.golferId, claim.pgaPlayerId) ?: findOrCreate(claim.toCreateRequest())
        is PgaPlayerIdClaim.Create -> findOrCreate(claim.toCreateRequest())
    }

private fun PgaPlayerIdClaim.toCreateRequest(): CreateGolferRequest =
    CreateGolferRequest(pgaPlayerId = pgaPlayerId, firstName = name.firstName, lastName = name.lastName)
