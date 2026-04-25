package com.cwfgw.seasons

import com.cwfgw.result.Result
import com.cwfgw.scoring.ScoringService
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

private val log = KotlinLogging.logger {}

/**
 * Orchestrates season-scope state transitions:
 *   - finalizeSeason: lock as completed once every tournament is done
 *   - cleanSeasonResults: destructive wipe of every score/result/standing
 *     plus revert of every tournament back to upcoming
 *
 * Lives in its own service (separate from
 * [com.cwfgw.tournaments.TournamentOpsService]) because the two state
 * machines are conceptually distinct — one's per-tournament lifecycle,
 * one's whole-season lifecycle. Both share a similar dep set, but
 * sharing a class would only be incidental.
 */
class SeasonOpsService(
    private val seasonService: SeasonService,
    private val tournamentService: TournamentService,
    private val scoringService: ScoringService,
) {
    /**
     * Lock a season as completed once every tournament has been
     * finalized. One-way state transition — no data writes beyond the
     * status flag. Reject with [SeasonOpsError.SeasonHasNoTournaments]
     * if the season has zero tournaments (clicking finalize on an empty
     * season is almost certainly a mistake), or with
     * [SeasonOpsError.IncompleteTournaments] listing every
     * not-yet-completed tournament so the operator sees what's left.
     */
    suspend fun finalizeSeason(seasonId: SeasonId): Result<Season, SeasonOpsError> {
        val season =
            seasonService.get(seasonId) ?: return Result.Err(SeasonOpsError.SeasonNotFound(seasonId))
        val tournaments = tournamentService.list(seasonId, status = null)
        if (tournaments.isEmpty()) return Result.Err(SeasonOpsError.SeasonHasNoTournaments)
        val incomplete =
            tournaments.filter { it.status != TournamentStatus.Completed }.sortedBy { it.startDate }
        if (incomplete.isNotEmpty()) {
            return Result.Err(SeasonOpsError.IncompleteTournaments(incomplete))
        }

        log.info { "Finalizing season '${season.name}' (${tournaments.size} tournaments)..." }
        val updated =
            seasonService.update(seasonId, UpdateSeasonRequest(status = SEASON_STATUS_COMPLETED))
        return Result.Ok(updated ?: season)
    }

    /**
     * Wipe every score / result / standing for the season and revert
     * every tournament back to upcoming. Destructive admin operation —
     * the operator is responsible for confirming intent at the UI layer.
     * Returns counts so the operator sees what was actually deleted.
     */
    suspend fun cleanSeasonResults(seasonId: SeasonId): Result<CleanSeasonResult, SeasonOpsError> {
        val season =
            seasonService.get(seasonId) ?: return Result.Err(SeasonOpsError.SeasonNotFound(seasonId))

        log.info { "Cleaning all results for season '${season.name}'..." }
        val scoresDeleted = scoringService.deleteScoresBySeason(seasonId)
        val resultsDeleted = tournamentService.deleteResultsBySeason(seasonId)
        val standingsDeleted = scoringService.deleteStandingsBySeason(seasonId)
        val tournamentsReset = tournamentService.resetSeasonTournaments(seasonId)
        log.info {
            "Cleaned season '${season.name}': $scoresDeleted scores, $resultsDeleted results, " +
                "$standingsDeleted standings deleted; $tournamentsReset tournaments reset to upcoming"
        }
        return Result.Ok(
            CleanSeasonResult(
                scoresDeleted = scoresDeleted,
                resultsDeleted = resultsDeleted,
                standingsDeleted = standingsDeleted,
                tournamentsReset = tournamentsReset,
            ),
        )
    }
}

/**
 * Wire-shape result of [SeasonOpsService.cleanSeasonResults]. Returns
 * the deletion counts so the UI can echo "Cleaned X scores, Y
 * results, ..." back to the operator who triggered the wipe.
 */
@Serializable
data class CleanSeasonResult(
    val scoresDeleted: Int,
    val resultsDeleted: Int,
    val standingsDeleted: Int,
    val tournamentsReset: Int,
)

/**
 * Season-status string the schema persists; the season status type is
 * still stringly typed (only Tournament got the enum treatment so far).
 * Cleanup tracked separately.
 */
private const val SEASON_STATUS_COMPLETED: String = "completed"
