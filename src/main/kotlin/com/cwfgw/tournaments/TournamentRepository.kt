package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.db.decoded
import com.cwfgw.db.required
import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.keys.TOURNAMENTS_PGA_TOURNAMENT_ID_KEY
import com.cwfgw.jooq.tables.records.TournamentResultsRecord
import com.cwfgw.jooq.tables.records.TournamentsRecord
import com.cwfgw.jooq.tables.references.SEASONS
import com.cwfgw.jooq.tables.references.TOURNAMENTS
import com.cwfgw.jooq.tables.references.TOURNAMENT_RESULTS
import com.cwfgw.seasons.SeasonId
import org.jooq.Field
import org.jooq.JSONB
import org.jooq.Table
import org.jooq.impl.DSL

interface TournamentRepository {
    /**
     * Ordered by start date then week, as the reports' `tournamentOrdering` is, so a same-date 8a precedes 8b;
     * then creation, then id, since one transaction's inserts share a `created_at`.
     */
    context(ctx: TransactionContext)
    fun findAll(filter: TournamentFilter): List<Tournament>

    context(ctx: TransactionContext)
    fun findById(id: TournamentId): Tournament?

    /**
     * Locks the season row before the tournament, the order a season delete's cascade takes both. NO KEY UPDATE runs
     * one finalize, reset, status or start-date PUT, season clean-results, or schedule or roster confirm per season at
     * a time, so each sees the others' sibling writes. An FK insert's key-share stays free, so a tournament create
     * does not wait.
     */
    context(ctx: WriteTransactionContext)
    fun findByIdForUpdate(id: TournamentId): Tournament?

    /** @see findByIdForUpdate */
    context(ctx: WriteTransactionContext)
    fun lockSeason(seasonId: SeasonId)

    context(ctx: TransactionContext)
    fun findByPgaTournamentId(pgaTournamentId: String): Tournament?

    /**
     * Takes no lock, so a read-only caller can; a write gating on the rows takes [findByPgaTournamentIdsForUpdate],
     * unless it gates only on a row's absence, which no lock holds and the unique key refuses instead.
     */
    context(ctx: TransactionContext)
    fun findByPgaTournamentIds(pgaTournamentIds: Collection<String>): List<Tournament>

    /**
     * Locks in id order: an id never changes, where a start date can between two lockers' reads. Their seasons go
     * unlocked, since a NO KEY UPDATE on them deadlocks two unlinked finalizes matching into each other's season.
     */
    context(ctx: WriteTransactionContext)
    fun findByPgaTournamentIdsForUpdate(pgaTournamentIds: Collection<String>): List<Tournament>

    context(ctx: WriteTransactionContext)
    fun create(request: NewTournament): Tournament

    context(ctx: WriteTransactionContext)
    fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament?

    context(ctx: TransactionContext)
    fun getResults(tournamentId: TournamentId): List<TournamentResult>

    /**
     * Single-query alternative to `tournaments.flatMap { getResults(it.id) }`.
     * Returns every leaderboard row for the supplied tournament ids in one
     * query. Empty input returns an empty list. Ordered by
     * `(tournament_id, position nulls last, id)` so `groupBy { it.tournamentId }`
     * at the call site preserves per-tournament order.
     */
    context(ctx: TransactionContext)
    fun getResultsByTournaments(ids: Collection<TournamentId>): List<TournamentResult>

    /** The holes [getResults] and [getResultsByTournaments] leave out. */
    context(ctx: TransactionContext)
    fun getScorecards(tournamentId: TournamentId): Map<GolferId, List<RoundScorecard>>

    /**
     * Placed finishes at or above [positionAtMost] only, ordered by tournament id. The tie count is taken over the
     * whole field before narrowing to [golferId]; counted after, every finish would read as untied.
     */
    context(ctx: TransactionContext)
    fun findResultsForGolfer(
        golferId: GolferId,
        tournamentIds: Collection<TournamentId>,
        positionAtMost: Int,
    ): List<GolferFinish>

    context(ctx: WriteTransactionContext)
    fun upsertResult(
        tournamentId: TournamentId,
        request: NewTournamentResult,
    ): TournamentResult

    /** Wipe every leaderboard row for the given tournament. Returns the row count for logging. */
    context(ctx: WriteTransactionContext)
    fun deleteResultsByTournament(tournamentId: TournamentId): Int

    /** Wipe every leaderboard row for every tournament in the given season. */
    context(ctx: WriteTransactionContext)
    fun deleteResultsBySeason(seasonId: SeasonId): Int

