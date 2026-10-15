package com.cwfgw.testing

import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.jooq.tables.references.GOLFERS
import java.time.Instant
import java.time.ZoneOffset

/**
 * Written past the repository, which has no tombstone write: an operator's script is the only production writer.
 * [tombstonedAt] takes null so a spec can write the [mergedInto] the schema must refuse.
 */
context(ctx: WriteTransactionContext)
fun seedTombstone(
    golferId: GolferId,
    mergedInto: GolferId? = null,
    tombstonedAt: Instant? = Instant.parse("2026-10-15T00:00:00Z"),
) {
    ctx.dsl.update(GOLFERS)
        .set(GOLFERS.TOMBSTONED_AT, tombstonedAt?.atOffset(ZoneOffset.UTC))
        .set(GOLFERS.MERGED_INTO, mergedInto?.value)
        .where(GOLFERS.ID.eq(golferId.value))
        .execute()
}

/** @see seedTombstone */
context(ctx: WriteTransactionContext)
fun seedTombstonedGolfer(
    firstName: String,
    lastName: String,
): Golfer {
    val golfers = GolferRepository()
    val golfer = golfers.create(CreateGolferRequest(firstName = firstName, lastName = lastName))
    seedTombstone(golfer.id)
    return checkNotNull(golfers.findById(golfer.id))
}
