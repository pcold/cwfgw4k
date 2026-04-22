package com.cwfgw.teams

import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.UUID

class TeamRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = TeamRepository(postgres.dsl)
    val golferRepo = GolferRepository(postgres.dsl)
    val seasonRepo = SeasonRepository(postgres.dsl)
    val leagueRepo = LeagueRepository(postgres.dsl)
    var seasonId = SeasonId(UUID.randomUUID())

    beforeEach {
        val league = leagueRepo.create(CreateLeagueRequest(name = "Castlewood Fantasy Golf"))
        seasonId =
            seasonRepo.create(
                CreateSeasonRequest(leagueId = league.id, name = "2026 Season", seasonYear = 2026),
            ).id
    }

    test("create persists the team under a season") {
        val created = repository.create(seasonId, CreateTeamRequest(ownerName = "Alice", teamName = "Eagles"))

        created.seasonId shouldBe seasonId
        created.ownerName shouldBe "Alice"
        created.teamName shouldBe "Eagles"
        repository.findById(created.id).shouldNotBeNull()
    }

    test("findBySeason orders by team_number asc nulls last, then team_name") {
        val unnumberedLater = repository.create(seasonId, CreateTeamRequest("Carol", "Wolves"))
        val numbered2 = repository.create(seasonId, CreateTeamRequest("Bob", "Hawks", teamNumber = 2))
        val numbered1 = repository.create(seasonId, CreateTeamRequest("Alice", "Eagles", teamNumber = 1))
        val unnumberedEarly = repository.create(seasonId, CreateTeamRequest("Dave", "Bears"))

        val result = repository.findBySeason(seasonId)

        result.map { it.id } shouldContainExactly
            listOf(numbered1.id, numbered2.id, unnumberedEarly.id, unnumberedLater.id)
    }

    test("update applies only supplied fields and bumps updated_at") {
        val created = repository.create(seasonId, CreateTeamRequest("Alice", "Eagles"))

        val updated = repository.update(created.id, UpdateTeamRequest(teamName = "Falcons"))

        updated.shouldNotBeNull()
        updated.teamName shouldBe "Falcons"
        updated.ownerName shouldBe "Alice"
        (updated.updatedAt >= created.updatedAt) shouldBe true
    }

    test("addToRoster applies default acquired_via and ownership_pct") {
        val team = repository.create(seasonId, CreateTeamRequest("Alice", "Eagles"))
        val golfer = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))

        val entry = repository.addToRoster(team.id, AddToRosterRequest(golferId = golfer.id))

        entry.teamId shouldBe team.id
        entry.golferId shouldBe golfer.id
        entry.acquiredVia shouldBe "free_agent"
        entry.ownershipPct.compareTo(BigDecimal("100")) shouldBe 0
        entry.isActive shouldBe true
        entry.droppedAt shouldBe null
    }

    test("getRoster returns only active entries ordered by draft_round asc nulls last") {
        val team = repository.create(seasonId, CreateTeamRequest("Alice", "Eagles"))
        val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
        val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
        val phil = golferRepo.create(CreateGolferRequest(firstName = "Phil", lastName = "Mickelson"))

        val roundedScottie =
            repository.addToRoster(
                team.id,
                AddToRosterRequest(golferId = scottie.id, draftRound = 1, acquiredVia = "draft"),
            )
        val roundedRory =
            repository.addToRoster(
                team.id,
                AddToRosterRequest(golferId = rory.id, draftRound = 2, acquiredVia = "draft"),
            )
        val unrounded = repository.addToRoster(team.id, AddToRosterRequest(golferId = phil.id))

        val roster = repository.getRoster(team.id)

        roster.map { it.id } shouldContainExactly listOf(roundedScottie.id, roundedRory.id, unrounded.id)
    }

    test("dropFromRoster soft-deletes and hides the entry from getRoster") {
        val team = repository.create(seasonId, CreateTeamRequest("Alice", "Eagles"))
        val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
        repository.addToRoster(team.id, AddToRosterRequest(golferId = rory.id))

        repository.dropFromRoster(team.id, rory.id).shouldBeTrue()

        repository.getRoster(team.id).shouldBeEmpty()
    }

    test("dropFromRoster returns false when the golfer is not on the team's active roster") {
        val team = repository.create(seasonId, CreateTeamRequest("Alice", "Eagles"))

        repository.dropFromRoster(team.id, GolferId(UUID.randomUUID())).shouldBeFalse()
    }

    test("getRosterView groups picks by team, joining golfer names") {
        val alice = repository.create(seasonId, CreateTeamRequest("Alice", "Eagles"))
        val bob = repository.create(seasonId, CreateTeamRequest("Bob", "Hawks"))
        val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
        val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))

        repository.addToRoster(
            alice.id,
            AddToRosterRequest(golferId = rory.id, draftRound = 1, acquiredVia = "draft"),
        )
        repository.addToRoster(
            bob.id,
            AddToRosterRequest(golferId = scottie.id, draftRound = 1, acquiredVia = "draft"),
        )

        val view = repository.getRosterView(seasonId)

        view shouldHaveSize 2
        view.first { it.teamId == alice.id }.picks.single().golferName shouldBe "Rory McIlroy"
        view.first { it.teamId == bob.id }.picks.single().golferName shouldBe "Scottie Scheffler"
    }
})
