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
import java.math.BigDecimal
import java.util.UUID

private val LEAGUE = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

private fun request(rules: SeasonRules?): CreateSeasonRequest =
    CreateSeasonRequest(leagueId = LEAGUE, name = "Spring", seasonYear = 2026, rules = rules)

class CreateSeasonViolationSpec : FunSpec({

    test("rules with payout places and distinct side-bet rounds have no violation") {
        request(SeasonRules.defaults()).violation().shouldBeNull()
    }

    test("a season without rules has no violation, since the defaults then apply") {
        request(rules = null).violation().shouldBeNull()
    }

    test("rules with no payout places are NoPayoutPlaces") {
        val noPlaces = SeasonRules.defaults().copy(payouts = emptyList())
        request(noPlaces).violation() shouldBe CreateSeasonViolation.NoPayoutPlaces
    }

    test("rules with no side-bet rounds are NoSideBetRounds") {
        val noRounds = SeasonRules.defaults().copy(sideBetRounds = emptyList())
        request(noRounds).violation() shouldBe CreateSeasonViolation.NoSideBetRounds
    }

    test("a side-bet round listed twice is DuplicateSideBetRound") {
        val repeated = SeasonRules.defaults().copy(sideBetRounds = listOf(5, 6, 5))
        request(repeated).violation() shouldBe CreateSeasonViolation.DuplicateSideBetRound
    }

    test("a negative flat side-bet amount is NegativeSideBetAmount even without rules") {
        val negative = request(rules = null).copy(sideBetAmount = BigDecimal("-0.01"))
        negative.violation() shouldBe CreateSeasonViolation.NegativeSideBetAmount
    }

    test("a negative side-bet amount in rules is NegativeSideBetAmount") {
        request(SeasonRules.defaults().copy(sideBetAmount = BigDecimal("-15"))).violation() shouldBe
            CreateSeasonViolation.NegativeSideBetAmount
    }

    test("a negative flat side-bet amount is NegativeSideBetAmount even when rules carry the written amount") {
        val shadowed = request(SeasonRules.defaults()).copy(sideBetAmount = BigDecimal("-15"))
        shadowed.violation() shouldBe CreateSeasonViolation.NegativeSideBetAmount
    }

    test("a zero side-bet amount has no violation, since zero is the season with no side bets") {
        request(SeasonRules.defaults().copy(sideBetAmount = BigDecimal.ZERO)).violation().shouldBeNull()
        request(rules = null).copy(sideBetAmount = BigDecimal.ZERO).violation().shouldBeNull()
    }

    test("POST /api/v1/seasons refuses a violation with 400, its message, and no season written") {
        val fake = FakeSeasonRepository()
        authenticatedApiTest({ seasonService = SeasonService(fake, FakeTransactor()) }) { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(request(SeasonRules.defaults().copy(payouts = emptyList())))
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe "payouts must list at least one place"
            client.get("/api/v1/seasons").body<List<Season>>().shouldBeEmpty()
        }
    }

    test("POST /api/v1/seasons refuses a repeated side-bet round with 400 and its message") {
        authenticatedApiTest { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(request(SeasonRules.defaults().copy(sideBetRounds = listOf(5, 5))))
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe "side bet rounds must not repeat a round"
        }
    }

    test("POST /api/v1/seasons refuses empty side-bet rounds with 400 and its message") {
        authenticatedApiTest { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(request(SeasonRules.defaults().copy(sideBetRounds = emptyList())))
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe "side bet rounds must list at least one round"
        }
    }

    test("POST /api/v1/seasons refuses a negative flat side-bet amount with 400, its message, and no season written") {
        authenticatedApiTest { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(request(rules = null).copy(sideBetAmount = BigDecimal("-15")))
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe "side bet amount must not be negative"
            client.get("/api/v1/seasons").body<List<Season>>().shouldBeEmpty()
        }
    }

    test("POST /api/v1/seasons refuses a negative side-bet amount in rules with 400 and its message") {
        authenticatedApiTest { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(request(SeasonRules.defaults().copy(sideBetAmount = BigDecimal("-15"))))
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe "side bet amount must not be negative"
        }
    }
})
