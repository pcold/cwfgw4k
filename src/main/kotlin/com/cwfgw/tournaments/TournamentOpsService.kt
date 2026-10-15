package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.EventImport
import com.cwfgw.espn.findLinkedEvent
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
import com.cwfgw.scoring.ScoringError
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.scoring.WeeklyScoreResult
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate

private val log = KotlinLogging.logger {}

/**
 * Finalize fetches from ESPN before opening its `tx.update`, so no connection is held across the HTTP round trip.
 */
class TournamentOpsService(
    private val tournamentRepository: TournamentRepository,
    private val scoringService: ScoringService,
    private val scoringRepository: ScoringRepository,
    private val espnService: EspnService,
    private val tx: Transactor,
) {
    suspend fun finalizeTournament(tournamentId: TournamentId): Result<Tournament, TournamentOpsError> {
        val tournament =
            tx.read { tournamentRepository.findById(tournamentId) }
                ?: return Result.Err(TournamentOpsError.TournamentNotFound(tournamentId))

        val events =
            espnService.fetchEspnEvents(tournament.startDate)
                .getOrElse { return Result.Err(TournamentOpsError.UpstreamUnavailable(it.failure)) }

        return try {
            tx.update { finalizeLockedIn(tournamentId, fetchedFor = tournament.startDate, events) }
        } catch (aborted: FinalizeAborted) {
            log.warn(aborted) { "Finalize of '${tournament.name}' rolled back: ${aborted.error}" }
            Result.Err(aborted.error)
        }
    }

    /**
     * The row may have changed or gone during the ESPN fetch, so the gates read it again under the lock. The linked
     * event is picked by the locked row's ESPN id, so only the fetch's date needs re-checking.
     */
    context(ctx: WriteTransactionContext)
    private fun finalizeLockedIn(
        tournamentId: TournamentId,
        fetchedFor: LocalDate,
        events: List<EspnTournament>,
    ): Result<Tournament, TournamentOpsError> {
        val tournament =
            tournamentRepository.findByIdForUpdate(tournamentId)
                ?: return Result.Err(TournamentOpsError.TournamentNotFound(tournamentId))

        if (tournament.status == TournamentStatus.Completed) {
            return Result.Err(TournamentOpsError.TournamentFinalized(tournamentId))
        }

        if (tournament.startDate != fetchedFor) {
            return Result.Err(TournamentOpsError.StartDateChanged(fetchedFor, tournament.startDate))
        }

        val blocking = findEarlierUnfinalized(tournament)
        if (blocking.isNotEmpty()) {
            return Result.Err(TournamentOpsError.OutOfOrder(TournamentOpsError.Action.Finalize, blocking))
        }

        log.info { "Finalizing tournament '${tournament.name}' (${tournament.id.value})..." }
        val matches = persistEspnEventsIn(tournament, events)
        // The import completes each match, which its own finalize then refuses: a match not scored here stays
        // unscored until a reset.
        (matches + tournament).forEach { scoreIn(it) }
        log.info { "Finalized '${tournament.name}'" }
        return Result.Ok(tournamentRepository.findById(tournamentId) ?: tournament)
    }

    /** Takes no season or tournament lock: finalize already holds each one it scores, in season → tournament order. */
    context(ctx: WriteTransactionContext)
    private fun scoreIn(tournament: Tournament): WeeklyScoreResult =
        scoringService.calculateScoresIn(tournament.seasonId, tournament.id)
            .getOrElse { throw FinalizeAborted(it.toOpsError(tournament)) }

    suspend fun resetTournament(tournamentId: TournamentId): Result<Tournament, TournamentOpsError> =
        tx.update {
            // Finalize locks before its upserts; this must match: the reverse order deadlocks the two.
            val tournament =
                tournamentRepository.findByIdForUpdate(tournamentId)
                    ?: return@update Result.Err(TournamentOpsError.TournamentNotFound(tournamentId))

            val blocking = findLaterCompleted(tournament)
            if (blocking.isNotEmpty()) {
                return@update Result.Err(TournamentOpsError.OutOfOrder(TournamentOpsError.Action.Reset, blocking))
            }

            log.info { "Resetting tournament '${tournament.name}' (${tournament.id.value})..." }
            scoringRepository.deleteByTournament(tournamentId)
            tournamentRepository.deleteResultsByTournament(tournamentId)
            val updated =
                tournamentRepository.update(
                    tournamentId,
                    UpdateTournamentRequest(status = TournamentStatus.Upcoming),
                )
            log.info { "Reset complete for '${tournament.name}'" }
            Result.Ok(updated ?: tournament)
        }

    context(ctx: WriteTransactionContext)
    private fun persistEspnEventsIn(
        tournament: Tournament,
        events: List<EspnTournament>,
    ): List<Tournament> {
        if (tournament.pgaTournamentId != null) {
            val event = findLinkedEvent(events, tournament) ?: return emptyList()
            espnService.persistImportIn(tournament, event)
            return emptyList()
        }
        val completed = events.filter { it.completed }
        val matches = tournamentRepository.findByPgaTournamentIdsForUpdate(completed.map { it.espnId })
        val importableByEspnId = findImportableMatches(tournament, matches).associateBy { it.pgaTournamentId }
        val imports =
            completed.mapNotNull { event -> importableByEspnId[event.espnId]?.let { EventImport(it, event) } }
        espnService.persistImportsIn(imports)
        return imports.map(EventImport::tournament)
    }

    /**
     * Only [unlinked]'s own season is ordered by the season lock it holds, so a match elsewhere is skipped, not
     * written. A match is gated as its own finalize would be, and [unlinked] stays unfinalized, so a match starting
     * after it is skipped.
     */
    context(ctx: TransactionContext)
    private fun findImportableMatches(
        unlinked: Tournament,
        matches: List<Tournament>,
    ): List<Tournament> {
        if (matches.isEmpty()) return emptyList()
        val unfinalized =
            tournamentRepository.findAll(TournamentFilter(seasonId = unlinked.seasonId, statuses = UNFINALIZED))
        return matches.filter { match ->
            val skip = findMatchSkip(unlinked, match, unfinalized) ?: return@filter true
            val unlinkedId = unlinked.id.value
            val matchId = match.id.value
            log.warn { "cwfgw4k.finalize event=match-skipped reason=$skip tournament_id=$unlinkedId match_id=$matchId" }
            false
        }
    }

    /**
     * Sound only under findByIdForUpdate's season lock, without which a sibling's finalize or reset can commit after
     * this read.
     */
    context(ctx: TransactionContext)
    private fun findEarlierUnfinalized(tournament: Tournament): List<Tournament> =
        tournamentRepository.findAll(
            TournamentFilter(
                seasonId = tournament.seasonId,
                statuses = UNFINALIZED,
                startsBefore = tournament.startDate,
            ),
        )

    /** @see findEarlierUnfinalized */
    context(ctx: TransactionContext)
    private fun findLaterCompleted(tournament: Tournament): List<Tournament> =
        tournamentRepository.findAll(
            TournamentFilter(
                seasonId = tournament.seasonId,
                statuses = setOf(TournamentStatus.Completed),
                startsAfter = tournament.startDate,
            ),
        )
}

