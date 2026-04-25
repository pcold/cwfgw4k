package com.cwfgw.tournaments

import com.cwfgw.espn.EspnError
import com.cwfgw.espn.EspnService
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
import com.cwfgw.result.map
import com.cwfgw.result.mapError
import com.cwfgw.scoring.ScoringService
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Orchestrates tournament + season state transitions:
 *   - finalizeTournament: ESPN import → score calculation → standings refresh
 *   - resetTournament:   delete scores + results, revert to upcoming, refresh standings
 *
 * Ordering is enforced chronologically: a tournament can't be finalized
 * while an earlier one in the same season is still upcoming/in_progress;
 * conversely, a tournament can't be reset while a later one is already
 * completed. Lives in its own service (rather than on [TournamentService])
 * so the dependency arrow points outward without creating a cycle —
 * [ScoringService] and [EspnService] both already depend on
 * [TournamentService].
 */
class TournamentOpsService(
    private val tournamentService: TournamentService,
    private val scoringService: ScoringService,
    private val espnService: EspnService,
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

    suspend fun resetTournament(tournamentId: TournamentId): Result<Tournament, TournamentOpsError> {
        val tournament =
            tournamentService.get(tournamentId)
                ?: return Result.Err(TournamentOpsError.TournamentNotFound(tournamentId))

        val blocking = findLaterCompleted(tournament)
        if (blocking.isNotEmpty()) {
            return Result.Err(TournamentOpsError.OutOfOrder(TournamentOpsError.Action.Reset, blocking))
        }

        log.info { "Resetting tournament '${tournament.name}' (${tournament.id.value})..." }
        scoringService.deleteScoresByTournament(tournamentId)
        tournamentService.deleteResults(tournamentId)
        val updated =
            tournamentService.update(
                tournamentId,
                UpdateTournamentRequest(status = TournamentStatus.Upcoming),
            )
        scoringService.refreshStandings(tournament.seasonId)
        log.info { "Reset complete for '${tournament.name}'" }
        return Result.Ok(updated ?: tournament)
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

    private suspend fun findLaterCompleted(tournament: Tournament): List<Tournament> =
        tournamentService.list(tournament.seasonId, status = TournamentStatus.Completed)
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
