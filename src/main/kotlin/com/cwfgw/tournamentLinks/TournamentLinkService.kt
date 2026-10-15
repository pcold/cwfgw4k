package com.cwfgw.tournamentLinks

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferName
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
        tx.get { repository.listByTournament(tournamentId) }

    /** Map a single tournament's overrides into the shape the matchers consume. */
    suspend fun overrideMap(tournamentId: TournamentId): Map<String, GolferId> =
        tx.get { repository.listByTournament(tournamentId) }.associate { it.espnCompetitorId to it.golferId }

    suspend fun upsert(
        tournamentId: TournamentId,
        request: UpsertTournamentPlayerOverrideRequest,
    ): Result<TournamentPlayerOverride, TournamentLinkError> =
        tx.update {
            lockedTournamentError(tournamentId)?.let { return@update Result.Err(it) }
            val golfer =
                golferRepository.findByIdForShare(request.golferId)
                    ?: return@update Result.Err(TournamentLinkError.GolferNotFound(request.golferId))
            if (golfer.tombstonedAt != null) {
                return@update Result.Err(TournamentLinkError.GolferTombstoned(golfer.id))
            }
            Result.Ok(repository.upsert(tournamentId, request.espnCompetitorId, golfer.id))
        }

    /** One transaction, so a refused link leaves no golfer behind for an admin to find and wonder about. */
    suspend fun createGolferAndLink(
        tournamentId: TournamentId,
        request: CreateLinkedGolferRequest,
    ): Result<TournamentPlayerOverride, TournamentLinkError> =
        tx.update {
            lockedTournamentError(tournamentId)?.let { return@update Result.Err(it) }
            val name =
                GolferName.splitName(request.name)?.takeIf { it.firstName.isNotEmpty() }
                    ?: return@update Result.Err(TournamentLinkError.GolferNameIncomplete(request.name))
            val golfer =
                golferRepository.create(CreateGolferRequest(firstName = name.firstName, lastName = name.lastName))
            Result.Ok(repository.upsert(tournamentId, request.espnCompetitorId, golfer.id))
        }

    suspend fun delete(
        tournamentId: TournamentId,
        espnCompetitorId: String,
    ): Result<Boolean, TournamentLinkError> =
        tx.update {
            lockedTournamentError(tournamentId)?.let { return@update Result.Err(it) }
            Result.Ok(repository.delete(tournamentId, espnCompetitorId))
        }

    /** Locked before the golfer, the season → tournament → golfer order finalize's backfill takes. */
    context(ctx: WriteTransactionContext)
    private fun lockedTournamentError(tournamentId: TournamentId): TournamentLinkError? {
        val tournament =
            tournamentRepository.findByIdForUpdate(tournamentId)
                ?: return TournamentLinkError.TournamentNotFound(tournamentId)
        return TournamentLinkError.TournamentFinalized(tournamentId).takeIf {
            tournament.status == TournamentStatus.Completed
        }
    }
}
