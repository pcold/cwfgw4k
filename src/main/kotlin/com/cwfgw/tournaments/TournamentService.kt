package com.cwfgw.tournaments

import com.cwfgw.seasons.SeasonId

class TournamentService(private val repository: TournamentRepository) {
    suspend fun list(
        seasonId: SeasonId?,
        status: String?,
    ): List<Tournament> = repository.findAll(seasonId = seasonId, status = status)

    suspend fun get(id: TournamentId): Tournament? = repository.findById(id)

    suspend fun findByPgaTournamentId(pgaTournamentId: String): Tournament? =
        repository.findByPgaTournamentId(pgaTournamentId)

    suspend fun create(request: CreateTournamentRequest): Tournament = repository.create(request)

    suspend fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? = repository.update(id, request)

    suspend fun getResults(tournamentId: TournamentId): List<TournamentResult> = repository.getResults(tournamentId)

    suspend fun importResults(
        tournamentId: TournamentId,
        requests: List<CreateTournamentResultRequest>,
    ): List<TournamentResult> = requests.map { repository.upsertResult(tournamentId, it) }
}
