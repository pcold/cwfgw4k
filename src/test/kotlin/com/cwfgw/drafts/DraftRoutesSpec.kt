package com.cwfgw.drafts

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.TeamId
import com.cwfgw.testing.ApiFixture
import com.cwfgw.testing.apiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
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

private fun withDraft(
    drafts: List<Draft> = emptyList(),
    picks: List<DraftPick> = emptyList(),
): ApiFixture.() -> Unit =
    {
        val teamService = this.teamService
        val fake = FakeDraftRepository(initialDrafts = drafts, initialPicks = picks)
        draftService = DraftService(fake, teamService)
    }

class DraftRoutesSpec : FunSpec({

    test("GET /draft returns 200 with the draft") {
        val d = draft()
        apiTest(withDraft(listOf(d))) { client ->
            val response = client.get("/api/v1/seasons/${SEASON_ID.value}/draft")
            response.status shouldBe HttpStatusCode.OK
            response.body<Draft>() shouldBe d
        }
    }

    test("GET /draft returns 404 when no draft exists") {
        apiTest { client ->
            client.get("/api/v1/seasons/${SEASON_ID.value}/draft").status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /draft creates and returns 201") {
        apiTest { client ->
            val response =
                client.post("/api/v1/seasons/${SEASON_ID.value}/draft") {
                    contentType(ContentType.Application.Json)
                    setBody(CreateDraftRequest())
                }
            response.status shouldBe HttpStatusCode.Created
        }
    }

    test("POST /draft/start returns 409 when draft is already in_progress") {
        val d = draft(status = "in_progress")
        apiTest(withDraft(listOf(d))) { client ->
            client.post("/api/v1/seasons/${SEASON_ID.value}/draft/start").status shouldBe HttpStatusCode.Conflict
        }
    }

    test("POST /draft/start returns 404 when no draft exists") {
        apiTest { client ->
            client.post("/api/v1/seasons/${SEASON_ID.value}/draft/start").status shouldBe HttpStatusCode.NotFound
        }
    }

    test("POST /draft/start transitions a pending draft to in_progress") {
        apiTest(withDraft(listOf(draft(status = "pending")))) { client ->
            val response = client.post("/api/v1/seasons/${SEASON_ID.value}/draft/start")

            response.status shouldBe HttpStatusCode.OK
            response.body<Draft>().status shouldBe "in_progress"
        }
    }

    test("POST /draft/initialize returns 409 when the season has no teams") {
        apiTest(withDraft(listOf(draft(status = "pending")))) { client ->
            client.post("/api/v1/seasons/${SEASON_ID.value}/draft/initialize").status shouldBe HttpStatusCode.Conflict
        }
    }

    test("POST /draft/initialize?rounds={bad} returns 400") {
        apiTest(withDraft(listOf(draft(status = "pending")))) { client ->
            client.post("/api/v1/seasons/${SEASON_ID.value}/draft/initialize?rounds=nope")
                .status shouldBe HttpStatusCode.BadRequest
        }
    }

    test("POST /draft/pick returns 409 when it is not the requested team's turn") {
        val teamA = TeamId(UUID.randomUUID())
        val teamB = TeamId(UUID.randomUUID())
        val d = draft(status = "in_progress")
        val pick = DraftPick(DraftPickId(UUID.randomUUID()), d.id, teamA, null, 1, 1, null)

        apiTest(withDraft(listOf(d), listOf(pick))) { client ->
            val response =
                client.post("/api/v1/seasons/${SEASON_ID.value}/draft/pick") {
                    contentType(ContentType.Application.Json)
                    setBody(MakePickRequest(teamId = teamB, golferId = GolferId(UUID.randomUUID())))
                }
            response.status shouldBe HttpStatusCode.Conflict
        }
    }

    test("GET /draft/picks returns 404 when no draft exists") {
        apiTest { client ->
            client.get("/api/v1/seasons/${SEASON_ID.value}/draft/picks").status shouldBe HttpStatusCode.NotFound
        }
    }

    test("GET /draft/available returns 404 when no draft exists") {
        apiTest { client ->
            client.get("/api/v1/seasons/${SEASON_ID.value}/draft/available").status shouldBe HttpStatusCode.NotFound
        }
    }
})
