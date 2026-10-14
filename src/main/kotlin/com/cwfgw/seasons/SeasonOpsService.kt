package com.cwfgw.seasons

import com.cwfgw.db.Transactor
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.tournaments.TournamentRepository
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.Serializable

private val log = KotlinLogging.logger {}

/**
 * Kept apart from [com.cwfgw.tournaments.TournamentOpsService] because the season and tournament lifecycles
 * are distinct. Takes repositories rather than services so an operation's gate and writes share one
 * transaction; see `src/CLAUDE.md` → Cross-repo flows.
 */
class SeasonOpsService(
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val scoringRepository: ScoringRepository,
    private val tx: Transactor,
) {
    /**
     * Wipe every score / result / standing for the season and revert
     * every tournament back to upcoming. Destructive admin operation —
     * the operator is responsible for confirming intent at the UI layer.
     * Returns counts so the operator sees what was actually deleted.
     */
    suspend fun cleanSeasonResults(seasonId: SeasonId): CleanSeasonResult? =
        tx.update {
            val season = seasonRepository.findById(seasonId) ?: return@update null

            log.info { "Cleaning all results for season '${season.name}'..." }
            val scoresDeleted = scoringRepository.deleteBySeason(seasonId)
            val resultsDeleted = tournamentRepository.deleteResultsBySeason(seasonId)
            val standingsDeleted = scoringRepository.deleteStandingsBySeason(seasonId)
            val tournamentsReset = tournamentRepository.resetSeasonTournaments(seasonId)
            log.info {
                "Cleaned season '${season.name}': $scoresDeleted scores, $resultsDeleted results, " +
                    "$standingsDeleted standings deleted; $tournamentsReset tournaments reset to upcoming"
            }
            CleanSeasonResult(
                scoresDeleted = scoresDeleted,
                resultsDeleted = resultsDeleted,
                standingsDeleted = standingsDeleted,
                tournamentsReset = tournamentsReset,
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
