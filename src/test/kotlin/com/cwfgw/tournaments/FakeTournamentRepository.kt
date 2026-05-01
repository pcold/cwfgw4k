package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.seasons.SeasonId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

private val tournamentOrdering: Comparator<Tournament> =
    compareBy<Tournament> { it.startDate }.thenBy { it.createdAt }

private val resultOrdering: Comparator<TournamentResult> =
    compareBy<TournamentResult, Int?>(nullsLast()) { it.position }
        .thenBy { it.id.value }

class FakeTournamentRepository(
    initial: List<Tournament> = emptyList(),
    initialResults: List<TournamentResult> = emptyList(),
    private val idFactory: () -> TournamentId = { TournamentId(UUID.randomUUID()) },
    private val resultIdFactory: () -> TournamentResultId = { TournamentResultId(UUID.randomUUID()) },
    private val clock: () -> Instant = Instant::now,
) : TournamentRepository {
    private val tournaments = ConcurrentHashMap<TournamentId, Tournament>()
    private val results = ConcurrentHashMap<TournamentResultId, TournamentResult>()

    init {
        initial.forEach { tournaments[it.id] = it }
        initialResults.forEach { results[it.id] = it }
    }

    context(ctx: TransactionContext)
    override suspend fun findAll(
        seasonId: SeasonId?,
        status: TournamentStatus?,
    ): List<Tournament> =
        tournaments.values
            .asSequence()
            .filter { seasonId == null || it.seasonId == seasonId }
            .filter { status == null || it.status == status }
            .sortedWith(tournamentOrdering)
            .toList()

    context(ctx: TransactionContext)
    override suspend fun findById(id: TournamentId): Tournament? = tournaments[id]

    context(ctx: TransactionContext)
    override suspend fun findByPgaTournamentId(pgaTournamentId: String): Tournament? =
        tournaments.values.firstOrNull { it.pgaTournamentId == pgaTournamentId }

    context(ctx: TransactionContext)
    override suspend fun create(request: CreateTournamentRequest): Tournament {
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
                payoutMultiplier = request.payoutMultiplier ?: BigDecimal("1.0000"),
                week = request.week,
                isTeamEvent = false,
                createdAt = clock(),
            )
        tournaments[tournament.id] = tournament
        return tournament
    }

    context(ctx: TransactionContext)
    override suspend fun update(
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
                payoutMultiplier = request.payoutMultiplier ?: current.payoutMultiplier,
                isTeamEvent = request.isTeamEvent ?: current.isTeamEvent,
            )
        tournaments[id] = updated
        return updated
    }

    context(ctx: TransactionContext)
    override suspend fun getResults(tournamentId: TournamentId): List<TournamentResult> =
        results.values
            .filter { it.tournamentId == tournamentId }
            .sortedWith(resultOrdering)

    context(ctx: TransactionContext)
    override suspend fun deleteResultsByTournament(tournamentId: TournamentId): Int {
        val matching = results.values.filter { it.tournamentId == tournamentId }
        matching.forEach { results.remove(it.id) }
        return matching.size
    }

    context(ctx: TransactionContext)
    override suspend fun deleteResultsBySeason(seasonId: SeasonId): Int {
        val tournamentIds = tournaments.values.filter { it.seasonId == seasonId }.map { it.id }.toSet()
        val matching = results.values.filter { it.tournamentId in tournamentIds }
        matching.forEach { results.remove(it.id) }
        return matching.size
    }

    context(ctx: TransactionContext)
    override suspend fun resetSeasonTournaments(seasonId: SeasonId): Int {
        val matching = tournaments.values.filter { it.seasonId == seasonId }
        matching.forEach { tournaments[it.id] = it.copy(status = TournamentStatus.Upcoming) }
        return matching.size
    }

    context(ctx: TransactionContext)
    override suspend fun upsertResult(
        tournamentId: TournamentId,
        request: CreateTournamentResultRequest,
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
}