private val UNFINALIZED: Set<TournamentStatus> = setOf(TournamentStatus.Upcoming, TournamentStatus.InProgress)

/** Logged by name as a skip's `reason=`, so a rename changes what a search of the logs matches. */
private enum class MatchSkip { OtherSeason, Completed, OutOfOrder }

private fun findMatchSkip(
    unlinked: Tournament,
    match: Tournament,
    unfinalized: List<Tournament>,
): MatchSkip? =
    when {
        match.seasonId != unlinked.seasonId -> MatchSkip.OtherSeason
        match.status == TournamentStatus.Completed -> MatchSkip.Completed
        unfinalized.any { it.startDate < match.startDate } -> MatchSkip.OutOfOrder
        else -> null
    }

/**
 * Thrown, not returned, from `tx.update`: the block commits on any normal return, an Err included, so only a
 * throw rolls the ESPN import back. Caught around `tx.update`; it never leaves the service.
 */
private class FinalizeAborted(val error: TournamentOpsError) : RuntimeException()

private fun ScoringError.toOpsError(tournament: Tournament): TournamentOpsError =
    when (this) {
        ScoringError.SeasonNotFound -> TournamentOpsError.SeasonNotFound(tournament.seasonId)
        ScoringError.TournamentNotFound -> TournamentOpsError.TournamentNotFound(tournament.id)
    }
