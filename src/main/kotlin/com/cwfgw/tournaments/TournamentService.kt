package com.cwfgw.tournaments

import com.cwfgw.db.Transactor
import com.cwfgw.db.updateOrOutraced
import com.cwfgw.result.Result

class TournamentService(
    private val repository: TournamentRepository,
    private val tx: Transactor,
) {
    suspend fun list(filter: TournamentFilter): List<Tournament> = tx.get { repository.findAll(filter) }

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
