package com.cwfgw.tournaments

import com.cwfgw.db.Transactor
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate

/** @see com.cwfgw.golfers.GolferCreateRaceSpec */
class TournamentCreateRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val service = TournamentService(TournamentRepository(), tx)

    suspend fun masters(): CreateTournamentRequest {
        val season =
            tx.update {
                val league = LeagueRepository().create(CreateLeagueRequest(name = "Castlewood"))
                SeasonRepository().create(CreateSeasonRequest(leagueId = league.id, name = "2026", seasonYear = 2026))
            }
        return CreateTournamentRequest(
            pgaTournamentId = "evt-401",
            name = "Masters",
            seasonId = season.id,
            startDate = LocalDate.parse("2026-04-09"),
            endDate = LocalDate.parse("2026-04-12"),
        )
    }

    test("a create that loses the race on pga_tournament_id answers PgaTournamentIdTaken") {
        val request = masters()
        val winner = service.create(request).orFail()

        service.create(request.copy(name = "The Masters")) shouldBe Result.Err(TournamentError.PgaTournamentIdTaken)

        service.list(TournamentFilter()) shouldContainExactly listOf(winner)
    }
})
