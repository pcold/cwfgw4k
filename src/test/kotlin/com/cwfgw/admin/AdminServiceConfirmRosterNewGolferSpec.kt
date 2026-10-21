package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferName
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
import io.kotest.matchers.shouldBe

/** On Postgres because the split is read back off the stored row, and a refusal has to mean nothing committed. */
class AdminServiceConfirmRosterNewGolferSpec : FunSpec({

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

    suspend fun confirmRoundOne(
        seasonId: SeasonId,
        name: String,
    ): Result<RosterUploadResult, AdminError> {
        val pick = ConfirmedPick(round = 1, ownershipPct = 100, assignment = GolferAssignment.New(name))
        val brown = fullTeam(number = 1, name = "BROWN", pick)
        return service().confirmRoster(ConfirmRosterRequest(seasonId = seasonId, teams = listOf(brown)))
    }

    suspend fun roundOneGolfer(seasonId: SeasonId): Golfer {
        val golferId = tx.read { TeamRepository().findRostersBySeason(seasonId) }.single { it.draftRound == 1 }.golferId
        return tx.read { GolferRepository().findAll(search = null) }.single { it.id == golferId }
    }

    test("a one-word new golfer is stored as a surname with no first name, as an ESPN import stores one") {
        val seasonId = seedSeason()

        confirmRoundOne(seasonId, "Scheffler").orFail()

        val stored = roundOneGolfer(seasonId)
        stored.firstName shouldBe ""
        stored.lastName shouldBe "Scheffler"
        val importSplit = GolferName.splitName("Scheffler")?.let { split -> split.firstName to split.lastName }
        importSplit shouldBe (stored.firstName to stored.lastName)
    }

    test("a new golfer's name is cut at its last space once its whitespace is collapsed") {
        val seasonId = seedSeason()

        confirmRoundOne(seasonId, "  Byeong   Hun An ").orFail()

        val stored = roundOneGolfer(seasonId)
        stored.firstName shouldBe "Byeong Hun"
        stored.lastName shouldBe "An"
    }

    test("a blank new golfer name is refused, naming its team and round, and nothing is written") {
        val seasonId = seedSeason()

        val result = confirmRoundOne(seasonId, "   ")

        val problem = ConfirmedRosterProblem.BlankNewGolferName(teamName = "BROWN", round = 1)
        result shouldBe Result.Err(AdminError.InvalidConfirmedRoster(listOf(problem)))
        tx.read { TeamRepository().findBySeason(seasonId) }.shouldBeEmpty()
        tx.read { GolferRepository().findAll(search = null) }.shouldBeEmpty()
    }

    test("two blank new golfers on one team are each refused as blank, never as a repeat of each other") {
        val seasonId = seedSeason()
        val brown =
            fullTeam(
                number = 1,
                name = "BROWN",
                ConfirmedPick(round = 1, ownershipPct = 100, assignment = GolferAssignment.New("")),
                ConfirmedPick(round = 2, ownershipPct = 100, assignment = GolferAssignment.New("  ")),
            )

        val result = service().confirmRoster(ConfirmRosterRequest(seasonId = seasonId, teams = listOf(brown)))

        val problems =
            listOf(
                ConfirmedRosterProblem.BlankNewGolferName(teamName = "BROWN", round = 1),
                ConfirmedRosterProblem.BlankNewGolferName(teamName = "BROWN", round = 2),
            )
        result shouldBe Result.Err(AdminError.InvalidConfirmedRoster(problems))
    }

    test("a repeated new golfer is named with its whitespace collapsed, as it would be stored") {
        val seasonId = seedSeason()
        val brown =
            fullTeam(
                number = 1,
                name = "BROWN",
                ConfirmedPick(round = 1, ownershipPct = 100, assignment = GolferAssignment.New("  Tom   Kim ")),
                ConfirmedPick(round = 2, ownershipPct = 100, assignment = GolferAssignment.New("Tom Kim")),
            )

        val result = service().confirmRoster(ConfirmRosterRequest(seasonId = seasonId, teams = listOf(brown)))

        val problem = ConfirmedRosterProblem.RepeatedNewGolfer(teamName = "BROWN", name = "Tom Kim")
        result shouldBe Result.Err(AdminError.InvalidConfirmedRoster(listOf(problem)))
    }
})