    /** Reset every tournament in the season to status `Upcoming`. Returns the row count for logging. */
    context(ctx: WriteTransactionContext)
    fun resetSeasonTournaments(seasonId: SeasonId): Int
}

fun TournamentRepository(): TournamentRepository = JooqTournamentRepository()

internal val UNIQUE_PGA_TOURNAMENT_ID: String = TOURNAMENTS_PGA_TOURNAMENT_ID_KEY.name

/** Without scorecards: a season's results would carry roughly 10MB of holes, which only one leaderboard reads. */
private val RESULT_FIELDS = TOURNAMENT_RESULTS.fields().filterNot { it == TOURNAMENT_RESULTS.SCORECARDS }

private class JooqTournamentRepository : TournamentRepository {
    context(ctx: TransactionContext)
    override fun findAll(filter: TournamentFilter): List<Tournament> {
        val conditions =
            buildList {
                filter.seasonId?.let { add(TOURNAMENTS.SEASON_ID.eq(it.value)) }
                // The full set adds no clause, so a status the CHECK admits but the enum lacks fails decoding rather
                // than silently dropping out of every unfiltered read.
                if (!filter.statuses.containsAll(TournamentStatus.entries)) {
                    add(TOURNAMENTS.STATUS.`in`(filter.statuses.map(TournamentStatus::value)))
                }
                filter.startsBefore?.let { add(TOURNAMENTS.START_DATE.lt(it)) }
                filter.startsAfter?.let { add(TOURNAMENTS.START_DATE.gt(it)) }
                filter.startsOn?.let { add(TOURNAMENTS.START_DATE.eq(it)) }
            }
        return ctx.dsl.selectFrom(TOURNAMENTS)
            .where(conditions)
            .orderBy(
                TOURNAMENTS.START_DATE.asc(),
                TOURNAMENTS.WEEK.asc().nullsFirst(),
                TOURNAMENTS.CREATED_AT.asc(),
                TOURNAMENTS.ID.asc(),
            )
            .fetch(::toTournament)
    }

    context(ctx: TransactionContext)
    override fun findById(id: TournamentId): Tournament? =
        ctx.dsl.selectFrom(TOURNAMENTS)
            .where(TOURNAMENTS.ID.eq(id.value))
            .fetchOne()
            ?.let(::toTournament)

    context(ctx: WriteTransactionContext)
    override fun findByIdForUpdate(id: TournamentId): Tournament? {
        val seasonId =
            ctx.dsl.select(TOURNAMENTS.SEASON_ID)
                .from(TOURNAMENTS)
                .where(TOURNAMENTS.ID.eq(id.value))
                .fetchOne(TOURNAMENTS.SEASON_ID)
                ?: return null
        lockSeason(SeasonId(seasonId))
        return ctx.dsl.selectFrom(TOURNAMENTS)
            .where(TOURNAMENTS.ID.eq(id.value))
            .forUpdate()
            .fetchOne()
            ?.let(::toTournament)
    }

    context(ctx: WriteTransactionContext)
    override fun lockSeason(seasonId: SeasonId) {
        ctx.dsl.selectOne().from(SEASONS).where(SEASONS.ID.eq(seasonId.value)).forNoKeyUpdate().execute()
    }

    context(ctx: TransactionContext)
    override fun findByPgaTournamentId(pgaTournamentId: String): Tournament? =
        ctx.dsl.selectFrom(TOURNAMENTS)
            .where(TOURNAMENTS.PGA_TOURNAMENT_ID.eq(pgaTournamentId))
            .fetchOne()
            ?.let(::toTournament)

    context(ctx: TransactionContext)
    override fun findByPgaTournamentIds(pgaTournamentIds: Collection<String>): List<Tournament> {
        if (pgaTournamentIds.isEmpty()) return emptyList()
        return ctx.dsl.selectFrom(TOURNAMENTS)
            .where(TOURNAMENTS.PGA_TOURNAMENT_ID.`in`(pgaTournamentIds))
            .fetch(::toTournament)
    }

    context(ctx: WriteTransactionContext)
    override fun findByPgaTournamentIdsForUpdate(pgaTournamentIds: Collection<String>): List<Tournament> {
        if (pgaTournamentIds.isEmpty()) return emptyList()
        return ctx.dsl.selectFrom(TOURNAMENTS)
            .where(TOURNAMENTS.PGA_TOURNAMENT_ID.`in`(pgaTournamentIds))
            .orderBy(TOURNAMENTS.ID.asc())
            .forUpdate()
            .fetch(::toTournament)
    }

