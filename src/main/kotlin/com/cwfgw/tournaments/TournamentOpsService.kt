package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnError
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnTournament
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
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
 * Both methods follow the cross-repo-flows rule: ordering check, ESPN
 * persistence (when applicable), scoring writes, standings refresh, and
 * the final read all run inside one `tx.update` via direct repositories
 * + the composable [ScoringService.calculateScoresIn] /
 * [ScoringService.refreshStandingsIn]. The ESPN network call lives
 * outside the transaction so a Postgres connection is never held across
 * an HTTP round trip (CWF-25).
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
                .getOrElse { return Result.Err(it.toOpsError()) }

        return tx.update {
            val blocking = findEarlierUnfinalized(tournament)
            if (blocking.isNotEmpty()) {
                return@update Result.Err(TournamentOpsError.OutOfOrder(TournamentOpsError.Action.Finalize, blocking))
            }

            log.info { "Finalizing tournament '${tournament.name}' (${tournament.id.value})..." }
            persistEspnEventsIn(tournament, events)
            scoringService.calculateScoresIn(tournament.seasonId, tournamentId)
            scoringService.refreshStandingsIn(tournament.seasonId)
            log.info { "Finalized '${tournament.name}'" }
            Result.Ok(tournamentRepository.findById(tournamentId) ?: tournament)
        }
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
    context(ctx: TransactionContext)
    private fun persistEspnEventsIn(
        tournament: Tournament,
        events: List<EspnTournament>,
    ) {
        if (tournament.pgaTournamentId != null) {
            val event = events.firstOrNull { it.espnId == tournament.pgaTournamentId } ?: return
            espnService.persistImportIn(tournament, event)
            return
        }
        for (event in events.filter { it.completed }) {
            val matched = tournamentRepository.findByPgaTournamentId(event.espnId) ?: continue
            espnService.persistImportIn(matched, event)
        }
    }

    context(ctx: TransactionContext)
    private fun findEarlierUnfinalized(tournament: Tournament): List<Tournament> {
        val upcoming = tournamentRepository.findAll(seasonId = tournament.seasonId, status = TournamentStatus.Upcoming)
        val inProgress =
            tournamentRepository.findAll(seasonId = tournament.seasonId, status = TournamentStatus.InProgress)
        return (upcoming + inProgress)
            .filter { it.id != tournament.id && it.startDate.isBefore(tournament.startDate) }
            .sortedBy { it.startDate }
    }

    context(ctx: TransactionContext)
    private fun findLaterCompleted(tournament: Tournament): List<Tournament> =
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
