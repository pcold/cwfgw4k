package com.cwfgw.seasons

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.db.required
import com.cwfgw.jooq.tables.records.SeasonsRecord
import com.cwfgw.jooq.tables.references.SEASONS
import com.cwfgw.jooq.tables.references.SEASON_RULE_PAYOUTS
import com.cwfgw.jooq.tables.references.SEASON_RULE_SIDE_BET_ROUNDS
import com.cwfgw.leagues.LeagueId
import org.jooq.Condition
import org.jooq.Field
import org.jooq.impl.DSL

interface SeasonRepository {
    context(ctx: TransactionContext)
    fun findAll(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): List<Season>

    context(ctx: TransactionContext)
    fun findById(id: SeasonId): Season?

    context(ctx: WriteTransactionContext)
    fun create(request: CreateSeasonRequest): Season

    context(ctx: TransactionContext)
    fun getRules(id: SeasonId): SeasonRules?

    /**
     * Single SQL DELETE: the subtree goes by ON DELETE CASCADE FKs (V010's and the tables' own), so a new
     * season-owned table needs one too. False when the id didn't exist.
     */
    context(ctx: WriteTransactionContext)
    fun delete(id: SeasonId): Boolean
}

fun SeasonRepository(): SeasonRepository = JooqSeasonRepository()

private class JooqSeasonRepository : SeasonRepository {
    context(ctx: TransactionContext)
    override fun findAll(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): List<Season> =
        ctx.dsl.selectFrom(SEASONS)
            .where(filterConditions(leagueId, seasonYear))
            .orderBy(
                SEASONS.SEASON_YEAR.desc(),
                SEASONS.SEASON_NUMBER.desc(),
                SEASONS.CREATED_AT.desc(),
            )
            .fetch(::toSeason)

    context(ctx: TransactionContext)
    override fun findById(id: SeasonId): Season? =
        ctx.dsl.selectFrom(SEASONS)
            .where(SEASONS.ID.eq(id.value))
            .fetchOne()
            ?.let(::toSeason)

    // The seasons-row insert plus per-position payouts and per-round side-bet
    // rounds need to be atomic. Callers reach this through tx.update { }, so
    // ctx.dsl is already transactional — every statement here joins that
    // outer transaction without a nested transactionResult.
    context(ctx: WriteTransactionContext)
    override fun create(request: CreateSeasonRequest): Season {
        val seasonNumber = request.seasonNumber ?: nextSeasonNumber(request.leagueId, request.seasonYear)
        val values = insertAssignments(request, seasonNumber)
        val inserted =
            ctx.dsl.insertInto(SEASONS)
                .set(values)
                .returning()
                .fetchSingle()
        val season = toSeason(inserted)
        request.rules?.let { writeRules(season.id, it) }
        return season
    }

    // Auto-increments within a league + year so callers (the admin UI in
    // particular) don't need to know the highest existing season_number.
    // Falls back to 1 when this is the first season for that league + year.
    context(ctx: TransactionContext)
    private fun nextSeasonNumber(
        leagueId: LeagueId,
        seasonYear: Int,
    ): Int {
        val highestExisting =
            ctx.dsl.select(DSL.max(SEASONS.SEASON_NUMBER))
                .from(SEASONS)
                .where(SEASONS.LEAGUE_ID.eq(leagueId.value))
                .and(SEASONS.SEASON_YEAR.eq(seasonYear))
                .fetchOne(0, Int::class.java)
        return (highestExisting ?: 0) + 1
    }

    context(ctx: WriteTransactionContext)
    private fun writeRules(
        seasonId: SeasonId,
        rules: SeasonRules,
    ) {
        rules.payouts.forEachIndexed { index, amount ->
            ctx.dsl.insertInto(SEASON_RULE_PAYOUTS)
                .set(SEASON_RULE_PAYOUTS.SEASON_ID, seasonId.value)
                .set(SEASON_RULE_PAYOUTS.POSITION, index + 1)
                .set(SEASON_RULE_PAYOUTS.AMOUNT, amount)
                .execute()
        }
        rules.sideBetRounds.forEach { round ->
            ctx.dsl.insertInto(SEASON_RULE_SIDE_BET_ROUNDS)
                .set(SEASON_RULE_SIDE_BET_ROUNDS.SEASON_ID, seasonId.value)
                .set(SEASON_RULE_SIDE_BET_ROUNDS.ROUND, round)
                .execute()
        }
    }