    context(ctx: WriteTransactionContext)
    override fun create(request: NewTournament): Tournament {
        val inserted =
            ctx.dsl.insertInto(TOURNAMENTS)
                .set(insertAssignments(request))
                .returning()
                .fetchSingle()
        return toTournament(inserted)
    }

    context(ctx: WriteTransactionContext)
    override fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? {
        val changes = updateAssignments(request)
        return if (changes.isEmpty()) {
            ctx.dsl.selectFrom(TOURNAMENTS)
                .where(TOURNAMENTS.ID.eq(id.value))
                .fetchOne()
                ?.let(::toTournament)
        } else {
            ctx.dsl.update(TOURNAMENTS)
                .set(changes)
                .where(TOURNAMENTS.ID.eq(id.value))
                .returning()
                .fetchOne()
                ?.let(::toTournament)
        }
    }

    context(ctx: TransactionContext)
    override fun getResults(tournamentId: TournamentId): List<TournamentResult> =
        ctx.dsl.select(RESULT_FIELDS)
            .from(TOURNAMENT_RESULTS)
            .where(TOURNAMENT_RESULTS.TOURNAMENT_ID.eq(tournamentId.value))
            .orderBy(TOURNAMENT_RESULTS.POSITION.asc().nullsLast(), TOURNAMENT_RESULTS.ID.asc())
            .fetchInto(TOURNAMENT_RESULTS)
            .map(::toResult)

    context(ctx: TransactionContext)
    override fun getResultsByTournaments(ids: Collection<TournamentId>): List<TournamentResult> {
        if (ids.isEmpty()) return emptyList()
        return ctx.dsl.select(RESULT_FIELDS)
            .from(TOURNAMENT_RESULTS)
            .where(TOURNAMENT_RESULTS.TOURNAMENT_ID.`in`(ids.map { it.value }))
            .orderBy(
                TOURNAMENT_RESULTS.TOURNAMENT_ID.asc(),
                TOURNAMENT_RESULTS.POSITION.asc().nullsLast(),
                TOURNAMENT_RESULTS.ID.asc(),
            )
            .fetchInto(TOURNAMENT_RESULTS)
            .map(::toResult)
    }

    context(ctx: TransactionContext)
    override fun getScorecards(tournamentId: TournamentId): Map<GolferId, List<RoundScorecard>> =
        ctx.dsl.select(TOURNAMENT_RESULTS.GOLFER_ID, TOURNAMENT_RESULTS.SCORECARDS)
            .from(TOURNAMENT_RESULTS)
            .where(TOURNAMENT_RESULTS.TOURNAMENT_ID.eq(tournamentId.value))
            .fetchInto(TOURNAMENT_RESULTS)
            .associate { record ->
                GolferId(record.golferId) to decodeScorecards(record.required(TOURNAMENT_RESULTS.SCORECARDS).data())
            }

    context(ctx: TransactionContext)
    override fun findResultsForGolfer(
        golferId: GolferId,
        tournamentIds: Collection<TournamentId>,
        positionAtMost: Int,
    ): List<GolferFinish> {
        if (tournamentIds.isEmpty()) return emptyList()
        val numTied =
            DSL.count()
                .over(DSL.partitionBy(TOURNAMENT_RESULTS.TOURNAMENT_ID, TOURNAMENT_RESULTS.POSITION))
                .`as`("num_tied")
        val leaderboard =
            ctx.dsl.select(TOURNAMENT_RESULTS.TOURNAMENT_ID, TOURNAMENT_RESULTS.GOLFER_ID, TOURNAMENT_RESULTS.POSITION)
                .select(numTied)
                .from(TOURNAMENT_RESULTS)
                .where(TOURNAMENT_RESULTS.TOURNAMENT_ID.`in`(tournamentIds.map { it.value }))
                .asTable("leaderboard")
        val tournamentId = leaderboard.column(TOURNAMENT_RESULTS.TOURNAMENT_ID)
        val position = leaderboard.column(TOURNAMENT_RESULTS.POSITION)
        val tiedAtPosition = leaderboard.column(numTied)
        return ctx.dsl.select(tournamentId, position, tiedAtPosition)
            .from(leaderboard)
            .where(leaderboard.column(TOURNAMENT_RESULTS.GOLFER_ID).eq(golferId.value))
            .and(position.le(positionAtMost))
            .orderBy(tournamentId.asc())
            .fetch { record ->
                // tournament_id is NOT NULL; position is nullable, but `position <= positionAtMost` drops its NULLs.
                GolferFinish(
                    tournamentId = TournamentId(checkNotNull(record[tournamentId])),
                    position = checkNotNull(record[position]),
                    numTied = record[tiedAtPosition],
                )
            }
    }

