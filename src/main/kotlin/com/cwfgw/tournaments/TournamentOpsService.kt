package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.findLinkedEvent
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
import com.cwfgw.scoring.ScoringError
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
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

        if (tournament.startDate != fetchedFor) {
            return Result.Err(TournamentOpsError.StartDateChanged(fetchedFor, tournament.startDate))
        }

        val blocking = findEarlierUnfinalized(tournament)
        if (blocking.isNotEmpty()) {
            return Result.Err(TournamentOpsError.OutOfOrder(TournamentOpsError.Action.Finalize, blocking))
        }

        log.info { "Finalizing tournament '${tournament.name}' (${tournament.id.value})..." }
        persistEspnEventsIn(tournament, events)
        scoringService.calculateScoresIn(tournament.seasonId, tournamentId)
            .getOrElse { throw FinalizeAborted(it.toOpsError(tournament)) }
        log.info { "Finalized '${tournament.name}'" }
        return Result.Ok(tournamentRepository.findById(tournamentId) ?: tournament)
    }

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

    /**
     * Persist whichever events apply for this finalize. Linked
     * tournament: match the single event by `pga_tournament_id` and
     * persist it; if ESPN doesn't have that event on the date, the
     * import is a no-op (scoring still runs against whatever results
     * are already in the DB). Unlinked tournament: iterate completed
     * events and persist any that match a DB tournament by
     * `pga_tournament_id` — the original tournament being finalized
     * has no `pga_tournament_id` so it won't match, mirroring the
     * pre-CWF-25 behavior.
     */
    context(ctx: WriteTransactionContext)
    private fun persistEspnEventsIn(
        tournament: Tournament,
        events: List<EspnTournament>,
    ) {
        if (tournament.pgaTournamentId != null) {
            val event = findLinkedEvent(events, tournament) ?: return
            espnService.persistImportIn(tournament, event)
            return
        }
        val completed = events.filter { it.completed }
        val matchedByEspnId =
            tournamentRepository.findByPgaTournamentIdsForUpdate(completed.map { it.espnId })
                .associateBy { it.pgaTournamentId }
        for (event in completed) {
            val matched = matchedByEspnId[event.espnId] ?: continue
            espnService.persistImportIn(matched, event)
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
                statuses = setOf(TournamentStatus.Upcoming, TournamentStatus.InProgress),
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
