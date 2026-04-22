package com.cwfgw.teams

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

private val SEASON_ONE = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))
private val SEASON_TWO = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa2"))

private fun team(
    id: TeamId = TeamId(UUID.randomUUID()),
    seasonId: SeasonId = SEASON_ONE,
    ownerName: String = "Alice",
    teamName: String = "Eagles",
): Team =
    Team(
        id = id,
        seasonId = seasonId,
        ownerName = ownerName,
        teamName = teamName,
        teamNumber = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

class TeamServiceSpec : FunSpec({

    test("listBySeason returns only teams in the requested season") {
        val a = team(seasonId = SEASON_ONE, teamName = "Eagles")
        val b = team(seasonId = SEASON_TWO, teamName = "Hawks")
        val service = TeamService(FakeTeamRepository(initialTeams = listOf(a, b)))

        service.listBySeason(SEASON_ONE).map { it.id } shouldContainExactlyInAnyOrder listOf(a.id)
    }

    test("get returns the team when present and null otherwise") {
        val existing = team()
        val service = TeamService(FakeTeamRepository(initialTeams = listOf(existing)))

        service.get(existing.id) shouldBe existing
        service.get(TeamId(UUID.randomUUID())).shouldBeNull()
    }

    test("create delegates to the repository with the provided season id") {
        val newId = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000bb1"))
        val newTime = Instant.parse("2026-03-15T00:00:00Z")
        val fake = FakeTeamRepository(teamIdFactory = { newId }, clock = { newTime })
        val service = TeamService(fake)

        val created = service.create(SEASON_ONE, CreateTeamRequest("Alice", "Eagles"))

        created.id shouldBe newId
        created.seasonId shouldBe SEASON_ONE
        created.createdAt shouldBe newTime
    }

    test("addToRoster defaults acquired_via to free_agent and ownership_pct to 100") {
        val service = TeamService(FakeTeamRepository())
        val teamId = TeamId(UUID.randomUUID())
        val golferId = GolferId(UUID.randomUUID())

        val entry = service.addToRoster(teamId, AddToRosterRequest(golferId = golferId))

        entry.acquiredVia shouldBe "free_agent"
        entry.ownershipPct.toPlainString() shouldBe "100.00"
    }

    test("dropFromRoster returns true for active entries and false otherwise") {
        val service = TeamService(FakeTeamRepository())
        val teamId = TeamId(UUID.randomUUID())
        val golferId = GolferId(UUID.randomUUID())

        service.addToRoster(teamId, AddToRosterRequest(golferId = golferId))

        service.dropFromRoster(teamId, golferId).shouldBeTrue()
        service.dropFromRoster(teamId, golferId).shouldBeFalse()
        service.getRoster(teamId).shouldBeEmpty()
    }
})
