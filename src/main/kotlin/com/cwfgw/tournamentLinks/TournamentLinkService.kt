package com.cwfgw.tournamentLinks

import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.result.Result
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus

/**
 * Manages per-tournament ESPN→golfer link overrides. Reads are open;
 * writes are blocked once the tournament reaches [TournamentStatus.Completed]
 * because changing the link map after finalization would silently invalidate
 * persisted scores.
 *
 * Matching paths consult [overrideMap] before falling back to automatic name /
 * id matching — the matcher is read-on-import; mutations here do not trigger
 * a re-import.
 */
class TournamentLinkService(
    private val repository: TournamentLinkRepository,
    private val tournamentService: TournamentService,
    private val golferService: GolferService,
) {
    suspend fun listByTournament(tournamentId: TournamentId): List<TournamentPlayerOverride> =
        repository.listByTournament(tournamentId)

    /** Map a single tournament's overrides into the shape the matchers consume. */
    suspend fun overrideMap(tournamentId: TournamentId): Map<String, GolferId> =
        repository.listByTournament(tournamentId).associate { it.espnCompetitorId to it.golferId }

    suspend fun upsert(
        tournamentId: TournamentId,
        request: UpsertTournamentPlayerOverrideRequest,
    ): Result<TournamentPlayerOverride, TournamentLinkError> {
        val tournament =
            tournamentService.get(tournamentId)
                ?: return Result.Err(TournamentLinkError.TournamentNotFound(tournamentId))
        if (tournament.status == TournamentStatus.Completed) {
            return Result.Err(TournamentLinkError.TournamentFinalized(tournamentId))
        }
        if (golferService.get(request.golferId) == null) {
            return Result.Err(TournamentLinkError.GolferNotFound(request.golferId))
        }
        val override = repository.upsert(tournamentId, request.espnCompetitorId, request.golferId)
        return Result.Ok(override)
    }

    suspend fun delete(
        tournamentId: TournamentId,
        espnCompetitorId: String,
    ): Result<Boolean, TournamentLinkError> {
        val tournament =
            tournamentService.get(tournamentId)
                ?: return Result.Err(TournamentLinkError.TournamentNotFound(tournamentId))
        if (tournament.status == TournamentStatus.Completed) {
            return Result.Err(TournamentLinkError.TournamentFinalized(tournamentId))
        }
        return Result.Ok(repository.delete(tournamentId, espnCompetitorId))
    }
}
