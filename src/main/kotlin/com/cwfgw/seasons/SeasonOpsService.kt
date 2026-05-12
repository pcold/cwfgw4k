package com.cwfgw.seasons

import com.cwfgw.db.Transactor
import com.cwfgw.result.Result
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.tournaments.TournamentRepository
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
 *
 * Depends on the relevant repositories directly (rather than fanning out
 * through other services) so each operation's gate check, data gather,
 * and write all happen inside the same transaction. Calling another
 * service mid-flow would open a fresh transaction on a different
 * connection and split the operation across multiple commit boundaries
 * — the [com.cwfgw.admin.AdminService.confirmRoster] reference shape
 * documents the pattern in `src/CLAUDE.md`.
 */
class SeasonOpsService(
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val scoringRepository: ScoringRepository,
    private val tx: Transactor,
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
    suspend fun finalizeSeason(seasonId: SeasonId): Result<Season, SeasonOpsError> =
        tx.update {
            val season =
                seasonRepository.findById(seasonId)
                    ?: return@update Result.Err(SeasonOpsError.SeasonNotFound(seasonId))
            val tournaments = tournamentRepository.findAll(seasonId = seasonId, status = null)
            if (tournaments.isEmpty()) return@update Result.Err(SeasonOpsError.SeasonHasNoTournaments)
            val incomplete =
                tournaments.filter { it.status != TournamentStatus.Completed }.sortedBy { it.startDate }
            if (incomplete.isNotEmpty()) {
                return@update Result.Err(SeasonOpsError.IncompleteTournaments(incomplete))
            }

            log.info { "Finalizing season '${season.name}' (${tournaments.size} tournaments)..." }
            val updated =
                seasonRepository.update(seasonId, UpdateSeasonRequest(status = SEASON_STATUS_COMPLETED))
            Result.Ok(updated ?: season)
        }

    /**
     * Wipe every score / result / standing for the season and revert
     * every tournament back to upcoming. Destructive admin operation —
     * the operator is responsible for confirming intent at the UI layer.
     * Returns counts so the operator sees what was actually deleted.
     */
    suspend fun cleanSeasonResults(seasonId: SeasonId): Result<CleanSeasonResult, SeasonOpsError> =
        tx.update {
            val season =
                seasonRepository.findById(seasonId)
                    ?: return@update Result.Err(SeasonOpsError.SeasonNotFound(seasonId))

            log.info { "Cleaning all results for season '${season.name}'..." }
            val scoresDeleted = scoringRepository.deleteBySeason(seasonId)
            val resultsDeleted = tournamentRepository.deleteResultsBySeason(seasonId)
            val standingsDeleted = scoringRepository.deleteStandingsBySeason(seasonId)
            val tournamentsReset = tournamentRepository.resetSeasonTournaments(seasonId)
            log.info {
                "Cleaned season '${season.name}': $scoresDeleted scores, $resultsDeleted results, " +
                    "$standingsDeleted standings deleted; $tournamentsReset tournaments reset to upcoming"
            }
            Result.Ok(
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
