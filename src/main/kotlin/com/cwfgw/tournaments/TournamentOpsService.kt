package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnError
import com.cwfgw.espn.EspnService
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
import com.cwfgw.result.map
import com.cwfgw.result.mapError
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Orchestrates per-tournament state transitions:
 *   - finalizeTournament: ESPN import → score calculation → standings refresh
 *   - resetTournament:   delete scores + results, revert to upcoming, refresh standings
 *
 * Ordering is enforced chronologically: a tournament can't be finalized
 * while an earlier one in the same season is still upcoming/in_progress;
 * conversely, a tournament can't be reset while a later one is already
 * completed. Lives in its own service (rather than on [TournamentService])
 * so the dependency arrow points outward without creating a cycle —
 * [ScoringService] and [EspnService] both already depend on
 * [TournamentService]. Season-scope orchestration lives in
 * [com.cwfgw.seasons.SeasonOpsService].
 *
 * [resetTournament] follows the cross-repo-flows rule: gate check,
 * deletes, status flip, and standings refresh all run inside one
 * `tx.update` via direct repositories + [ScoringService.refreshStandingsIn].
 * [finalizeTournament] still opens multiple transactions because it
 * interleaves an ESPN HTTP call with persistence; that consolidation
 * needs an `EspnService` split (fetch then persist) and is tracked
 * separately.
 */
@Suppress("LongParameterList")
class TournamentOpsService(
    private val tournamentService: TournamentService,
    private val tournamentRepository: TournamentRepository,
    private val scoringService: ScoringService,
    private val scoringRepository: ScoringRepository,
    private val espnService: EspnService,
    private val tx: Transactor,
) {
    suspend fun finalizeTournament(tournamentId: TournamentId): Result<Tournament, TournamentOpsError> {
        val tournament =
            tournamentService.get(tournamentId)
                ?: return Result.Err(TournamentOpsError.TournamentNotFound(tournamentId))

        val blocking = findEarlierUnfinalized(tournament)
        if (blocking.isNotEmpty()) {
            return Result.Err(TournamentOpsError.OutOfOrder(TournamentOpsError.Action.Finalize, blocking))
        }

        log.info { "Finalizing tournament '${tournament.name}' (${tournament.id.value})..." }
        importFromEspn(tournament).getOrElse { return Result.Err(it) }
        scoringService.calculateScores(tournament.seasonId, tournamentId)
        scoringService.refreshStandings(tournament.seasonId)
        log.info { "Finalized '${tournament.name}'" }
        return Result.Ok(tournamentService.get(tournamentId) ?: tournament)
    }

    suspend fun resetTournament(tournamentId: TournamentId): Result<Tournament, TournamentOpsError> =
        tx.update {
            val tournament =
                tournamentRepository.findById(tournamentId)
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
            scoringService.refreshStandingsIn(tournament.seasonId)
            log.info { "Reset complete for '${tournament.name}'" }
            Result.Ok(updated ?: tournament)
        }

    private suspend fun importFromEspn(tournament: Tournament): Result<Unit, TournamentOpsError> {
        val importResult =
            if (tournament.pgaTournamentId != null) {
                espnService.importForTournament(tournament.id).map { }
            } else {
                espnService.importByDate(tournament.startDate).map { }
            }
        return importResult.mapError { it.toOpsError() }
    }

    private suspend fun findEarlierUnfinalized(tournament: Tournament): List<Tournament> {
        val upcoming = tournamentService.list(tournament.seasonId, status = TournamentStatus.Upcoming)
        val inProgress = tournamentService.list(tournament.seasonId, status = TournamentStatus.InProgress)
        return (upcoming + inProgress)
            .filter { it.id != tournament.id && it.startDate.isBefore(tournament.startDate) }
            .sortedBy { it.startDate }
    }

    context(ctx: TransactionContext)
    private suspend fun findLaterCompleted(tournament: Tournament): List<Tournament> =
        tournamentRepository.findAll(seasonId = tournament.seasonId, status = TournamentStatus.Completed)
            .filter { it.id != tournament.id && it.startDate.isAfter(tournament.startDate) }
            .sortedBy { it.startDate }
}

private fun EspnError.toOpsError(): TournamentOpsError =
    when (this) {
        is EspnError.UpstreamUnavailable -> TournamentOpsError.UpstreamUnavailable(status)
        is EspnError.EventNotInScoreboard -> TournamentOpsError.EventNotInScoreboard(espnEventId)
        is EspnError.TournamentNotFound -> TournamentOpsError.TournamentNotFound(tournamentId)
        is EspnError.TournamentNotLinked ->
            // Caller already branched on pgaTournamentId; reaching this means race or stale state.
            TournamentOpsError.TournamentNotFound(tournamentId)
    }