    context(ctx: TransactionContext)
    override fun getRules(id: SeasonId): SeasonRules? {
        val season =
            ctx.dsl.selectFrom(SEASONS)
                .where(SEASONS.ID.eq(id.value))
                .fetchOne() ?: return null

        val payouts =
            ctx.dsl.select(SEASON_RULE_PAYOUTS.AMOUNT)
                .from(SEASON_RULE_PAYOUTS)
                .where(SEASON_RULE_PAYOUTS.SEASON_ID.eq(id.value))
                .orderBy(SEASON_RULE_PAYOUTS.POSITION.asc())
                .fetch { record ->
                    checkNotNull(record[SEASON_RULE_PAYOUTS.AMOUNT]) {
                        "season_rule_payouts.amount is NOT NULL but returned null"
                    }
                }

        val sideBetRounds =
            ctx.dsl.select(SEASON_RULE_SIDE_BET_ROUNDS.ROUND)
                .from(SEASON_RULE_SIDE_BET_ROUNDS)
                .where(SEASON_RULE_SIDE_BET_ROUNDS.SEASON_ID.eq(id.value))
                .orderBy(SEASON_RULE_SIDE_BET_ROUNDS.ROUND.asc())
                .fetch { record ->
                    checkNotNull(record[SEASON_RULE_SIDE_BET_ROUNDS.ROUND]) {
                        "season_rule_side_bet_rounds.round is NOT NULL but returned null"
                    }
                }

        return SeasonRules(
            payouts = payouts.ifEmpty { SeasonRules.DEFAULT_PAYOUTS },
            tieFloor = season.required(SEASONS.TIE_FLOOR),
            sideBetRounds = sideBetRounds.ifEmpty { SeasonRules.DEFAULT_SIDE_BET_ROUNDS },
            sideBetAmount = season.required(SEASONS.SIDE_BET_AMOUNT),
        )
    }

    context(ctx: WriteTransactionContext)
    override fun delete(id: SeasonId): Boolean =
        ctx.dsl.deleteFrom(SEASONS).where(SEASONS.ID.eq(id.value)).execute() > 0

    private fun filterConditions(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): Condition {
        val conditions =
            buildList {
                leagueId?.let { add(SEASONS.LEAGUE_ID.eq(it.value)) }
                seasonYear?.let { add(SEASONS.SEASON_YEAR.eq(it)) }
            }
        return conditions.reduceOrNull(Condition::and) ?: DSL.noCondition()
    }

    private fun insertAssignments(
        request: CreateSeasonRequest,
        seasonNumber: Int,
    ): Map<Field<*>, Any?> =
        buildMap {
            put(SEASONS.LEAGUE_ID, request.leagueId.value)
            put(SEASONS.NAME, request.name)
            put(SEASONS.SEASON_YEAR, request.seasonYear)
            put(SEASONS.SEASON_NUMBER, seasonNumber)
            request.maxTeams?.let { put(SEASONS.MAX_TEAMS, it) }
            request.rules?.let { rules ->
                put(SEASONS.TIE_FLOOR, rules.tieFloor)
                put(SEASONS.SIDE_BET_AMOUNT, rules.sideBetAmount)
            }
        }

    private fun toSeason(record: SeasonsRecord): Season =
        Season(
            id = SeasonId(record.required(SEASONS.ID)),
            leagueId = LeagueId(record.leagueId),
            name = record.name,
            seasonYear = record.seasonYear,
            seasonNumber = record.seasonNumber,
            tieFloor = record.required(SEASONS.TIE_FLOOR),
            sideBetAmount = record.required(SEASONS.SIDE_BET_AMOUNT),
            maxTeams = record.required(SEASONS.MAX_TEAMS),
            createdAt = record.required(SEASONS.CREATED_AT).toInstant(),
            updatedAt = record.required(SEASONS.UPDATED_AT).toInstant(),
        )
}
