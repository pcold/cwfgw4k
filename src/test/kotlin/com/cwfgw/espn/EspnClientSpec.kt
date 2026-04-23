package com.cwfgw.espn

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.comparables.shouldBeLessThanOrEqualTo
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import java.time.LocalDate

private data class CompetitorFixture(
    val id: String,
    val name: String,
    val order: Int,
    val score: String,
    val statusId: String = "1",
    val roundScores: List<Int> = listOf(70, 70, 70, 70),
) {
    fun json(): String {
        val linescores = roundScores.joinToString(",") { """{"value":$it.0}""" }
        return """
            {
                "id": "$id",
                "order": $order,
                "score": "$score",
                "athlete": {"displayName": "$name"},
                "linescores": [$linescores],
                "status": {"type": {"id": "$statusId"}}
            }
            """.trimIndent()
    }
}

private fun competitor(
    id: String,
    name: String,
    order: Int,
    score: String,
): String = CompetitorFixture(id, name, order, score).json()

private fun teamCompetitor(
    id: String,
    teamName: String,
    order: Int,
    score: String,
    roundScores: List<Int> = listOf(62, 68, 67, 67),
): String {
    val linescores = roundScores.joinToString(",") { """{"value":$it.0}""" }
    return """
        {
            "id": "$id",
            "type": "team",
            "order": $order,
            "score": "$score",
            "team": {"displayName": "$teamName"},
            "linescores": [$linescores],
            "status": {"type": {"id": "1"}}
        }
        """.trimIndent()
}

private fun scoreboard(
    eventId: String = "401580123",
    eventName: String = "The Players Championship",
    completed: Boolean = true,
    competitors: List<String> = emptyList(),
): String =
    """
    {
      "events": [{
        "id": "$eventId",
        "name": "$eventName",
        "status": {"type": {"completed": $completed}},
        "competitions": [{"competitors": [${competitors.joinToString(",")}]}]
      }]
    }
    """.trimIndent()

// Build a (scoreStr, scoreToPar) pair consistently so generated fixtures match the real parser's round-trip.
private data class ScoreFixture(val scoreStr: String, val scoreToPar: Int)

private fun arbScore(): Arb<ScoreFixture> =
    Arb.int(-20..20).map { n ->
        val str =
            when {
                n == 0 -> "E"
                n > 0 -> "+$n"
                else -> n.toString()
            }
        ScoreFixture(scoreStr = str, scoreToPar = n)
    }

private fun arbCompetitors(): Arb<List<EspnCompetitor>> =
    Arb.list(arbScore(), 1..13).map { scores ->
        scores.mapIndexed { idx, score ->
            EspnCompetitor(
                espnId = "c-$idx",
                name = "Player $idx",
                order = idx + 1,
                scoreStr = score.scoreStr,
                scoreToPar = score.scoreToPar,
                totalStrokes = null,
                roundScores = emptyList(),
                position = 0,
                status = EspnStatus.Active,
                isTeamPartner = false,
                pairKey = null,
            )
        }
    }

