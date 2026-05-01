package com.cwfgw.drafts

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.Golfer
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamService

/**
 * Draft state machine. A season has at most one draft which moves through
 * `pending` → `in_progress` → `completed`. Pick slots are pre-allocated in
 * snake order during `initializePicks`, then filled in turn-by-turn via
 * `makePick`.
 *
 * [get] returns a nullable `Draft?` for the pure "does a draft exist?"
 * question. All other operations return [Result] so each multi-mode failure
 * (wrong status, not your turn, all picks made, etc.) can drive its own
 * HTTP status at the route boundary.
 */
class DraftService(
    private val repository: DraftRepository,
    private val teamService: TeamService,
    private val tx: Transactor,
) {
    suspend fun get(seasonId: SeasonId): Draft? = tx.read { repository.findBySeason(seasonId) }

    suspend fun create(
        seasonId: SeasonId,
        request: CreateDraftRequest,
    ): Draft = tx.update { repository.create(seasonId, request) }

    suspend fun start(seasonId: SeasonId): Result<Draft, DraftError> =
        tx.update {
            val draft = repository.findBySeason(seasonId) ?: return@update Result.Err(DraftError.NotFound)
            if (draft.status != STATUS_PENDING) {
                return@update Result.Err(DraftError.WrongStatus(current = draft.status, expected = STATUS_PENDING))
            }
            val updated =
                repository.updateStatus(draft.id, STATUS_IN_PROGRESS)
                    ?: error("updateStatus returned null for draft ${draft.id.value}")
            Result.Ok(updated)
        }

    suspend fun getPicks(seasonId: SeasonId): Result<List<DraftPick>, DraftError> =
        tx.read {
            val draft = repository.findBySeason(seasonId) ?: return@read Result.Err(DraftError.NotFound)
            Result.Ok(repository.getPicks(draft.id))
        }

    suspend fun getAvailableGolfers(seasonId: SeasonId): Result<List<Golfer>, DraftError> =
        tx.read {
            val draft = repository.findBySeason(seasonId) ?: return@read Result.Err(DraftError.NotFound)
            Result.Ok(repository.getAvailableGolfers(draft.id))
        }

    suspend fun initializePicks(
        seasonId: SeasonId,
        rounds: Int,
    ): Result<List<DraftPick>, DraftError> {
        val teams = teamService.listBySeason(seasonId)
        return tx.update {
            val draft = repository.findBySeason(seasonId) ?: return@update Result.Err(DraftError.NotFound)
            if (draft.status != STATUS_PENDING) {
                return@update Result.Err(DraftError.WrongStatus(current = draft.status, expected = STATUS_PENDING))
            }
            if (teams.isEmpty()) return@update Result.Err(DraftError.NoTeams)
            val slots = snakeDraftOrder(teams.map { it.id }, rounds)
            Result.Ok(repository.createPicks(draft.id, slots))
        }
    }

    suspend fun makePick(
        seasonId: SeasonId,
        request: MakePickRequest,
    ): Result<DraftPick, DraftError> =
        tx.update {
            val draft = repository.findBySeason(seasonId) ?: return@update Result.Err(DraftError.NotFound)
            if (draft.status != STATUS_IN_PROGRESS) {
                return@update Result.Err(DraftError.WrongStatus(current = draft.status, expected = STATUS_IN_PROGRESS))
            }
            val picks = repository.getPicks(draft.id)
            val next = picks.firstOrNull { it.golferId == null } ?: return@update Result.Err(DraftError.AllPicksMade)
            if (next.teamId != request.teamId) {
                return@update Result.Err(
                    DraftError.NotYourTurn(actualTeam = next.teamId, requestedTeam = request.teamId),
                )
            }
            val made =
                repository.makePick(draft.id, next.pickNum, request.golferId)
                    ?: error("makePick returned null for draft ${draft.id.value} pick ${next.pickNum}")
            Result.Ok(made)
        }

    /**
     * Snake draft pick order: odd rounds go in team order, even rounds reverse. Each
     * slot gets a globally-unique `pick_num` so callers can find the next unfilled pick
     * just by ordering on it.
     */
    internal fun snakeDraftOrder(
        teamIds: List<TeamId>,
        rounds: Int,
    ): List<PickSlot> =
        (1..rounds).flatMap { round ->
            val ordered = if (round % 2 == 0) teamIds.reversed() else teamIds
            ordered.mapIndexed { idx, teamId ->
                PickSlot(
                    teamId = teamId,
                    roundNum = round,
                    pickNum = (round - 1) * teamIds.size + idx + 1,
                )
            }
        }

    companion object {
        private const val STATUS_PENDING = "pending"
        private const val STATUS_IN_PROGRESS = "in_progress"
    }
}