    context(ctx: WriteTransactionContext)
    override fun deleteResultsByTournament(tournamentId: TournamentId): Int =
        ctx.dsl.deleteFrom(TOURNAMENT_RESULTS)
            .where(TOURNAMENT_RESULTS.TOURNAMENT_ID.eq(tournamentId.value))
            .execute()

    context(ctx: WriteTransactionContext)
    override fun deleteResultsBySeason(seasonId: SeasonId): Int {
        val tournamentIds =
            ctx.dsl.select(TOURNAMENTS.ID)
                .from(TOURNAMENTS)
                .where(TOURNAMENTS.SEASON_ID.eq(seasonId.value))
                .fetch(TOURNAMENTS.ID)
        return if (tournamentIds.isEmpty()) {
            0
        } else {
            ctx.dsl.deleteFrom(TOURNAMENT_RESULTS)
                .where(TOURNAMENT_RESULTS.TOURNAMENT_ID.`in`(tournamentIds))
                .execute()
        }
    }

    context(ctx: WriteTransactionContext)
    override fun resetSeasonTournaments(seasonId: SeasonId): Int =
        ctx.dsl.update(TOURNAMENTS)
            .set(TOURNAMENTS.STATUS, TournamentStatus.Upcoming.value)
            .where(TOURNAMENTS.SEASON_ID.eq(seasonId.value))
            .execute()

    context(ctx: WriteTransactionContext)
    override fun upsertResult(
        tournamentId: TournamentId,
        request: NewTournamentResult,
    ): TournamentResult {
        val upserted =
            ctx.dsl.insertInto(TOURNAMENT_RESULTS)
                .set(resultInsertAssignments(tournamentId, request))
                .onConflict(TOURNAMENT_RESULTS.TOURNAMENT_ID, TOURNAMENT_RESULTS.GOLFER_ID)
                .doUpdate()
                .set(resultUpdateAssignments(request))
                .returning(RESULT_FIELDS)
                .fetchSingle()
        return toResult(upserted)
    }

    private fun insertAssignments(request: NewTournament): Map<Field<*>, Any?> =
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
        request: NewTournamentResult,
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
            TOURNAMENT_RESULTS.SCORECARDS to JSONB.valueOf(encodeScorecards(request.scorecards)),
        )

    private fun resultUpdateAssignments(request: NewTournamentResult): Map<Field<*>, Any?> =
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
            TOURNAMENT_RESULTS.SCORECARDS to JSONB.valueOf(encodeScorecards(request.scorecards)),
        )

    private fun toTournament(record: TournamentsRecord): Tournament =
        Tournament(
            id = TournamentId(record.required(TOURNAMENTS.ID)),
            pgaTournamentId = record.pgaTournamentId,
            name = record.name,
            seasonId = SeasonId(record.seasonId),
            startDate = record.startDate,
            endDate = record.endDate,
            courseName = record.courseName,
            status =
                decoded(
                    field = TOURNAMENTS.STATUS,
                    stored = record.required(TOURNAMENTS.STATUS),
                    decode = TournamentStatus::fromValueOrNull,
                ),
            purseAmount = record.purseAmount,
            payoutMultiplier = record.required(TOURNAMENTS.PAYOUT_MULTIPLIER),
            week = record.week,
            isTeamEvent = record.required(TOURNAMENTS.IS_TEAM_EVENT),
            createdAt = record.required(TOURNAMENTS.CREATED_AT).toInstant(),
        )

    private fun toResult(record: TournamentResultsRecord): TournamentResult =
        TournamentResult(
            id = TournamentResultId(record.required(TOURNAMENT_RESULTS.ID)),
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
            madeCut = record.required(TOURNAMENT_RESULTS.MADE_CUT),
            pairKey = record.pairKey,
        )
}

/** Not jOOQ's `Table.field(Field)`, which answers nullable for a column this subquery is known to select. */
private fun <T> Table<*>.column(selected: Field<T>): Field<T> =
    DSL.field(qualifiedName.append(selected.unqualifiedName), selected.dataType)
