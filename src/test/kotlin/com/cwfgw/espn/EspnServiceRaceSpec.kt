package com.cwfgw.espn

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.NewTournament
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import java.time.LocalDate

/**
 * The import's golfer lookups are blinded, which is the window another finalize commits the same athlete
 * into; the winner's row is written up front so the import's insert meets it as a concurrent loser would.
 */
class EspnServiceRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("an import that loses the race creating a golfer scores the winner's row in the same transaction") {
        val tournaments = TournamentRepository()
        val tournament =
            tx.update {
                val league = LeagueRepository().create(CreateLeagueRequest(name = "Castlewood"))
                val season =
                    SeasonRepository().create(
                        CreateSeasonRequest(leagueId = league.id, name = "2026", seasonYear = 2026),
                    )
                tournaments.create(
                    NewTournament(
                        pgaTournamentId = "evt-401",
                        name = "Masters",
                        seasonId = season.id,
                        startDate = LocalDate.parse("2026-04-09"),
                        endDate = LocalDate.parse("2026-04-12"),
                    ),
                )
            }
        val winner =
            tx.update {
                GolferRepository().create(
                    CreateGolferRequest(pgaPlayerId = "espn-1", firstName = "Scottie", lastName = "Scheffler"),
                )
            }
        val service =
            testEspnService(
                client = FakeEspnClient(),
                seasonRepository = SeasonRepository(),
                golferRepository = BlindGolferRepository(GolferRepository()),
                teamRepository = TeamRepository(),
                tournamentRepository = tournaments,
                linkRepository = TournamentLinkRepository(),
                tx = tx,
            )

        tx.update { service.persistImportIn(tournament, masters(espnId = "espn-1", name = "Scottie Scheffler")) }

        tx.read { GolferRepository().findAll(search = null) }.map { it.id } shouldContainExactly listOf(winner.id)
        tx.read { tournaments.getResults(tournament.id) }.map { it.golferId } shouldContainExactly listOf(winner.id)
    }
})

private class BlindGolferRepository(
    delegate: GolferRepository,
) : GolferRepository by delegate {
    context(ctx: TransactionContext)
    override fun findAll(search: String?): List<Golfer> = emptyList()

    context(ctx: TransactionContext)
    override fun findByPgaPlayerId(pgaPlayerId: String): Golfer? = null
}

private fun masters(
    espnId: String,
    name: String,
): EspnTournament =
    EspnTournament(
        espnId = "evt-401",
        name = "Masters",
        completed = true,
        competitors =
            listOf(
                EspnCompetitor(
                    espnId = espnId,
                    name = name,
                    order = 1,
                    scoreStr = "-10",
                    scoreToPar = -10,
                    totalStrokes = 278,
                    roundScores = emptyList(),
                    position = 1,
                    status = EspnStatus.Active,
                    isTeamPartner = false,
                    pairKey = null,
                ),
            ),
        isTeamEvent = false,
    )
