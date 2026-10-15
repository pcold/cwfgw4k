package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.POSTGRES_UUID_ORDER
import com.cwfgw.testing.fakeTableOf
import com.cwfgw.testing.uniqueViolation
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID

/** `payout_multiplier` is numeric(6,4): Postgres answers four places whatever the request carried. */
private const val MULTIPLIER_SCALE = 4

private val tournamentOrdering: Comparator<Tournament> =
    compareBy<Tournament> { it.startDate }
        .thenBy { it.week }
        .thenBy { it.createdAt }
        .thenBy(POSTGRES_UUID_ORDER) { it.id.value }

private val resultOrdering: Comparator<TournamentResult> =
    compareBy<TournamentResult, Int?>(nullsLast()) { it.position }
        .thenBy(POSTGRES_UUID_ORDER) { it.id.value }

class FakeTournamentRepository(
    initial: List<Tournament> = emptyList(),
    initialResults: List<TournamentResult> = emptyList(),
    private val idFactory: () -> TournamentId = { TournamentId(UUID.randomUUID()) },
    private val resultIdFactory: () -> TournamentResultId = { TournamentResultId(UUID.randomUUID()) },
    private val clock: () -> Instant = Instant::now,
) : TournamentRepository {
    private val tournaments = fakeTableOf<TournamentId, Tournament>()
    private val results = fakeTableOf<TournamentResultId, TournamentResult>()

    init {
        initial.forEach { tournaments[it.id] = it }
        initialResults.forEach { results[it.id] = it }
    }

    context(ctx: TransactionContext)
    override fun findAll(filter: TournamentFilter): List<Tournament> =
        tournaments.values
            .asSequence()
            .filter { filter.seasonId == null || it.seasonId == filter.seasonId }
            .filter { it.status in filter.statuses }
            .filter { tournament -> filter.startsBefore?.let { tournament.startDate < it } ?: true }
            .filter { tournament -> filter.startsAfter?.let { tournament.startDate > it } ?: true }
            .filter { tournament -> filter.startsOn?.let { tournament.startDate == it } ?: true }
            .sortedWith(tournamentOrdering)
            .toList()

    context(ctx: TransactionContext)
    override fun findById(id: TournamentId): Tournament? = tournaments[id]

    /** The lock order is pinned against Postgres, in TournamentFinalizeRaceSpec. */
    context(ctx: WriteTransactionContext)
    override fun findByIdForUpdate(id: TournamentId): Tournament? = tournaments[id]

    /** The lock is pinned against Postgres, in TournamentSeasonLockRaceSpec. */
    context(ctx: WriteTransactionContext)
    override fun lockSeason(seasonId: SeasonId) = Unit

    context(ctx: TransactionContext)
    override fun findByPgaTournamentId(pgaTournamentId: String): Tournament? =
        tournaments.values.firstOrNull { it.pgaTournamentId == pgaTournamentId }

    /** The locks are pinned against Postgres, in TournamentSiblingRaceSpec. */
    context(ctx: WriteTransactionContext)
    override fun findByPgaTournamentIdsForUpdate(pgaTournamentIds: Collection<String>): List<Tournament> =
        tournaments.values
            .filter { it.pgaTournamentId in pgaTournamentIds }
            .sortedWith(compareBy(POSTGRES_UUID_ORDER) { it.id.value })

    context(ctx: WriteTransactionContext)
    override fun create(request: NewTournament): Tournament {
        if (request.pgaTournamentId != null && findByPgaTournamentId(request.pgaTournamentId) != null) {
            throw uniqueViolation(UNIQUE_PGA_TOURNAMENT_ID)
        }
        val tournament =
            Tournament(
                id = idFactory(),
                pgaTournamentId = request.pgaTournamentId,
                name = request.name,
                seasonId = request.seasonId,
                startDate = request.startDate,
                endDate = request.endDate,
                courseName = request.courseName,
                status = TournamentStatus.Upcoming,
                purseAmount = request.purseAmount,
                payoutMultiplier = (request.payoutMultiplier ?: BigDecimal.ONE).toMultiplierScale(),
                week = request.week,
                isTeamEvent = false,
                createdAt = clock(),
            )
        tournaments[tournament.id] = tournament
        return tournament
    }

    context(ctx: WriteTransactionContext)
    override fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? {
        val current = tournaments[id] ?: return null
        val updated =
            current.copy(
                name = request.name ?: current.name,
                startDate = request.startDate ?: current.startDate,
                endDate = request.endDate ?: current.endDate,
                courseName = request.courseName ?: current.courseName,
                status = request.status ?: current.status,
                purseAmount = request.purseAmount ?: current.purseAmount,
                payoutMultiplier = request.payoutMultiplier?.toMultiplierScale() ?: current.payoutMultiplier,
                isTeamEvent = request.isTeamEvent ?: current.isTeamEvent,
            )
        tournaments[id] = updated
        return updated
    }

    context(ctx: TransactionContext)
    override fun getResults(tournamentId: TournamentId): List<TournamentResult> =
        results.values
            .filter { it.tournamentId == tournamentId }
            .sortedWith(resultOrdering)

    context(ctx: TransactionContext)
    override fun getResultsByTournaments(ids: Collection<TournamentId>): List<TournamentResult> {
        if (ids.isEmpty()) return emptyList()
        val idSet = ids.toSet()
        return results.values
            .filter { it.tournamentId in idSet }
            .sortedWith(
                compareBy(POSTGRES_UUID_ORDER) { result: TournamentResult -> result.tournamentId.value }
                    .then(resultOrdering),
            )
    }

    context(ctx: TransactionContext)
    override fun findResultsForGolfer(
        golferId: GolferId,
        tournamentIds: Collection<TournamentId>,
        positionAtMost: Int,
    ): List<GolferFinish> {
        val idSet = tournamentIds.toSet()
        val leaderboard = results.values.filter { it.tournamentId in idSet }
        val tiedAt = leaderboard.groupingBy { it.tournamentId to it.position }.eachCount()
        return leaderboard
            .filter { it.golferId == golferId }
            .mapNotNull { result ->
                val position = result.position?.takeIf { it <= positionAtMost } ?: return@mapNotNull null
                GolferFinish(
                    tournamentId = result.tournamentId,
                    position = position,
                    numTied = tiedAt.getValue(result.tournamentId to position),
                )
            }
            .sortedWith(compareBy(POSTGRES_UUID_ORDER) { it.tournamentId.value })
    }

    context(ctx: WriteTransactionContext)
    override fun deleteResultsByTournament(tournamentId: TournamentId): Int {
        val matching = results.values.filter { it.tournamentId == tournamentId }
        matching.forEach { results.remove(it.id) }
        return matching.size
    }

    context(ctx: WriteTransactionContext)
    override fun deleteResultsBySeason(seasonId: SeasonId): Int {
        val tournamentIds = tournaments.values.filter { it.seasonId == seasonId }.map { it.id }.toSet()
        val matching = results.values.filter { it.tournamentId in tournamentIds }
        matching.forEach { results.remove(it.id) }
        return matching.size
    }

    context(ctx: WriteTransactionContext)
    override fun resetSeasonTournaments(seasonId: SeasonId): Int {
        val matching = tournaments.values.filter { it.seasonId == seasonId }
        matching.forEach { tournaments[it.id] = it.copy(status = TournamentStatus.Upcoming) }
        return matching.size
    }

    context(ctx: WriteTransactionContext)
    override fun upsertResult(
        tournamentId: TournamentId,
        request: NewTournamentResult,
    ): TournamentResult {
        val existing =
            results.values.firstOrNull {
                it.tournamentId == tournamentId && it.golferId == request.golferId
            }
        val entry =
            TournamentResult(
                id = existing?.id ?: resultIdFactory(),
                tournamentId = tournamentId,
                golferId = request.golferId,
                position = request.position,
                scoreToPar = request.scoreToPar,
                totalStrokes = request.totalStrokes,
                earnings = request.earnings,
                round1 = request.round1,
                round2 = request.round2,
                round3 = request.round3,
                round4 = request.round4,
                madeCut = request.madeCut,
                pairKey = request.pairKey,
            )
        results[entry.id] = entry
        return entry
    }

    private fun BigDecimal.toMultiplierScale(): BigDecimal = setScale(MULTIPLIER_SCALE, RoundingMode.HALF_UP)
}
