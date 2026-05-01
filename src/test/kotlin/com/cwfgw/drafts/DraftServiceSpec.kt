package com.cwfgw.drafts

import com.cwfgw.golfers.GolferId
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.FakeTransactor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.Instant
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))

private fun draft(
    id: DraftId = DraftId(UUID.randomUUID()),
    status: String = "pending",
): Draft =
    Draft(
        id = id,
        seasonId = SEASON_ID,
        status = status,
        draftType = "snake",
        startedAt = null,
        completedAt = null,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
    )

private fun service(
    drafts: List<Draft> = emptyList(),
    picks: List<DraftPick> = emptyList(),
): DraftService =
    DraftService(
        FakeDraftRepository(initialDrafts = drafts, initialPicks = picks),
        TeamService(FakeTeamRepository(), FakeTransactor()),
    )

class DraftServiceSpec : FunSpec({

    test("snakeDraftOrder yields team order forward on odd rounds and reversed on even rounds") {
        val a = TeamId(UUID.randomUUID())
        val b = TeamId(UUID.randomUUID())
        val c = TeamId(UUID.randomUUID())

        val order = service().snakeDraftOrder(listOf(a, b, c), rounds = 3)

        order.map { it.teamId } shouldContainExactly listOf(a, b, c, c, b, a, a, b, c)
        order.map { it.pickNum } shouldContainExactly listOf(1, 2, 3, 4, 5, 6, 7, 8, 9)
        order.map { it.roundNum } shouldContainExactly listOf(1, 1, 1, 2, 2, 2, 3, 3, 3)
    }

    test("start returns NotFound when no draft exists") {
        service().start(SEASON_ID) shouldBe Result.Err(DraftError.NotFound)
    }

    test("start returns WrongStatus when the draft is already in_progress") {
        val result = service(drafts = listOf(draft(status = "in_progress"))).start(SEASON_ID)

        result.shouldBeInstanceOf<Result.Err<DraftError>>()
            .error.shouldBeInstanceOf<DraftError.WrongStatus>()
    }

    test("start transitions a pending draft to in_progress") {
        val result = service(drafts = listOf(draft(status = "pending"))).start(SEASON_ID)

        result.shouldBeInstanceOf<Result.Ok<Draft>>().value.status shouldBe "in_progress"
    }

    test("initializePicks fails with NoTeams when the season has none") {
        service(drafts = listOf(draft(status = "pending")))
            .initializePicks(SEASON_ID, rounds = 2) shouldBe Result.Err(DraftError.NoTeams)
    }

    test("makePick refuses when it is not the requested team's turn") {
        val teamA = TeamId(UUID.randomUUID())
        val teamB = TeamId(UUID.randomUUID())
        val d = draft(status = "in_progress")
        val pick = DraftPick(DraftPickId(UUID.randomUUID()), d.id, teamA, null, 1, 1, null)

        val result =
            service(drafts = listOf(d), picks = listOf(pick))
                .makePick(SEASON_ID, MakePickRequest(teamId = teamB, golferId = GolferId(UUID.randomUUID())))

        val wrongTurn =
            result.shouldBeInstanceOf<Result.Err<DraftError>>()
                .error.shouldBeInstanceOf<DraftError.NotYourTurn>()
        wrongTurn.actualTeam shouldBe teamA
        wrongTurn.requestedTeam shouldBe teamB
    }

    test("makePick fills the next unfilled pick when it is the requested team's turn") {
        val teamA = TeamId(UUID.randomUUID())
        val d = draft(status = "in_progress")
        val pick = DraftPick(DraftPickId(UUID.randomUUID()), d.id, teamA, null, 1, 1, null)
        val golfer = GolferId(UUID.randomUUID())

        val result =
            service(drafts = listOf(d), picks = listOf(pick))
                .makePick(SEASON_ID, MakePickRequest(teamId = teamA, golferId = golfer))

        result.shouldBeInstanceOf<Result.Ok<DraftPick>>().value.golferId shouldBe golfer
    }

    test("makePick returns AllPicksMade when every slot has a golfer") {
        val teamA = TeamId(UUID.randomUUID())
        val d = draft(status = "in_progress")
        val pick =
            DraftPick(
                DraftPickId(UUID.randomUUID()),
                d.id,
                teamA,
                GolferId(UUID.randomUUID()),
                1,
                1,
                Instant.parse("2026-04-01T00:00:00Z"),
            )

        val result =
            service(drafts = listOf(d), picks = listOf(pick))
                .makePick(SEASON_ID, MakePickRequest(teamId = teamA, golferId = GolferId(UUID.randomUUID())))

        result shouldBe Result.Err(DraftError.AllPicksMade)
    }
})
