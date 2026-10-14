package com.cwfgw.teams

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.apiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))

private fun team(
    id: TeamId = TeamId(UUID.randomUUID()),
    ownerName: String = "Alice",
    teamName: String = "Eagles",
    teamNumber: Int? = null,
): Team =
    Team(
        id = id,
        seasonId = SEASON_ID,
        ownerName = ownerName,
        teamName = teamName,
        teamNumber = teamNumber,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

class TeamRoutesSpec : FunSpec({

    test("GET /seasons/{sid}/rosters returns the precomputed roster view") {
        val eagles = team(teamName = "Eagles")
        val hawks = team(teamName = "Hawks")
        val view =
            listOf(
                RosterViewTeam(
                    teamId = eagles.id,
                    teamNumber = 1,
                    teamName = "Eagles",
                    picks =
                        listOf(
                            RosterViewPick(
                                round = 1,
                                golferName = "Rory McIlroy",
                                ownershipPct = BigDecimal("100.00"),
                                golferId = GolferId(UUID.randomUUID()),
                            ),
                        ),
                ),
                RosterViewTeam(
                    teamId = hawks.id,
                    teamNumber = 2,
                    teamName = "Hawks",
                    picks = emptyList(),
                ),
            )
        val fake =
            FakeTeamRepository(
                initialTeams = listOf(eagles, hawks),
                initialRosterView = mapOf(SEASON_ID to view),
            )

        apiTest({ teamService = TeamService(fake, FakeTransactor()) }) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/rosters")

            response.status shouldBe HttpStatusCode.OK
            val body: List<RosterViewTeam> = response.body()
            body.map { it.teamId } shouldContainExactly listOf(eagles.id, hawks.id)
            body.first().picks.single().golferName shouldBe "Rory McIlroy"
        }
    }

    test("GET /seasons/{non-uuid}/rosters returns 400") {
        apiTest({ teamService = TeamService(FakeTeamRepository(), FakeTransactor()) }) { client ->
            val response = client.get("/api/v1/seasons/not-a-uuid/rosters")

            response.status shouldBe HttpStatusCode.BadRequest
        }
    }
})
