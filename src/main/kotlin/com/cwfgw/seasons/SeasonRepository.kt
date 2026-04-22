package com.cwfgw.seasons

import com.cwfgw.jooq.tables.records.SeasonsRecord
import com.cwfgw.jooq.tables.references.SEASONS
import com.cwfgw.jooq.tables.references.SEASON_RULE_PAYOUTS
import com.cwfgw.jooq.tables.references.SEASON_RULE_SIDE_BET_ROUNDS
import com.cwfgw.leagues.LeagueId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.Condition
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL

interface SeasonRepository {
    suspend fun findAll(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): List<Season>

    suspend fun findById(id: SeasonId): Season?

    suspend fun create(request: CreateSeasonRequest): Season

    suspend fun update(
        id: SeasonId,
        request: UpdateSeasonRequest,
    ): Season?

    suspend fun getRules(id: SeasonId): SeasonRules?
}

fun SeasonRepository(dsl: DSLContext): SeasonRepository = JooqSeasonRepository(dsl)

private class JooqSeasonRepository(private val dsl: DSLContext) : SeasonRepository {
    override suspend fun findAll(
        leagueId: LeagueId?,
        seasonYear: Int?,
    ): List<Season> =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(SEASONS)
                .where(filterConditions(leagueId, seasonYear))
                .orderBy(
                    SEASONS.SEASON_YEAR.desc(),
                    SEASONS.SEASON_NUMBER.desc(),
                    SEASONS.CREATED_AT.desc(),
                )
                .fetch(::toSeason)
        }

    override suspend fun findById(id: SeasonId): Season? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(SEASONS)
                .where(SEASONS.ID.eq(id.value))
                .fetchOne()
                ?.let(::toSeason)
        }

    override suspend fun create(request: CreateSeasonRequest): Season =
        withContext(Dispatchers.IO) {
            val values = insertAssignments(request)
            val inserted =
                dsl.insertInto(SEASONS)
                    .set(values)
                    .returning()
                    .fetchOne() ?: error("INSERT RETURNING produced no row for seasons")
            toSeason(inserted)
        }

    override suspend fun update(
        id: SeasonId,
        request: UpdateSeasonRequest,
    ): Season? =
        withContext(Dispatchers.IO) {
            val changes = updateAssignments(request)
            if (changes.isEmpty()) {
                dsl.selectFrom(SEASONS)
                    .where(SEASONS.ID.eq(id.value))
                    .fetchOne()
                    ?.let(::toSeason)
            } else {
                dsl.update(SEASONS)
                    .set(changes + (SEASONS.UPDATED_AT to DSL.currentOffsetDateTime()))
                    .where(SEASONS.ID.eq(id.value))
                    .returning()
                    .fetchOne()
                    ?.let(::toSeason)
            }
        }

    override suspend fun getRules(id: SeasonId): SeasonRules? =
        withContext(Dispatchers.IO) {
            val season =
                dsl.selectFrom(SEASONS)
                    .where(SEASONS.ID.eq(id.value))
                    .fetchOne() ?: return@withContext null

            val payouts =
                dsl.select(SEASON_RULE_PAYOUTS.AMOUNT)
                    .from(SEASON_RULE_PAYOUTS)
                    .where(SEASON_RULE_PAYOUTS.SEASON_ID.eq(id.value))
                    .orderBy(SEASON_RULE_PAYOUTS.POSITION.asc())
                    .fetch { record ->
                        checkNotNull(record[SEASON_RULE_PAYOUTS.AMOUNT]) {
                            "season_rule_payouts.amount is NOT NULL but returned null"
                        }
                    }

            val sideBetRounds =
                dsl.select(SEASON_RULE_SIDE_BET_ROUNDS.ROUND)
                    .from(SEASON_RULE_SIDE_BET_ROUNDS)
                    .where(SEASON_RULE_SIDE_BET_ROUNDS.SEASON_ID.eq(id.value))
                    .orderBy(SEASON_RULE_SIDE_BET_ROUNDS.ROUND.asc())
                    .fetch { record ->
                        checkNotNull(record[SEASON_RULE_SIDE_BET_ROUNDS.ROUND]) {
                            "season_rule_side_bet_rounds.round is NOT NULL but returned null"
                        }
                    }

            SeasonRules(
                payouts = payouts.ifEmpty { SeasonRules.DEFAULT_PAYOUTS },
                tieFloor =
                    checkNotNull(season.tieFloor) {
                        "seasons.tie_floor is NOT NULL but returned null"
                    },
                sideBetRounds = sideBetRounds.ifEmpty { SeasonRules.DEFAULT_SIDE_BET_ROUNDS },
                sideBetAmount =
                    checkNotNull(season.sideBetAmount) {
                        "seasons.side_bet_amount is NOT NULL but returned null"
                    },
            )
        }

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

    private fun insertAssignments(request: CreateSeasonRequest): Map<Field<*>, Any?> =
        buildMap {
            put(SEASONS.LEAGUE_ID, request.leagueId.value)
            put(SEASONS.NAME, request.name)
            put(SEASONS.SEASON_YEAR, request.seasonYear)
            request.seasonNumber?.let { put(SEASONS.SEASON_NUMBER, it) }
            request.maxTeams?.let { put(SEASONS.MAX_TEAMS, it) }
            request.tieFloor?.let { put(SEASONS.TIE_FLOOR, it) }
            request.sideBetAmount?.let { put(SEASONS.SIDE_BET_AMOUNT, it) }
        }

    private fun updateAssignments(request: UpdateSeasonRequest): Map<Field<*>, Any?> =
        buildMap {
            request.name?.let { put(SEASONS.NAME, it) }
            request.status?.let { put(SEASONS.STATUS, it) }
            request.maxTeams?.let { put(SEASONS.MAX_TEAMS, it) }
            request.tieFloor?.let { put(SEASONS.TIE_FLOOR, it) }
            request.sideBetAmount?.let { put(SEASONS.SIDE_BET_AMOUNT, it) }
        }

    private fun toSeason(record: SeasonsRecord): Season =
        Season(
            id = SeasonId(checkNotNull(record.id) { "seasons.id is NOT NULL but returned null" }),
            leagueId = LeagueId(record.leagueId),
            name = record.name,
            seasonYear = record.seasonYear,
            seasonNumber =
                checkNotNull(record.seasonNumber) {
                    "seasons.season_number is NOT NULL but returned null"
                },
            status = checkNotNull(record.status) { "seasons.status is NOT NULL but returned null" },
            tieFloor =
                checkNotNull(record.tieFloor) {
                    "seasons.tie_floor is NOT NULL but returned null"
                },
            sideBetAmount =
                checkNotNull(record.sideBetAmount) {
                    "seasons.side_bet_amount is NOT NULL but returned null"
                },
            maxTeams =
                checkNotNull(record.maxTeams) {
                    "seasons.max_teams is NOT NULL but returned null"
                },
            createdAt =
                checkNotNull(record.createdAt) {
                    "seasons.created_at is NOT NULL but returned null"
                }.toInstant(),
            updatedAt =
                checkNotNull(record.updatedAt) {
                    "seasons.updated_at is NOT NULL but returned null"
                }.toInstant(),
        )
}
