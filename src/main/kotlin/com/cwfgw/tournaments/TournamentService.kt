package com.cwfgw.tournaments

import com.cwfgw.db.Transactor
import com.cwfgw.seasons.SeasonId

class TournamentService(
    private val repository: TournamentRepository,
    private val tx: Transactor,
) {
    suspend fun list(
        seasonId: SeasonId?,
        status: TournamentStatus?,
    ): List<Tournament> = tx.get { repository.findAll(seasonId = seasonId, status = status) }

    suspend fun get(id: TournamentId): Tournament? = tx.get { repository.findById(id) }

    suspend fun findByPgaTournamentId(pgaTournamentId: String): Tournament? =
        tx.get { repository.findByPgaTournamentId(pgaTournamentId) }

    suspend fun create(request: CreateTournamentRequest): Tournament = tx.update { repository.create(request) }

    suspend fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? = tx.update { repository.update(id, request) }

    suspend fun getResults(tournamentId: TournamentId): List<TournamentResult> =
        tx.get { repository.getResults(tournamentId) }

    suspend fun importResults(
        tournamentId: TournamentId,
        requests: List<CreateTournamentResultRequest>,
    ): List<TournamentResult> = tx.update { requests.map { repository.upsertResult(tournamentId, it) } }

    suspend fun deleteResults(tournamentId: TournamentId): Int =
        tx.update { repository.deleteResultsByTournament(tournamentId) }

    suspend fun deleteResultsBySeason(seasonId: SeasonId): Int =
        tx.update { repository.deleteResultsBySeason(seasonId) }

    suspend fun resetSeasonTournaments(seasonId: SeasonId): Int =
        tx.update { repository.resetSeasonTournaments(seasonId) }
}
