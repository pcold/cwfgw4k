package com.cwfgw.testing

import com.cwfgw.db.TransactionContext
import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.references.GOLFERS

/** No route or service deactivates a golfer, so a spec that needs an inactive one writes the column itself. */
context(ctx: TransactionContext)
internal fun deactivateGolfer(golferId: GolferId) {
    ctx.dsl.update(GOLFERS)
        .set(GOLFERS.ACTIVE, false)
        .where(GOLFERS.ID.eq(golferId.value))
        .execute()
}
