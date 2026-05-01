package com.cwfgw.teams

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

private fun rosterEntry(
    teamId: TeamId,
    golferId: GolferId = GolferId(UUID.randomUUID()),
    draftRound: Int? = null,
): RosterEntry =
    RosterEntry(
        id = RosterEntryId(UUID.randomUUID()),
        teamId = teamId,
        golferId = golferId,
        acquiredVia = "draft",
        draftRound = draftRound,
        ownershipPct = BigDecimal("100.00"),
        acquiredAt = Instant.parse("2026-03-01T00:00:00Z"),
        droppedAt = null,
        isActive = true,
    )

private fun teamsAndRoster(
    teams: List<Team> = emptyList(),
    roster: List<RosterEntry> = emptyList(),
): ApiFixture.() -> Unit =
    {
        teamService = TeamService(FakeTeamRepository(initialTeams = teams, initialRoster = roster), FakeTransactor())
    }

class TeamRoutesSpec : FunSpec({

    test("GET /seasons/{seasonId}/teams returns teams for that season") {
        val eagles = team(teamName = "Eagles", teamNumber = 1)
        val hawks = team(teamName = "Hawks", teamNumber = 2)
        apiTest(teamsAndRoster(teams = listOf(hawks, eagles))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/teams")

            response.status shouldBe HttpStatusCode.OK
            val teams: List<Team> = response.body()
            teams.map { it.id } shouldContainExactly listOf(eagles.id, hawks.id)
        }
    }

    test("GET /seasons/{seasonId}/teams/{teamId} returns 200 with the team") {
        val eagles = team()
        apiTest(teamsAndRoster(teams = listOf(eagles))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/teams/${eagles.id.value}")

            response.status shouldBe HttpStatusCode.OK
            response.body<Team>() shouldBe eagles
        }
    }

    test("GET /seasons/{seasonId}/teams/{unknown} returns 404") {
        apiTest { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/teams/${UUID.randomUUID()}")
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /seasons/{seasonId}/teams creates a team returning 201") {
        val newId = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000bb1"))
        val newTime = Instant.parse("2026-03-15T12:00:00Z")
        val fake = FakeTeamRepository(teamIdFactory = { newId }, clock = { newTime })

        authenticatedApiTest({ teamService = TeamService(fake, FakeTransactor()) }) { client ->
            val response =
                client.post("/api/v1/seasons/${SEASON_ID.value}/teams") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTeamRequest(ownerName = "Alice", teamName = "Eagles"))
                }

            response.status shouldBe HttpStatusCode.Created
            val created: Team = response.body()
            created.id shouldBe newId
            created.seasonId shouldBe SEASON_ID
            created.teamName shouldBe "Eagles"
        }
    }

    test("PUT /seasons/{seasonId}/teams/{teamId} applies partial updates") {
        val eagles = team(teamName = "Eagles")
        authenticatedApiTest(teamsAndRoster(teams = listOf(eagles))) { client ->
            val response =
                client.put("/api/v1/seasons/${SEASON_ID.value}/teams/${eagles.id.value}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTeamRequest(teamName = "Falcons"))
                }

            response.status shouldBe HttpStatusCode.OK
            val updated: Team = response.body()
            updated.teamName shouldBe "Falcons"
            updated.ownerName shouldBe "Alice"
        }
    }

    test("GET /seasons/{sid}/teams/{tid}/roster returns the active roster") {
        val eagles = team()
        val entry = rosterEntry(teamId = eagles.id, draftRound = 1)
        apiTest(teamsAndRoster(teams = listOf(eagles), roster = listOf(entry))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/teams/${eagles.id.value}/roster")

            response.status shouldBe HttpStatusCode.OK
            val roster: List<RosterEntry> = response.body()
            roster.map { it.id } shouldContainExactly listOf(entry.id)
        }
    }

    test("POST /seasons/{sid}/teams/{tid}/roster creates an entry returning 201") {
        val eagles = team()
        val entryId = RosterEntryId(UUID.fromString("00000000-0000-0000-0000-000000000ee1"))
        val newTime = Instant.parse("2026-03-15T00:00:00Z")
        val fake =
            FakeTeamRepository(
                initialTeams = listOf(eagles),
                rosterIdFactory = { entryId },
                clock = { newTime },
            )

        authenticatedApiTest({ teamService = TeamService(fake, FakeTransactor()) }) { client ->
            val response =
                client.post("/api/v1/seasons/${SEASON_ID.value}/teams/${eagles.id.value}/roster") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        AddToRosterRequest(
                            golferId = GolferId(UUID.randomUUID()),
                            acquiredVia = "draft",
                            draftRound = 3,
                        ),
                    )
                }

            response.status shouldBe HttpStatusCode.Created
            val created: RosterEntry = response.body()
            created.id shouldBe entryId
            created.acquiredVia shouldBe "draft"
            created.draftRound shouldBe 3
        }
    }

    test("DELETE /seasons/{sid}/teams/{tid}/roster/{golferId} returns 204 on drop") {
        val eagles = team()
        val golferId = GolferId(UUID.randomUUID())
        val entry = rosterEntry(teamId = eagles.id, golferId = golferId)

        authenticatedApiTest(teamsAndRoster(teams = listOf(eagles), roster = listOf(entry))) { client ->
            val response =
                client.delete(
                    "/api/v1/seasons/${SEASON_ID.value}/teams/${eagles.id.value}/roster/${golferId.value}",
                )

            response.status shouldBe HttpStatusCode.NoContent
        }
    }

    test("DELETE /seasons/{sid}/teams/{tid}/roster/{unknown} returns 404") {
        val eagles = team()
        authenticatedApiTest(teamsAndRoster(teams = listOf(eagles))) { client ->
            val response =
                client.delete(
                    "/api/v1/seasons/${SEASON_ID.value}/teams/${eagles.id.value}/roster/${UUID.randomUUID()}",
                )
            response.status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /seasons/{sid}/teams returns 401 without an authenticated session") {
        apiTest { client ->
            val response =
                client.post("/api/v1/seasons/${SEASON_ID.value}/teams") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateTeamRequest(ownerName = "anon", teamName = "anon"))
                }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("PUT /seasons/{sid}/teams/{tid} returns 401 without an authenticated session") {
        val eagles = team()
        apiTest(teamsAndRoster(teams = listOf(eagles))) { client ->
            val response =
                client.put("/api/v1/seasons/${SEASON_ID.value}/teams/${eagles.id.value}") {
                    contentType(ContentType.Application.Json)
                    setBody(UpdateTeamRequest(teamName = "anon"))
                }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("POST /seasons/{sid}/teams/{tid}/roster returns 401 without an authenticated session") {
        val eagles = team()
        apiTest(teamsAndRoster(teams = listOf(eagles))) { client ->
            val response =
                client.post("/api/v1/seasons/${SEASON_ID.value}/teams/${eagles.id.value}/roster") {
                    contentType(ContentType.Application.Json)
                    setBody(AddToRosterRequest(golferId = GolferId(UUID.randomUUID())))
                }
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("DELETE /seasons/{sid}/teams/{tid}/roster/{golferId} returns 401 without an authenticated session") {
        val eagles = team()
        apiTest(teamsAndRoster(teams = listOf(eagles))) { client ->
            val response =
                client.delete(
                    "/api/v1/seasons/${SEASON_ID.value}/teams/${eagles.id.value}/roster/${UUID.randomUUID()}",
                )
            response.status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /seasons/{sid}/rosters returns the precomputed roster view") {
        val eagles = team(teamName = "Eagles")
        val hawks = team(teamName = "Hawks")
        val view =
            listOf(
                RosterViewTeam(
                    teamId = eagles.id,
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
})