class EspnClientSpec : FunSpec({

    // ----- parseScoreboard: worked examples -----

    test("parseScoreboard reads tournament id, name, and completed status") {
        val json =
            scoreboard(
                eventId = "401580999",
                eventName = "Masters Tournament",
                competitors = listOf(competitor("1", "Tiger Woods", 1, "-10")),
            )

        val t = parseScoreboard(json).single()

        t.espnId shouldBe "401580999"
        t.name shouldBe "Masters Tournament"
        t.completed shouldBe true
    }

    test("parseScoreboard reads competitor fields including totalStrokes from linescores") {
        val json =
            scoreboard(
                competitors =
                    listOf(
                        CompetitorFixture(
                            id = "42",
                            name = "Scottie Scheffler",
                            order = 1,
                            score = "-15",
                            roundScores = listOf(65, 67, 68, 66),
                        ).json(),
                    ),
            )

        val c = parseScoreboard(json).single().competitors.single()

        c.espnId shouldBe "42"
        c.name shouldBe "Scottie Scheffler"
        c.scoreStr shouldBe "-15"
        c.scoreToPar shouldBe -15
        c.totalStrokes shouldBe 266
        c.roundScores shouldContainExactly listOf(65, 67, 68, 66)
    }

    test("unique scores get sequential positions") {
        val json =
            scoreboard(
                competitors =
                    listOf(
                        competitor("1", "Player A", 1, "-10"),
                        competitor("2", "Player B", 2, "-8"),
                        competitor("3", "Player C", 3, "-6"),
                    ),
            )

        parseScoreboard(json).single().competitors.map { it.position } shouldContainExactly listOf(1, 2, 3)
    }

    test("tied scores share position and the next slot skips the shared ones") {
        val json =
            scoreboard(
                competitors =
                    listOf(
                        competitor("1", "Player A", 1, "-10"),
                        competitor("2", "Player B", 2, "-8"),
                        competitor("3", "Player C", 3, "-8"),
                        competitor("4", "Player D", 4, "-6"),
                    ),
            )

        parseScoreboard(json).single().competitors.map { it.name to it.position } shouldContainExactly
            listOf("Player A" to 1, "Player B" to 2, "Player C" to 2, "Player D" to 4)
    }

    test("three-way tie shares position and the fourth competitor skips to position 4") {
        val json =
            scoreboard(
                competitors =
                    listOf(
                        competitor("1", "Player A", 1, "-10"),
                        competitor("2", "Player B", 2, "-7"),
                        competitor("3", "Player C", 3, "-7"),
                        competitor("4", "Player D", 4, "-7"),
                        competitor("5", "Player E", 5, "-5"),
                    ),
            )

        parseScoreboard(json).single().competitors.map { it.name to it.position } shouldContainExactly
            listOf(
                "Player A" to 1,
                "Player B" to 2,
                "Player C" to 2,
                "Player D" to 2,
                "Player E" to 5,
            )
    }

    test("positions follow score when ESPN's order field lags live rank") {
        val json =
            scoreboard(
                competitors =
                    listOf(
                        competitor("1", "Midfielder", 1, "-3"),
                        competitor("2", "Even Steven", 2, "E"),
                        competitor("3", "Leader", 3, "-9"),
                        competitor("4", "Runner Up", 4, "-8"),
                    ),
            )

        parseScoreboard(json).single().competitors.map { it.name to it.position } shouldContainExactly
            listOf(
                "Leader" to 1,
                "Runner Up" to 2,
                "Midfielder" to 3,
                "Even Steven" to 4,
            )
    }

    test("a competitor with no score at all sinks to the bottom of the leaderboard") {
        val scoredJson = competitor("1", "Leader", 1, "-5")
        val noScoreJson =
            """
            {
                "id": "2",
                "order": 2,
                "athlete": {"displayName": "Unknown Score"},
                "linescores": [],
                "status": {"type": {"id": "1"}}
            }
            """.trimIndent()
        val json = scoreboard(competitors = listOf(scoredJson, noScoreJson))

        val competitors = parseScoreboard(json).single().competitors
        competitors.first().name shouldBe "Leader"
        competitors.last().name shouldBe "Unknown Score"
        competitors.last().position shouldBe 2
    }

    test("even par E is parsed as scoreToPar = 0") {
        val json = scoreboard(competitors = listOf(competitor("1", "Even Player", 1, "E")))

        parseScoreboard(json).single().competitors.single().scoreToPar shouldBe 0
    }

    test("positive score is parsed without the plus sign") {
        val json = scoreboard(competitors = listOf(competitor("1", "Over Par", 1, "+3")))

        parseScoreboard(json).single().competitors.single().scoreToPar shouldBe 3
    }

    // ----- status handling -----

    test("active status (1) has madeCut = true") {
        val json = scoreboard(competitors = listOf(competitor("1", "Active", 1, "-1")))

        val c = parseScoreboard(json).single().competitors.single()
        c.status shouldBe EspnStatus.Active
        c.madeCut shouldBe true
    }

    test("cut status (2) has madeCut = false") {
        val json =
            scoreboard(
                competitors =
                    listOf(
                        CompetitorFixture(
                            id = "1",
                            name = "Cut Player",
                            order = 1,
                            score = "+5",
                            statusId = "2",
                            roundScores = listOf(75, 76),
                        ).json(),
                    ),
            )

        val c = parseScoreboard(json).single().competitors.single()
        c.status shouldBe EspnStatus.Cut
        c.madeCut shouldBe false
    }

    test("withdrawn status (3) has madeCut = false") {
        val json =
            scoreboard(
                competitors =
                    listOf(
                        CompetitorFixture(
                            id = "1",
                            name = "WD Player",
                            order = 1,
                            score = "+2",
                            statusId = "3",
                            roundScores = listOf(72),
                        ).json(),
                    ),
            )

        val c = parseScoreboard(json).single().competitors.single()
        c.status shouldBe EspnStatus.Withdrawn
        c.madeCut shouldBe false
    }

    test("unknown status codes decode to EspnStatus.Unknown and madeCut = false") {
        val json =
            scoreboard(
                competitors =
                    listOf(
                        CompetitorFixture(
                            id = "1",
                            name = "Mystery",
                            order = 1,
                            score = "E",
                            statusId = "99",
                        ).json(),
                    ),
            )

        val c = parseScoreboard(json).single().competitors.single()
        c.status shouldBe EspnStatus.Unknown
        c.madeCut shouldBe false
    }

    // ----- malformed competitor rows -----

    test("a competitor row missing both team.displayName and athlete.displayName is dropped") {
        val ghostRow =
            """
            {
                "id": "1",
                "order": 1,
                "score": "-5",
                "linescores": [],
                "status": {"type": {"id": "1"}}
            }
            """.trimIndent()
        val json = scoreboard(competitors = listOf(ghostRow, competitor("2", "Real Player", 2, "-3")))

        val competitors = parseScoreboard(json).single().competitors
        competitors.size shouldBe 1
        competitors.single().name shouldBe "Real Player"
    }

    test("a competitor row missing an id is dropped") {
        val ghostRow =
            """
            {
                "order": 1,
                "score": "-5",
                "athlete": {"displayName": "No Id"},
                "linescores": [],
                "status": {"type": {"id": "1"}}
            }
            """.trimIndent()
        val json = scoreboard(competitors = listOf(ghostRow, competitor("2", "Real Player", 2, "-3")))

        val competitors = parseScoreboard(json).single().competitors
        competitors.size shouldBe 1
        competitors.single().name shouldBe "Real Player"
    }

    // ----- team events -----

    test("team entry expands into two partner rows sharing position, score, and pairKey") {
        val json =
            scoreboard(
                eventName = "Zurich Classic",
                competitors = listOf(teamCompetitor("131066", "Novak/Griffin", 1, "-28")),
            )

        val tournament = parseScoreboard(json).single()

        tournament.isTeamEvent shouldBe true
        tournament.competitors.size shouldBe 2
        tournament.competitors.map { it.name } shouldContainExactly listOf("Novak", "Griffin")
        tournament.competitors.all { it.isTeamPartner } shouldBe true
        tournament.competitors.all { it.position == 1 } shouldBe true
        tournament.competitors.all { it.scoreToPar == -28 } shouldBe true
        tournament.competitors.map { it.espnId }.distinct().size shouldBe 2
        tournament.competitors.map { it.pairKey }.distinct() shouldContainExactly listOf("team:131066")
    }

    test("team-event positions advance by team count, not partner count") {
        val json =
            scoreboard(
                eventName = "Zurich Classic",
                competitors =
                    listOf(
                        teamCompetitor("1", "Novak/Griffin", 1, "-28"),
                        teamCompetitor("2", "Smith/Jones", 2, "-28"),
                        teamCompetitor("3", "Alpha/Beta", 3, "-25"),
                    ),
            )

        val competitors = parseScoreboard(json).single().competitors
        competitors.count { it.position == 1 } shouldBe 4
        competitors.count { it.position == 3 } shouldBe 2
    }

    test("non-team event does not mark isTeamEvent") {
        val json = scoreboard(competitors = listOf(competitor("1", "Scottie Scheffler", 1, "-10")))

        val tournament = parseScoreboard(json).single()
        tournament.isTeamEvent shouldBe false
        tournament.competitors.single().pairKey.shouldBeNull()
    }

    // ----- multi-event + empty -----

    test("parseScoreboard returns multiple tournaments from a multi-event response") {
        val json =
            """
            {
              "events": [
                {
                  "id": "100",
                  "name": "Event One",
                  "status": {"type": {"completed": true}},
                  "competitions": [{"competitors": [${competitor("1", "Player A", 1, "-5")}]}]
                },
                {
                  "id": "200",
                  "name": "Event Two",
                  "status": {"type": {"completed": false}},
                  "competitions": [{"competitors": [${competitor("2", "Player B", 1, "-3")}]}]
                }
              ]
            }
            """.trimIndent()

        val tournaments = parseScoreboard(json)
        tournaments.map { it.name } shouldContainExactly listOf("Event One", "Event Two")
        tournaments.map { it.completed } shouldContainExactly listOf(true, false)
    }

    test("parseScoreboard on an empty events array returns an empty list") {
        parseScoreboard("""{"events": []}""").shouldBeEmpty()
    }

    // ----- assignPositions: algebraic invariants -----

    test("assignPositions: output size equals input size") {
        checkAll(arbCompetitors()) { competitors ->
            assignPositions(competitors).size shouldBe competitors.size
        }
    }

    test("assignPositions: positions start at 1 for the best-scoring competitor") {
        checkAll(arbCompetitors()) { competitors ->
            assignPositions(competitors).minOf { it.position } shouldBe 1
        }
    }

    test("assignPositions: any two competitors with the same scoreStr end up with the same position") {
        checkAll(arbCompetitors()) { competitors ->
            val byScore = assignPositions(competitors).groupBy { it.scoreStr }
            byScore.values.forEach { sharedScoreGroup ->
                sharedScoreGroup.map { it.position }.distinct().size shouldBe 1
            }
        }
    }

    test("assignPositions: a strictly better score never gets a strictly larger position") {
        checkAll(arbCompetitors()) { competitors ->
            val positioned = assignPositions(competitors)
            val pairs = positioned.flatMap { a -> positioned.map { b -> a to b } }
            pairs.forEach { (a, b) ->
                val aScore = a.scoreToPar
                val bScore = b.scoreToPar
                if (aScore != null && bScore != null && aScore < bScore) {
                    a.position shouldBeLessThanOrEqualTo b.position
                }
            }
        }
    }

    // ----- fetchScoreboard: HTTP layer via MockEngine -----

    test("fetchScoreboard issues a GET with dates=YYYYMMDD and decodes a 200 response") {
        var requestedUrl: String? = null
        val mockEngine =
            MockEngine { request ->
                requestedUrl = request.url.toString()
                respond(
                    content = scoreboard(competitors = listOf(competitor("1", "Rory", 1, "-10"))),
                    status = HttpStatusCode.OK,
                    headers = headersOf("Content-Type", "application/json"),
                )
            }
        val client = EspnClient(HttpClient(mockEngine), baseUrl = "https://mock.test/scoreboard")

        val tournaments = client.fetchScoreboard(LocalDate.parse("2026-04-15"))

        requestedUrl shouldBe "https://mock.test/scoreboard?dates=20260415"
        tournaments.single().competitors.single().name shouldBe "Rory"
    }

    test("fetchScoreboard throws EspnUpstreamException with the upstream status on non-200") {
        val mockEngine =
            MockEngine { _ -> respondError(HttpStatusCode.InternalServerError, "upstream boom") }
        val client = EspnClient(HttpClient(mockEngine), baseUrl = "https://mock.test/scoreboard")

        val thrown =
            shouldThrow<EspnUpstreamException> {
                client.fetchScoreboard(LocalDate.parse("2026-04-15"))
            }
        thrown.status shouldBe HttpStatusCode.InternalServerError.value
    }
})
