package com.cwfgw.tournaments

import com.cwfgw.db.Transactor
import com.cwfgw.db.updateOrOutraced
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId

class TournamentService(
    private val repository: TournamentRepository,
    private val tx: Transactor,
) {
    suspend fun list(filter: TournamentFilter): List<Tournament> = tx.get { repository.findAll(filter) }

    /**
     * By finalization, not today's date: a past week stays current until it is finalized (CWF-78). Schedule order is
     * [TournamentRepository.findAll]'s, so the pages' dropdowns and this answer agree on a tie.
     */
    suspend fun current(seasonId: SeasonId): CurrentTournament =
        tx.read {
            val earliestUnfinalized =
                repository.findAll(TournamentFilter(seasonId = seasonId, statuses = UNFINALIZED_STATUSES)).firstOrNull()
            if (earliestUnfinalized != null) {
                CurrentTournament.Unfinalized(earliestUnfinalized.id)
            } else {
                repository.findAll(TournamentFilter(seasonId = seasonId)).lastOrNull()
                    ?.let { latest -> CurrentTournament.SeasonFinalized(latest.id) }
                    ?: CurrentTournament.NoTournaments
            }
        }

    suspend fun findByPgaTournamentId(pgaTournamentId: String): Tournament? =
        tx.get { repository.findByPgaTournamentId(pgaTournamentId) }

    suspend fun create(request: CreateTournamentRequest): Result<Tournament, TournamentError> =
        tx.updateOrOutraced(UNIQUE_PGA_TOURNAMENT_ID, outraced = { TournamentError.PgaTournamentIdTaken }) {
            Result.Ok(repository.create(request))
        }

    suspend fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? = tx.update { repository.update(id, request) }
}
