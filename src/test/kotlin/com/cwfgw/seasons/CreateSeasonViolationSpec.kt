package com.cwfgw.seasons

import com.cwfgw.http.ErrorBody
import com.cwfgw.leagues.LeagueId
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.authenticatedApiTest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import java.util.UUID

private val LEAGUE = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

private fun request(
    name: String = "Spring",
    rules: SeasonRules? = SeasonRules.defaults(),
): CreateSeasonRequest = CreateSeasonRequest(leagueId = LEAGUE, name = name, seasonYear = 2026, rules = rules)

class CreateSeasonViolationSpec : FunSpec({

    test("a named season with at least one payout place has no violation") {
        request().violation().shouldBeNull()
    }

    test("a season without rules has no violation, since the defaults then apply") {
        request(rules = null).violation().shouldBeNull()
    }

    test("a whitespace-only name is BlankName") {
        request(name = "  ").violation() shouldBe CreateSeasonViolation.BlankName
    }

    test("rules with no payout places are NoPayoutPlaces") {
        val noPlaces = SeasonRules.defaults().copy(payouts = emptyList())
        request(rules = noPlaces).violation() shouldBe CreateSeasonViolation.NoPayoutPlaces
    }

    test("POST /api/v1/seasons refuses a violation with 400, its message, and no season written") {
        val fake = FakeSeasonRepository()
        authenticatedApiTest({ seasonService = SeasonService(fake, FakeTransactor()) }) { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(request(rules = SeasonRules.defaults().copy(payouts = emptyList())))
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe "payouts must list at least one place"
            client.get("/api/v1/seasons").body<List<Season>>().shouldBeEmpty()
        }
    }

    test("POST /api/v1/seasons refuses a blank name with 400 and its message") {
        authenticatedApiTest { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(request(name = ""))
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe "season name must not be blank"
        }
    }
})
