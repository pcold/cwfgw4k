package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe

/** On Postgres because "writes nothing" has to mean nothing committed, and the CHECK is what this runs ahead of. */
class AdminServiceConfirmRosterOwnershipSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    suspend fun seedSeason(): SeasonId =
        tx.update {
            val league = LeagueRepository().create(CreateLeagueRequest(name = "Castlewood"))
            SeasonRepository().create(CreateSeasonRequest(leagueId = league.id, name = "2026", seasonYear = 2026)).id
        }

    fun service(): AdminService =
        AdminService(
            tx = tx,
            seasonService = SeasonService(SeasonRepository(), tx),
            tournamentService = TournamentService(TournamentRepository(), tx),
            tournamentRepository = TournamentRepository(),
            espnService = testEspnService(client = FakeEspnClient(), tx = tx),
            golferService = GolferService(GolferRepository(), tx),
            golferRepository = GolferRepository(),
            teamRepository = TeamRepository(),
        )

    suspend fun confirm(
        seasonId: SeasonId,
        vararg teams: ConfirmedTeam,
    ): Result<RosterUploadResult, AdminError> =
        service().confirmRoster(ConfirmRosterRequest(seasonId = seasonId, teams = teams.toList()))

    test("an ownership of zero or past 100 is refused, naming its team and round, and nothing is written") {
        val seasonId = seedSeason()
        val brown =
            fullTeam(
                number = 1,
                name = "BROWN",
                newPick(round = 1, ownershipPct = 0, name = "Tom Kim"),
                newPick(round = 2, ownershipPct = 101, name = "Min Woo Lee"),
            )

        confirm(seasonId, brown) shouldBe
            Result.Err(
                AdminError.InvalidConfirmedRoster(
                    listOf(
                        ConfirmedRosterProblem.OwnershipPctOutOfRange("BROWN", round = 1, ownershipPct = 0),
                        ConfirmedRosterProblem.OwnershipPctOutOfRange("BROWN", round = 2, ownershipPct = 101),
                    ),
                ),
            )
        tx.read { TeamRepository().findBySeason(seasonId) }.shouldBeEmpty()
        tx.read { GolferRepository().findAll(search = null) }.shouldBeEmpty()
    }

    test("an ownership at either end of the range is written as given") {
        val seasonId = seedSeason()
        val brown =
            fullTeam(
                number = 1,
                name = "BROWN",
                newPick(round = 1, ownershipPct = OWNERSHIP_PCTS.first, name = "Tom Kim"),
                newPick(round = 2, ownershipPct = OWNERSHIP_PCTS.last, name = "Min Woo Lee"),
            )

        confirm(seasonId, brown).orFail()

        val written = tx.read { TeamRepository().findRostersBySeason(seasonId) }.filter { it.draftRound in 1..2 }
        written.map { it.ownershipPct.toInt() } shouldContainExactlyInAnyOrder
            listOf(OWNERSHIP_PCTS.first, OWNERSHIP_PCTS.last)
    }
})

private fun newPick(
    round: Int,
    ownershipPct: Int,
    name: String,
): ConfirmedPick = ConfirmedPick(round = round, ownershipPct = ownershipPct, assignment = GolferAssignment.New(name))
