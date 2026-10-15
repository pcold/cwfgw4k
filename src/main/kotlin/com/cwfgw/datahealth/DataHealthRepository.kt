package com.cwfgw.datahealth

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.required
import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.records.GolfersRecord
import com.cwfgw.jooq.tables.references.GOLFERS
import com.cwfgw.jooq.tables.references.SEASONS
import com.cwfgw.jooq.tables.references.TEAMS
import com.cwfgw.jooq.tables.references.TEAM_ROSTERS
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL

/** "Currently rostered" means on a team in its own league's latest season, ordered as `SeasonRepository.findAll`. */
interface DataHealthRepository {
    /** Tombstones stay in: an ESPN id held by a tombstone still takes the results. */
    context(ctx: TransactionContext)
    fun findRosteredTwins(): List<RosteredTwinGroup>

    context(ctx: TransactionContext)
    fun findRosteredWithoutEspnId(): List<DataHealthGolfer>

    /** Tombstones are left out: a blank first name breaks only name matching, which never offers a tombstone. */
    context(ctx: TransactionContext)
    fun findBlankFirstNames(): List<DataHealthGolfer>
}

fun DataHealthRepository(): DataHealthRepository = JooqDataHealthRepository()

// The space is inlined: GROUP BY matches the selected expression only when both render the same SQL, and two
// bind markers are two different parameters.
private val NORMALIZED_NAME: Field<String?> =
    DSL.lower(DSL.trim(GOLFERS.FIRST_NAME)).concat(DSL.inline(" "), DSL.lower(DSL.trim(GOLFERS.LAST_NAME)))

private val HAS_FIRST_NAME: Condition = DSL.trim(GOLFERS.FIRST_NAME).ne("")

// Per league, not CWF-41's one global latest season, which ignores every other league's current roster.
private val CURRENTLY_ROSTERED: Condition =
    GOLFERS.ID.`in`(
        DSL.select(TEAM_ROSTERS.GOLFER_ID)
            .from(TEAM_ROSTERS)
            .join(TEAMS).on(TEAMS.ID.eq(TEAM_ROSTERS.TEAM_ID))
            .where(
                TEAMS.SEASON_ID.`in`(
                    DSL.selectDistinct(SEASONS.ID).on(SEASONS.LEAGUE_ID)
                        .from(SEASONS)
                        .orderBy(
                            SEASONS.LEAGUE_ID,
                            SEASONS.SEASON_YEAR.desc(),
                            SEASONS.SEASON_NUMBER.desc(),
                            SEASONS.CREATED_AT.desc(),
                        ),
                ),
            ),
    )

private class JooqDataHealthRepository : DataHealthRepository {
    context(ctx: TransactionContext)
    override fun findRosteredTwins(): List<RosteredTwinGroup> {
        val twinNames =
            DSL.select(NORMALIZED_NAME)
                .from(GOLFERS)
                .where(HAS_FIRST_NAME)
                .groupBy(NORMALIZED_NAME)
                .having(DSL.boolOr(CURRENTLY_ROSTERED.and(GOLFERS.PGA_PLAYER_ID.isNull)))
                .and(DSL.boolOr(GOLFERS.PGA_PLAYER_ID.isNotNull))
        return ctx.dsl.select(listOf(NORMALIZED_NAME) + GOLFERS.fields())
            .from(GOLFERS)
            .where(NORMALIZED_NAME.`in`(twinNames))
            .and(HAS_FIRST_NAME)
            .and(GOLFERS.PGA_PLAYER_ID.isNotNull.or(CURRENTLY_ROSTERED))
            .orderBy(NORMALIZED_NAME, GOLFERS.ID)
            .fetch { record ->
                val name = checkNotNull(record[NORMALIZED_NAME]) { "a name built from NOT NULL columns was null" }
                name to toDataHealthGolfer(record.into(GOLFERS))
            }
            .groupBy(keySelector = { (name, _) -> name }, valueTransform = { (_, golfer) -> golfer })
            .map { (name, golfers) ->
                val (rosteredWithoutEspnId, holdingEspnId) = golfers.partition { golfer -> golfer.pgaPlayerId == null }
                RosteredTwinGroup(name, rosteredWithoutEspnId, holdingEspnId)
            }
    }

    context(ctx: TransactionContext)
    override fun findRosteredWithoutEspnId(): List<DataHealthGolfer> =
        findWhere(CURRENTLY_ROSTERED.and(GOLFERS.PGA_PLAYER_ID.isNull))

    context(ctx: TransactionContext)
    override fun findBlankFirstNames(): List<DataHealthGolfer> =
        findWhere(HAS_FIRST_NAME.not().and(GOLFERS.TOMBSTONED_AT.isNull))

    context(ctx: TransactionContext)
    private fun findWhere(condition: Condition): List<DataHealthGolfer> =
        ctx.dsl.selectFrom(GOLFERS)
            .where(condition)
            .orderBy(GOLFERS.LAST_NAME, GOLFERS.FIRST_NAME, GOLFERS.ID)
            .fetch(::toDataHealthGolfer)

    private fun toDataHealthGolfer(record: GolfersRecord): DataHealthGolfer =
        DataHealthGolfer(
            id = GolferId(record.required(GOLFERS.ID)),
            firstName = record.firstName,
            lastName = record.lastName,
            pgaPlayerId = record.pgaPlayerId,
        )
}
