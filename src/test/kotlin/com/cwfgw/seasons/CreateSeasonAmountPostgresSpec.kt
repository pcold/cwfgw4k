package com.cwfgw.seasons

import com.cwfgw.db.Transactor
import com.cwfgw.http.ErrorBody
import com.cwfgw.http.appJson
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueId
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.testing.authenticatedApiTest
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.TextContent
import java.math.BigDecimal
import java.util.UUID

// Swapped for the raw amount after encoding: the serializer's toPlainString would allocate 1E+1000000000 in full.
private val PLACEHOLDER = BigDecimal("424242.4242")

private class RuleAmountField(
    val label: String,
    val holding: (BigDecimal) -> SeasonRules,
    val readBack: (SeasonRules) -> BigDecimal?,
)

private val RULE_AMOUNT_FIELDS =
    listOf(
        RuleAmountField(
            label = "payouts",
            holding = { SeasonRules.defaults().copy(payouts = listOf(BigDecimal(18), it)) },
            readBack = { it.payouts.getOrNull(1) },
        ),
        RuleAmountField(
            label = "tie floor",
            holding = { SeasonRules.defaults().copy(tieFloor = it) },
            readBack = SeasonRules::tieFloor,
        ),
        RuleAmountField(
            label = "side bet amount",
            holding = { SeasonRules.defaults().copy(sideBetAmount = it) },
            readBack = SeasonRules::sideBetAmount,
        ),
    )

private val PAST_FOUR_PLACES = listOf("12.34565", "15.00000", "1e-20000", "0E-20000", "1." + "0".repeat(16384))

private suspend fun HttpClient.createSeason(
    leagueId: LeagueId,
    field: RuleAmountField,
    rawAmount: String,
): HttpResponse {
    val request = CreateSeasonRequest(leagueId, name = "Spring", seasonYear = 2026, rules = field.holding(PLACEHOLDER))
    val json = appJson.encodeToString(CreateSeasonRequest.serializer(), request)
    return post("/api/v1/seasons") {
        setBody(TextContent(json.replace(PLACEHOLDER.toPlainString(), rawAmount), ContentType.Application.Json))
    }
}

private fun refusesEveryField(
    tx: Transactor,
    leagueId: LeagueId,
    rawAmounts: List<String>,
    message: (String) -> String,
) = authenticatedApiTest({ seasonService = SeasonService(SeasonRepository(), tx) }) { client ->
    // One comparison so a red run names every failing case: assertSoftly loses its collector across the client's
    // thread hops.
    val cases = rawAmounts.flatMap { rawAmount -> RULE_AMOUNT_FIELDS.map { field -> field to rawAmount } }
    val answered =
        cases.associate { (field, rawAmount) ->
            val response = client.createSeason(leagueId, field, rawAmount)
            "${field.label} ${rawAmount.take(12)}" to "${response.status.value} ${response.bodyAsText().take(120)}"
        }
    val refused =
        cases.associate { (field, rawAmount) ->
            val refusal = appJson.encodeToString(ErrorBody.serializer(), ErrorBody(message(field.label)))
            "${field.label} ${rawAmount.take(12)}" to "${HttpStatusCode.BadRequest.value} $refusal"
        }
    answered shouldBe refused
    tx.get { SeasonRepository().findAll(leagueId = null, seasonYear = null) }.shouldBeEmpty()
}

class CreateSeasonAmountPostgresSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    var leagueId = LeagueId(UUID.randomUUID())

    beforeEach {
        leagueId = tx.update { LeagueRepository().create(CreateLeagueRequest(name = "Castlewood Fantasy Golf")) }.id
    }

    test("an amount past four decimal places answers 400 in every rule field and writes nothing to Postgres") {
        refusesEveryField(tx, leagueId, PAST_FOUR_PLACES) { "$it must have at most 4 decimal places" }
    }

    test("an amount past the column's magnitude answers 400 in every rule field and writes nothing to Postgres") {
        refusesEveryField(tx, leagueId, listOf("1E+1000000000")) { "$it must be less than 1,000,000" }
    }

    test("a zero with an exponent past what Postgres parses as text is written as zero in every rule field") {
        authenticatedApiTest({ seasonService = SeasonService(SeasonRepository(), tx) }) { client ->
            RULE_AMOUNT_FIELDS.forEach { field ->
                val response = client.createSeason(leagueId, field, "0E+2000000000")
                withClue(field.label) { response.status shouldBe HttpStatusCode.Created }
                val seasonId = response.body<Season>().id
                val rules = tx.get { SeasonRepository().getRules(seasonId) }.shouldNotBeNull()
                field.readBack(rules) shouldBe BigDecimal("0.0000")
            }
        }
    }
})
