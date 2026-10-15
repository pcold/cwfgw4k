package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
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
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf

/**
 * On Postgres because a repeated golfer used to fail only there, on the roster's unique key, and because
 * "nothing written" has to mean nothing committed.
 */
class AdminServiceConfirmRosterRepeatsSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    suspend fun seedSeason(): SeasonId =
        tx.update {
            val league = LeagueRepository().create(CreateLeagueRequest(name = "Castlewood"))
            SeasonRepository().create(CreateSeasonRequest(leagueId = league.id, name = "2026", seasonYear = 2026)).id
        }

    suspend fun seedGolfer(): Golfer =
        tx.update { GolferRepository().create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler")) }

    fun service(): AdminService =
        AdminService(
            tx = tx,
            seasonService = SeasonService(SeasonRepository(), tx),
            tournamentService = TournamentService(TournamentRepository(), tx),
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

    suspend fun teamsWritten(seasonId: SeasonId) = tx.read { TeamRepository().findBySeason(seasonId) }

    suspend fun rosterRowsWritten(seasonId: SeasonId) = tx.read { TeamRepository().findRostersBySeason(seasonId) }

    suspend fun golfersWritten() = tx.read { GolferRepository().findAll(search = null) }

    test("a team name the roster repeats is an invalid roster rather than a taken name, and writes nothing") {
        val seasonId = seedSeason()

        val result =
            confirm(
                seasonId,
                team(number = 1, name = "BROWN", newPick(round = 1, firstName = "First", lastName = "Golfer")),
                team(number = 2, name = "BROWN"),
            )

        result shouldBe refusal(ConfirmedRosterProblem.RepeatedTeamName("BROWN"))
        teamsWritten(seasonId).shouldBeEmpty()
        golfersWritten().shouldBeEmpty()
    }

    test("team names that differ only in case are two teams, as the unique key compares them") {
        val seasonId = seedSeason()

        confirm(seasonId, team(number = 1, name = "BROWN"), team(number = 2, name = "Brown")).orFail()

        teamsWritten(seasonId).map { it.teamName } shouldContainExactlyInAnyOrder listOf("BROWN", "Brown")
    }

    test("an existing golfer a team lists twice is refused before the roster key collides, and writes nothing") {
        val seasonId = seedSeason()
        val scottie = seedGolfer()

        val result =
            confirm(seasonId, team(number = 1, name = "BROWN", existingPick(1, scottie), existingPick(2, scottie)))

        result shouldBe refusal(ConfirmedRosterProblem.RepeatedExistingGolfer("BROWN", scottie.id))
        teamsWritten(seasonId).shouldBeEmpty()
        rosterRowsWritten(seasonId).shouldBeEmpty()
    }

    test("one existing golfer on two teams is a split pick, not a repeat") {
        val seasonId = seedSeason()
        val scottie = seedGolfer()

        confirm(
            seasonId,
            team(number = 1, name = "BROWN", existingPick(1, scottie)),
            team(number = 2, name = "WOMBLE", existingPick(1, scottie)),
        ).orFail()

        rosterRowsWritten(seasonId) shouldHaveSize 2
    }

    test("two new golfers under one team with one normalized name are refused, and no golfer is created") {
        val seasonId = seedSeason()

        val result =
            confirm(
                seasonId,
                team(
                    number = 1,
                    name = "BROWN",
                    newPick(round = 1, firstName = "Byeong-Hun", lastName = "An"),
                    newPick(round = 2, firstName = "byeong hun", lastName = "an"),
                ),
            )

        result shouldBe refusal(ConfirmedRosterProblem.RepeatedNewGolfer("BROWN", "Byeong-Hun", "An"))
        golfersWritten().shouldBeEmpty()
    }

    test("one refusal lists every problem the roster has with itself") {
        val seasonId = seedSeason()
        val scottie = seedGolfer()

        val problems =
            confirm(
                seasonId,
                team(number = 1, name = "BROWN", existingPick(1, scottie), existingPick(2, scottie)),
                team(number = 2, name = "BROWN", newPick(1, "Tom", "Kim"), newPick(2, "Tom", "Kim")),
            ).shouldBeInstanceOf<Result.Err<AdminError>>()
                .error
                .shouldBeInstanceOf<AdminError.InvalidConfirmedRoster>()
                .problems

        problems shouldContainExactlyInAnyOrder
            listOf(
                ConfirmedRosterProblem.RepeatedTeamName("BROWN"),
                ConfirmedRosterProblem.RepeatedExistingGolfer("BROWN", scottie.id),
                ConfirmedRosterProblem.RepeatedNewGolfer("BROWN", "Tom", "Kim"),
            )
    }
})

private fun team(
    number: Int,
    name: String,
    vararg picks: ConfirmedPick,
): ConfirmedTeam = ConfirmedTeam(teamNumber = number, teamName = name, picks = picks.toList())

private fun existingPick(
    round: Int,
    golfer: Golfer,
): ConfirmedPick = ConfirmedPick(round = round, ownershipPct = 100, assignment = GolferAssignment.Existing(golfer.id))

private fun newPick(
    round: Int,
    firstName: String,
    lastName: String,
): ConfirmedPick =
    ConfirmedPick(round = round, ownershipPct = 100, assignment = GolferAssignment.New(firstName, lastName))

private fun refusal(problem: ConfirmedRosterProblem): Result<RosterUploadResult, AdminError> =
    Result.Err(AdminError.InvalidConfirmedRoster(listOf(problem)))
