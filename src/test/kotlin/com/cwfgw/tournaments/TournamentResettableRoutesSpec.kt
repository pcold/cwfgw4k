package com.cwfgw.tournaments

import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.apiTest
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.regularUserApiTest
import com.cwfgw.testing.seedTournaments
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))

private fun tournament(
    name: String,
    startDate: String,
    status: TournamentStatus,
): Tournament =
    Tournament(
        id = TournamentId(UUID.randomUUID()),
        pgaTournamentId = null,
        name = name,
        seasonId = SEASON_ID,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(startDate).plusDays(3),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = BigDecimal("1.0000"),
        week = null,
        isTeamEvent = false,
        createdAt = Instant.parse("2026-04-01T00:00:00Z"),
    )

private const val RESETTABLE_PATH = "/api/v1/tournaments/resettable"

class TournamentResettableRoutesSpec : FunSpec({

    test("GET /api/v1/tournaments/resettable lists the season's completed tournaments reset would accept") {
        val sony = tournament("Sony Open", "2026-01-15", TournamentStatus.Completed)
        val masters = tournament("The Masters", "2026-04-09", TournamentStatus.Completed)
        val pga = tournament("PGA Championship", "2026-05-14", TournamentStatus.Upcoming)

        authenticatedApiTest({ seedTournaments(sony, masters, pga) }) { client ->
            val response = client.get("$RESETTABLE_PATH?season_id=${SEASON_ID.value}")
            response.status shouldBe HttpStatusCode.OK
            response.body<List<Tournament>>().map(Tournament::name) shouldContainExactly listOf("The Masters")
        }
    }

    test("GET /api/v1/tournaments/resettable returns 401 without an authenticated session") {
        apiTest { client ->
            client.get("$RESETTABLE_PATH?season_id=${SEASON_ID.value}").status shouldBe HttpStatusCode.Unauthorized
        }
    }

    test("GET /api/v1/tournaments/resettable returns 403 for a user who is not an admin") {
        regularUserApiTest { client ->
            client.get("$RESETTABLE_PATH?season_id=${SEASON_ID.value}").status shouldBe HttpStatusCode.Forbidden
        }
    }

    test("GET /api/v1/tournaments/resettable without a season_id returns 400") {
        authenticatedApiTest { client ->
            client.get(RESETTABLE_PATH).status shouldBe HttpStatusCode.BadRequest
        }
    }
})
