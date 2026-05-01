package com.cwfgw.tournamentLinks

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentStatus

/**
 * Manages per-tournament ESPN→golfer link overrides. Reads are open;
 * writes are blocked once the tournament reaches [TournamentStatus.Completed]
 * because changing the link map after finalization would silently invalidate
 * persisted scores.
 *
 * The gate checks (tournament exists, not finalized; golfer exists) and the
 * write run inside a single `tx.update` so a concurrent finalize between
 * the check and the write can't slip an override into a closed tournament.
 *
 * Matching paths consult [overrideMap] before falling back to automatic name /
 * id matching — the matcher is read-on-import; mutations here do not trigger
 * a re-import.
 */
class TournamentLinkService(
    private val repository: TournamentLinkRepository,
    private val tournamentRepository: TournamentRepository,
    private val golferRepository: GolferRepository,
    private val tx: Transactor,
) {
    suspend fun listByTournament(tournamentId: TournamentId): List<TournamentPlayerOverride> =
        tx.read { repository.listByTournament(tournamentId) }

    /** Map a single tournament's overrides into the shape the matchers consume. */
    suspend fun overrideMap(tournamentId: TournamentId): Map<String, GolferId> =
        tx.read { repository.listByTournament(tournamentId) }.associate { it.espnCompetitorId to it.golferId }

    suspend fun upsert(
        tournamentId: TournamentId,
        request: UpsertTournamentPlayerOverrideRequest,
    ): Result<TournamentPlayerOverride, TournamentLinkError> =
        tx.update {
            val tournament =
                tournamentRepository.findById(tournamentId)
                    ?: return@update Result.Err(TournamentLinkError.TournamentNotFound(tournamentId))
            if (tournament.status == TournamentStatus.Completed) {
                return@update Result.Err(TournamentLinkError.TournamentFinalized(tournamentId))
            }
            if (golferRepository.findById(request.golferId) == null) {
                return@update Result.Err(TournamentLinkError.GolferNotFound(request.golferId))
            }
            Result.Ok(repository.upsert(tournamentId, request.espnCompetitorId, request.golferId))
        }

    suspend fun delete(
        tournamentId: TournamentId,
        espnCompetitorId: String,
    ): Result<Boolean, TournamentLinkError> =
        tx.update {
            val tournament =
                tournamentRepository.findById(tournamentId)
                    ?: return@update Result.Err(TournamentLinkError.TournamentNotFound(tournamentId))
            if (tournament.status == TournamentStatus.Completed) {
                return@update Result.Err(TournamentLinkError.TournamentFinalized(tournamentId))
            }
            Result.Ok(repository.delete(tournamentId, espnCompetitorId))
        }
}
