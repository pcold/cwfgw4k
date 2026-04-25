package com.cwfgw.tournaments

import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.records.TournamentResultsRecord
import com.cwfgw.jooq.tables.records.TournamentsRecord
import com.cwfgw.jooq.tables.references.TOURNAMENTS
import com.cwfgw.jooq.tables.references.TOURNAMENT_RESULTS
import com.cwfgw.seasons.SeasonId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Field

interface TournamentRepository {
    suspend fun findAll(
        seasonId: SeasonId?,
        status: TournamentStatus?,
    ): List<Tournament>

    suspend fun findById(id: TournamentId): Tournament?

    suspend fun findByPgaTournamentId(pgaTournamentId: String): Tournament?

    suspend fun create(request: CreateTournamentRequest): Tournament

    suspend fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament?

    suspend fun getResults(tournamentId: TournamentId): List<TournamentResult>

    suspend fun upsertResult(
        tournamentId: TournamentId,
        request: CreateTournamentResultRequest,
    ): TournamentResult

    /** Wipe every leaderboard row for the given tournament. Returns the row count for logging. */
    suspend fun deleteResultsByTournament(tournamentId: TournamentId): Int

    /** Wipe every leaderboard row for every tournament in the given season. */
    suspend fun deleteResultsBySeason(seasonId: SeasonId): Int

    /** Reset every tournament in the season to status `Upcoming`. Returns the row count for logging. */
    suspend fun resetSeasonTournaments(seasonId: SeasonId): Int
}

fun TournamentRepository(dsl: DSLContext): TournamentRepository = JooqTournamentRepository(dsl)

private class JooqTournamentRepository(private val dsl: DSLContext) : TournamentRepository {
    override suspend fun findAll(
        seasonId: SeasonId?,
        status: TournamentStatus?,
    ): List<Tournament> =
        withContext(Dispatchers.IO) {
            val conditions =
                buildList {
                    seasonId?.let { add(TOURNAMENTS.SEASON_ID.eq(it.value)) }
                    status?.let { add(TOURNAMENTS.STATUS.eq(it.value)) }
                }
            dsl.selectFrom(TOURNAMENTS)
                .where(conditions)
                .orderBy(TOURNAMENTS.START_DATE.asc(), TOURNAMENTS.CREATED_AT.asc())
                .fetch(::toTournament)
        }

