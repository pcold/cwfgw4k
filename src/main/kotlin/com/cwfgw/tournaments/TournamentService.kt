package com.cwfgw.tournaments

import com.cwfgw.db.Transactor
import com.cwfgw.db.updateOrOutraced
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import java.time.Clock
import java.time.LocalDate

class TournamentService(
    private val repository: TournamentRepository,
    private val tx: Transactor,
    private val clock: Clock = Clock.systemUTC(),
) {
    /** [filter] matches the stored statuses, not the derived ones. */
    suspend fun list(filter: TournamentFilter): List<Tournament> {
        val today = LocalDate.ofInstant(clock.instant(), ESPN_SCHEDULE_ZONE)
        return tx.get { repository.findAll(filter) }.map { it.copy(status = it.statusAsOf(today)) }
    }

    /**
     * By finalization, not today's date: a past week stays current until it is finalized. Schedule order is
     * [TournamentRepository.findAll]'s, so the pages' dropdowns and this answer agree on a tie.
     */
    suspend fun current(seasonId: SeasonId): CurrentTournament {
        val schedule = tx.get { repository.findAll(TournamentFilter(seasonId = seasonId)) }
        val unfinalized = schedule.firstOrNull { it.status != TournamentStatus.Completed }
        val latest = schedule.lastOrNull()
        return when {
            unfinalized != null -> CurrentTournament.Unfinalized(unfinalized.id)
            latest != null -> CurrentTournament.SeasonFinalized(latest.id)
            else -> CurrentTournament.NoTournaments
        }
    }

    suspend fun create(request: NewTournament): Result<Tournament, TournamentError> =
        tx.updateOrOutraced(UNIQUE_PGA_TOURNAMENT_ID, outraced = { TournamentError.PgaTournamentIdTaken }) {
            Result.Ok(repository.create(request))
        }

    /** A status or start date is what finalize's and reset's sibling-order checks read, under the season lock. */
    suspend fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? =
        tx.update {
            val changesSiblingOrder = request.status != null || request.startDate != null
            if (changesSiblingOrder && repository.findByIdForUpdate(id) == null) return@update null
            repository.update(id, request)
        }
}
