package com.cwfgw.seasons

import com.cwfgw.http.ErrorBody
import com.cwfgw.leagues.LeagueId
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.authenticatedApiTest
import io.kotest.assertions.withClue
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

private fun overflowCases(amount: BigDecimal): List<Pair<CreateSeasonRequest, CreateSeasonViolation>> {
    val rules = SeasonRules.defaults()
    return listOf(
        request(rules.copy(payouts = listOf(BigDecimal(18), amount))) to CreateSeasonViolation.PayoutTooLarge,
        request(rules.copy(tieFloor = amount)) to CreateSeasonViolation.TieFloorTooLarge,
        request(rules.copy(sideBetAmount = amount)) to CreateSeasonViolation.SideBetAmountTooLarge,
    )
}

private fun decimalPlacesCases(amount: BigDecimal): List<Pair<CreateSeasonRequest, CreateSeasonViolation>> {
    val rules = SeasonRules.defaults()
    return listOf(
        request(rules.copy(payouts = listOf(BigDecimal(18), amount))) to
            CreateSeasonViolation.PayoutTooManyDecimalPlaces,
        request(rules.copy(tieFloor = amount)) to CreateSeasonViolation.TieFloorTooManyDecimalPlaces,
        request(rules.copy(sideBetAmount = amount)) to CreateSeasonViolation.SideBetAmountTooManyDecimalPlaces,
    )
}