    override suspend fun findById(id: TournamentId): Tournament? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(TOURNAMENTS)
                .where(TOURNAMENTS.ID.eq(id.value))
                .fetchOne()
                ?.let(::toTournament)
        }

    override suspend fun findByPgaTournamentId(pgaTournamentId: String): Tournament? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(TOURNAMENTS)
                .where(TOURNAMENTS.PGA_TOURNAMENT_ID.eq(pgaTournamentId))
                .fetchOne()
                ?.let(::toTournament)
        }

    override suspend fun create(request: CreateTournamentRequest): Tournament =
        withContext(Dispatchers.IO) {
            val inserted =
                dsl.insertInto(TOURNAMENTS)
                    .set(insertAssignments(request))
                    .returning()
                    .fetchOne() ?: error("INSERT RETURNING produced no row for tournaments")
            toTournament(inserted)
        }

    override suspend fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? =
        withContext(Dispatchers.IO) {
            val changes = updateAssignments(request)
            if (changes.isEmpty()) {
                dsl.selectFrom(TOURNAMENTS)
                    .where(TOURNAMENTS.ID.eq(id.value))
                    .fetchOne()
                    ?.let(::toTournament)
            } else {
                dsl.update(TOURNAMENTS)
                    .set(changes)
                    .where(TOURNAMENTS.ID.eq(id.value))
                    .returning()
                    .fetchOne()
                    ?.let(::toTournament)
            }
        }

    override suspend fun getResults(tournamentId: TournamentId): List<TournamentResult> =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(TOURNAMENT_RESULTS)
                .where(TOURNAMENT_RESULTS.TOURNAMENT_ID.eq(tournamentId.value))
                .orderBy(TOURNAMENT_RESULTS.POSITION.asc().nullsLast(), TOURNAMENT_RESULTS.ID.asc())
                .fetch(::toResult)
        }

    override suspend fun deleteResultsByTournament(tournamentId: TournamentId): Int =
        withContext(Dispatchers.IO) {
            dsl.deleteFrom(TOURNAMENT_RESULTS)
                .where(TOURNAMENT_RESULTS.TOURNAMENT_ID.eq(tournamentId.value))
                .execute()
        }

    override suspend fun deleteResultsBySeason(seasonId: SeasonId): Int =
        withContext(Dispatchers.IO) {
            val tournamentIds =
                dsl.select(TOURNAMENTS.ID)
                    .from(TOURNAMENTS)
                    .where(TOURNAMENTS.SEASON_ID.eq(seasonId.value))
                    .fetch(TOURNAMENTS.ID)
            if (tournamentIds.isEmpty()) {
                0
            } else {
                dsl.deleteFrom(TOURNAMENT_RESULTS)
                    .where(TOURNAMENT_RESULTS.TOURNAMENT_ID.`in`(tournamentIds))
                    .execute()
            }
        }

    override suspend fun resetSeasonTournaments(seasonId: SeasonId): Int =
        withContext(Dispatchers.IO) {
            dsl.update(TOURNAMENTS)
                .set(TOURNAMENTS.STATUS, TournamentStatus.Upcoming.value)
                .where(TOURNAMENTS.SEASON_ID.eq(seasonId.value))
                .execute()
        }

    override suspend fun upsertResult(
        tournamentId: TournamentId,
        request: CreateTournamentResultRequest,
    ): TournamentResult =
        withContext(Dispatchers.IO) {
            val upserted =
                dsl.insertInto(TOURNAMENT_RESULTS)
                    .set(resultInsertAssignments(tournamentId, request))
                    .onConflict(TOURNAMENT_RESULTS.TOURNAMENT_ID, TOURNAMENT_RESULTS.GOLFER_ID)
                    .doUpdate()
                    .set(resultUpdateAssignments(request))
                    .returning()
                    .fetchOne() ?: error("UPSERT RETURNING produced no row for tournament_results")
            toResult(upserted)
        }

    private fun insertAssignments(request: CreateTournamentRequest): Map<Field<*>, Any?> =
        buildMap {
            put(TOURNAMENTS.NAME, request.name)
            put(TOURNAMENTS.SEASON_ID, request.seasonId.value)
            put(TOURNAMENTS.START_DATE, request.startDate)
            put(TOURNAMENTS.END_DATE, request.endDate)
            request.pgaTournamentId?.let { put(TOURNAMENTS.PGA_TOURNAMENT_ID, it) }
            request.courseName?.let { put(TOURNAMENTS.COURSE_NAME, it) }
            request.purseAmount?.let { put(TOURNAMENTS.PURSE_AMOUNT, it) }
            request.payoutMultiplier?.let { put(TOURNAMENTS.PAYOUT_MULTIPLIER, it) }
            request.week?.let { put(TOURNAMENTS.WEEK, it) }
        }

    private fun updateAssignments(request: UpdateTournamentRequest): Map<Field<*>, Any?> =
        buildMap {
            request.name?.let { put(TOURNAMENTS.NAME, it) }
            request.startDate?.let { put(TOURNAMENTS.START_DATE, it) }
            request.endDate?.let { put(TOURNAMENTS.END_DATE, it) }
            request.courseName?.let { put(TOURNAMENTS.COURSE_NAME, it) }
            request.status?.let { put(TOURNAMENTS.STATUS, it.value) }
            request.purseAmount?.let { put(TOURNAMENTS.PURSE_AMOUNT, it) }
            request.payoutMultiplier?.let { put(TOURNAMENTS.PAYOUT_MULTIPLIER, it) }
            request.isTeamEvent?.let { put(TOURNAMENTS.IS_TEAM_EVENT, it) }
        }

    private fun resultInsertAssignments(
        tournamentId: TournamentId,
        request: CreateTournamentResultRequest,
    ): Map<Field<*>, Any?> =
        mapOf(
            TOURNAMENT_RESULTS.TOURNAMENT_ID to tournamentId.value,
            TOURNAMENT_RESULTS.GOLFER_ID to request.golferId.value,
            TOURNAMENT_RESULTS.POSITION to request.position,
            TOURNAMENT_RESULTS.SCORE_TO_PAR to request.scoreToPar,
            TOURNAMENT_RESULTS.TOTAL_STROKES to request.totalStrokes,
            TOURNAMENT_RESULTS.EARNINGS to request.earnings,
            TOURNAMENT_RESULTS.ROUND1 to request.round1,
            TOURNAMENT_RESULTS.ROUND2 to request.round2,
            TOURNAMENT_RESULTS.ROUND3 to request.round3,
            TOURNAMENT_RESULTS.ROUND4 to request.round4,
            TOURNAMENT_RESULTS.MADE_CUT to request.madeCut,
            TOURNAMENT_RESULTS.PAIR_KEY to request.pairKey,
        )

    private fun resultUpdateAssignments(request: CreateTournamentResultRequest): Map<Field<*>, Any?> =
        mapOf(
            TOURNAMENT_RESULTS.POSITION to request.position,
            TOURNAMENT_RESULTS.SCORE_TO_PAR to request.scoreToPar,
            TOURNAMENT_RESULTS.TOTAL_STROKES to request.totalStrokes,
            TOURNAMENT_RESULTS.EARNINGS to request.earnings,
            TOURNAMENT_RESULTS.ROUND1 to request.round1,
            TOURNAMENT_RESULTS.ROUND2 to request.round2,
            TOURNAMENT_RESULTS.ROUND3 to request.round3,
            TOURNAMENT_RESULTS.ROUND4 to request.round4,
            TOURNAMENT_RESULTS.MADE_CUT to request.madeCut,
            TOURNAMENT_RESULTS.PAIR_KEY to request.pairKey,
        )

    private fun toTournament(record: TournamentsRecord): Tournament =
        Tournament(
            id = TournamentId(checkNotNull(record.id) { "tournaments.id is NOT NULL but returned null" }),
            pgaTournamentId = record.pgaTournamentId,
            name = record.name,
            seasonId = SeasonId(record.seasonId),
            startDate = record.startDate,
            endDate = record.endDate,
            courseName = record.courseName,
            status =
                run {
                    val raw = checkNotNull(record.status) { "tournaments.status is NOT NULL but returned null" }
                    requireNotNull(TournamentStatus.fromValueOrNull(raw)) {
                        "tournaments.status held unrecognized value '$raw' — schema and code disagree"
                    }
                },
            purseAmount = record.purseAmount,
            payoutMultiplier =
                checkNotNull(record.payoutMultiplier) {
                    "tournaments.payout_multiplier is NOT NULL but returned null"
                },
            week = record.week,
            isTeamEvent =
                checkNotNull(record.isTeamEvent) {
                    "tournaments.is_team_event is NOT NULL but returned null"
                },
            createdAt =
                checkNotNull(record.createdAt) {
                    "tournaments.created_at is NOT NULL but returned null"
                }.toInstant(),
        )

    private fun toResult(record: TournamentResultsRecord): TournamentResult =
        TournamentResult(
            id = TournamentResultId(checkNotNull(record.id) { "tournament_results.id is NOT NULL but returned null" }),
            tournamentId = TournamentId(record.tournamentId),
            golferId = GolferId(record.golferId),
            position = record.position,
            scoreToPar = record.scoreToPar,
            totalStrokes = record.totalStrokes,
            earnings = record.earnings,
            round1 = record.round1,
            round2 = record.round2,
            round3 = record.round3,
            round4 = record.round4,
            madeCut =
                checkNotNull(record.madeCut) {
                    "tournament_results.made_cut is NOT NULL but returned null"
                },
            pairKey = record.pairKey,
        )
}
