package com.cwfgw.testing

import com.cwfgw.db.TransactionContext
import com.cwfgw.jooq.tables.references.TEAM_ROSTERS
import com.cwfgw.teams.RosterEntryId
import java.time.OffsetDateTime

/** Nothing in production drops a roster entry, so a spec that needs a dropped one sets `dropped_at` itself. */
context(ctx: TransactionContext)
internal fun markDropped(entryId: RosterEntryId) {
    ctx.dsl.update(TEAM_ROSTERS)
        .set(TEAM_ROSTERS.DROPPED_AT, OffsetDateTime.now())
        .where(TEAM_ROSTERS.ID.eq(entryId.value))
        .execute()
}
