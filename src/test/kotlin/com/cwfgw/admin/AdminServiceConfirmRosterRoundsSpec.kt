package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/** On Postgres because "writes nothing" has to mean nothing committed. */
class AdminServiceConfirmRosterRoundsSpec : FunSpec({

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
            seasonRepository = SeasonRepository(),
            tournamentRepository = TournamentRepository(),
            espnService = testEspnService(client = FakeEspnClient(), tx = tx),
            golferRepository = GolferRepository(),
            teamRepository = TeamRepository(),
        )

    suspend fun confirm(
        seasonId: SeasonId,
        vararg teams: ConfirmedTeam,
    ): Result<RosterUploadResult, AdminError> =
        service().confirmRoster(ConfirmRosterRequest(seasonId = seasonId, teams = teams.toList()))

    suspend fun problems(
        seasonId: SeasonId,
        vararg teams: ConfirmedTeam,
    ): List<ConfirmedRosterProblem> =
        confirm(seasonId, *teams)
            .shouldBeInstanceOf<Result.Err<AdminError>>()
            .error
            .shouldBeInstanceOf<AdminError.InvalidConfirmedRoster>()
            .problems

    test("a roster where every team has one pick in each round writes each team's eight rounds") {
        val seasonId = seedSeason()

        confirm(seasonId, fullTeam(number = 1, name = "BROWN"), fullTeam(number = 2, name = "WOMBLE")).orFail()

        val rosterRows = tx.read { TeamRepository().findRostersBySeason(seasonId) }
        rosterRows.groupBy { it.teamId }.values.map { rows -> rows.map { it.draftRound } } shouldContainExactly
            listOf(DRAFT_ROUNDS.toList(), DRAFT_ROUNDS.toList())
    }

    test("one refusal lists every round problem across teams beside a repeat, and writes nothing") {
        val seasonId = seedSeason()
        val brown = fullTeam(number = 1, name = "BROWN", newPick(round = 9, firstName = "Tom", lastName = "Kim"))
        val womble =
            fullTeam(
                number = 2,
                name = "WOMBLE",
                newPick(round = 3, firstName = "Min Woo", lastName = "Lee"),
                newPick(round = 3, firstName = "Sungjae", lastName = "Im"),
            )
        val cowan = fullTeam(number = 3, name = "COWAN").let { team -> team.copy(picks = team.picks.drop(2)) }

        problems(seasonId, brown, womble, cowan, fullTeam(number = 4, name = "BROWN")) shouldContainExactlyInAnyOrder
            listOf(
                ConfirmedRosterProblem.RepeatedTeamName("BROWN"),
                ConfirmedRosterProblem.RoundOutOfRange("BROWN", 9),
                ConfirmedRosterProblem.RepeatedRound("WOMBLE", 3),
                ConfirmedRosterProblem.MissingRounds("COWAN", listOf(1, 2)),
            )
        tx.read { TeamRepository().findBySeason(seasonId) }.shouldBeEmpty()
        tx.read { TeamRepository().findRostersBySeason(seasonId) }.shouldBeEmpty()
        tx.read { GolferRepository().findAll(search = null) }.shouldBeEmpty()
    }

    test("a round outside the draft named twice is out of range once and not also repeated") {
        val seasonId = seedSeason()
        val brown =
            fullTeam(
                number = 1,
                name = "BROWN",
                newPick(round = 0, firstName = "Tom", lastName = "Kim"),
                newPick(round = 0, firstName = "Min Woo", lastName = "Lee"),
            )

        problems(seasonId, brown) shouldBe listOf(ConfirmedRosterProblem.RoundOutOfRange("BROWN", 0))
    }

    test("a team that doubles one round and so skips another is told both") {
        val seasonId = seedSeason()
        val doubled = newPick(round = 1, firstName = "Tom", lastName = "Kim")
        val brown =
            fullTeam(number = 1, name = "BROWN").let { team ->
                team.copy(picks = team.picks.filterNot { it.round == DRAFT_ROUNDS.last } + doubled)
            }

        problems(seasonId, brown) shouldContainExactly
            listOf(
                ConfirmedRosterProblem.RepeatedRound("BROWN", 1),
                ConfirmedRosterProblem.MissingRounds("BROWN", listOf(DRAFT_ROUNDS.last)),
            )
    }
})

private fun newPick(
    round: Int,
    firstName: String,
    lastName: String,
): ConfirmedPick =
    ConfirmedPick(round = round, ownershipPct = 100, assignment = GolferAssignment.New("$firstName $lastName"))