private fun outOfRangeRefusals(): List<Pair<CreateSeasonRequest, String>> {
    val rules = SeasonRules.defaults()
    val overflow = BigDecimal("1e7")
    return listOf(
        request(rules.copy(payouts = listOf(BigDecimal(-5), BigDecimal(12)))) to "payouts must not be negative",
        request(rules.copy(tieFloor = BigDecimal(-1))) to "tie floor must not be negative",
        request(rules).copy(maxTeams = 0) to "max teams must be at least 1",
        request(rules.copy(payouts = listOf(overflow))) to "payouts must be less than 1,000,000",
        request(rules.copy(tieFloor = overflow)) to "tie floor must be less than 1,000,000",
        request(rules.copy(sideBetAmount = overflow)) to "side bet amount must be less than 1,000,000",
        request(rules.copy(sideBetRounds = listOf(5, 9))) to "side bet rounds must be between 1 and 8",
    )
}

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

    test("rules with no side-bet rounds have no violation, since no rounds is the season with no side bets") {
        request(SeasonRules.defaults().copy(sideBetRounds = emptyList())).violation().shouldBeNull()
    }

    test("a side-bet round outside the draft's rounds is SideBetRoundOutOfRange, and rounds 1 and 8 have none") {
        listOf(listOf(0), listOf(5, 9), listOf(-1), listOf(9, 9)).forEach { rounds ->
            withClue(rounds) {
                request(SeasonRules.defaults().copy(sideBetRounds = rounds)).violation() shouldBe
                    CreateSeasonViolation.SideBetRoundOutOfRange
            }
        }
        request(SeasonRules.defaults().copy(sideBetRounds = listOf(1, 8))).violation().shouldBeNull()
    }

    test("a side-bet round listed twice is DuplicateSideBetRound") {
        val repeated = SeasonRules.defaults().copy(sideBetRounds = listOf(5, 6, 5))
        request(repeated).violation() shouldBe CreateSeasonViolation.DuplicateSideBetRound
    }

    test("a negative side-bet amount in rules is NegativeSideBetAmount") {
        request(SeasonRules.defaults().copy(sideBetAmount = BigDecimal("-15"))).violation() shouldBe
            CreateSeasonViolation.NegativeSideBetAmount
    }

    test("a zero side-bet amount has no violation, since zero is the season with no side bets") {
        request(SeasonRules.defaults().copy(sideBetAmount = BigDecimal.ZERO)).violation().shouldBeNull()
    }

    test("an empty name, or one of nothing but ASCII whitespace, is BlankName") {
        listOf("", "   ", "\t", "\n", " \t\n\r\u000B\u000C ").forEach { blank ->
            request(rules = null).copy(name = blank).violation() shouldBe CreateSeasonViolation.BlankName
        }
    }

    test("a padded name, a lone v, and a no-break space have no violation, matching the CHECK") {
        listOf("\tSpring \n", "v", " ").forEach { name ->
            request(rules = null).copy(name = name).violation().shouldBeNull()
        }
    }

    test("a negative payout place is NegativePayout, and a zero one has no violation") {
        val charged = SeasonRules.defaults().copy(payouts = listOf(BigDecimal(18), BigDecimal("-0.01")))
        request(charged).violation() shouldBe CreateSeasonViolation.NegativePayout
        request(SeasonRules.defaults().copy(payouts = listOf(BigDecimal.ZERO))).violation().shouldBeNull()
    }

    test("a negative tie floor in rules is NegativeTieFloor, and a zero one has no violation") {
        val floorless = request(SeasonRules.defaults().copy(tieFloor = BigDecimal("-1")))
        floorless.violation() shouldBe CreateSeasonViolation.NegativeTieFloor
        request(SeasonRules.defaults().copy(tieFloor = BigDecimal.ZERO)).violation().shouldBeNull()
    }

    test("max teams below 1 is MaxTeamsBelowOne, and 1 has no violation") {
        listOf(0, -3).forEach { maxTeams ->
            request(rules = null).copy(maxTeams = maxTeams).violation() shouldBe CreateSeasonViolation.MaxTeamsBelowOne
        }
        request(rules = null).copy(maxTeams = 1).violation().shouldBeNull()
    }

    test("each rule amount NUMERIC(10,4) would round to 10^6 or past is its TooLarge variant") {
        listOf("999999.99995", "1e7", "1E+1000000000").map(::BigDecimal).forEach { amount ->
            overflowCases(amount).forEach { (overflowing, variant) -> overflowing.violation() shouldBe variant }
        }
    }

    test("each rule amount at the largest NUMERIC(10,4) holds, 999999.9999, has no violation") {
        overflowCases(BigDecimal("999999.9999")).forEach { (largest, _) -> largest.violation().shouldBeNull() }
    }

    test("each rule amount past four decimal places, trailing zeros counted, is its TooManyDecimalPlaces variant") {
        listOf("12.34565", "15.00000", "1e-20000", "0E-20000", "1." + "0".repeat(16384)).map(::BigDecimal)
            .forEach { amount ->
                decimalPlacesCases(amount).forEach { (tooFine, variant) -> tooFine.violation() shouldBe variant }
            }
    }

    test("each rule amount at four places or fewer, a negative scale included, has no violation") {
        listOf("12.3456", "1E+5", "0E+2000000000").map(::BigDecimal).forEach { amount ->
            decimalPlacesCases(amount).forEach { (fineEnough, _) -> fineEnough.violation().shouldBeNull() }
        }
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

    test("POST /api/v1/seasons creates a season with no side-bet rounds, and its rules read back none") {
        authenticatedApiTest { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(request(SeasonRules.defaults().copy(sideBetRounds = emptyList())))
                }

            response.status shouldBe HttpStatusCode.Created
            val season = response.body<Season>()
            client.get("/api/v1/seasons/${season.id.value}/rules").body<SeasonRules>().sideBetRounds.shouldBeEmpty()
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

    test("POST /api/v1/seasons refuses a blank name with 400, its message, and no season written") {
        authenticatedApiTest { client ->
            val response =
                client.post("/api/v1/seasons") {
                    contentType(ContentType.Application.Json)
                    setBody(request(rules = null).copy(name = "   "))
                }

            response.status shouldBe HttpStatusCode.BadRequest
            response.body<ErrorBody>().error shouldBe "season name must not be blank"
            client.get("/api/v1/seasons").body<List<Season>>().shouldBeEmpty()
        }
    }

    test("POST /api/v1/seasons refuses each out-of-range rule value with 400, its message, and no season written") {
        authenticatedApiTest { client ->
            outOfRangeRefusals().forEach { (outOfRange, message) ->
                val response =
                    client.post("/api/v1/seasons") {
                        contentType(ContentType.Application.Json)
                        setBody(outOfRange)
                    }

                withClue(message) {
                    response.status shouldBe HttpStatusCode.BadRequest
                    response.body<ErrorBody>().error shouldBe message
                }
            }
            client.get("/api/v1/seasons").body<List<Season>>().shouldBeEmpty()
        }
    }
})
