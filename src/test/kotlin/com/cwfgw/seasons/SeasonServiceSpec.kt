package com.cwfgw.seasons

import com.cwfgw.leagues.LeagueId
import com.cwfgw.testing.FakeTransactor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private val LEAGUE_ONE = LeagueId(UUID.fromString("00000000-0000-0000-0000-00000000aaaa"))
private val LEAGUE_TWO = LeagueId(UUID.fromString("00000000-0000-0000-0000-00000000bbbb"))

private fun season(
    id: SeasonId = SeasonId(UUID.randomUUID()),
    leagueId: LeagueId = LEAGUE_ONE,
    name: String = "Season",
    seasonYear: Int = 2026,
    seasonNumber: Int = 1,
): Season =
    Season(
        id = id,
        leagueId = leagueId,
        name = name,
        seasonYear = seasonYear,
        seasonNumber = seasonNumber,
        status = "draft",
        tieFloor = BigDecimal.ONE,
        sideBetAmount = BigDecimal(15),
        maxTeams = 10,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

class SeasonServiceSpec : FunSpec({

    test("list passes both filters through to the repository") {
        val a = season(name = "L1 2026", leagueId = LEAGUE_ONE, seasonYear = 2026)
        val b = season(name = "L2 2026", leagueId = LEAGUE_TWO, seasonYear = 2026)
        val c = season(name = "L1 2025", leagueId = LEAGUE_ONE, seasonYear = 2025)
        val service = SeasonService(FakeSeasonRepository(initial = listOf(a, b, c)), FakeTransactor())

        val byLeague = service.list(leagueId = LEAGUE_ONE, seasonYear = null).map { it.id }
        byLeague shouldContainExactlyInAnyOrder listOf(a.id, c.id)

        val byYear = service.list(leagueId = null, seasonYear = 2026).map { it.id }
        byYear shouldContainExactlyInAnyOrder listOf(a.id, b.id)

        val bothFilters = service.list(leagueId = LEAGUE_ONE, seasonYear = 2025).map { it.id }
        bothFilters shouldContainExactly listOf(c.id)
    }

    test("create passes the request through and defaults status to draft") {
        val newId = SeasonId(UUID.fromString("00000000-0000-0000-0000-00000000cccc"))
        val newTime = Instant.parse("2026-03-15T12:00:00Z")
        val fake = FakeSeasonRepository(idFactory = { newId }, clock = { newTime })
        val service = SeasonService(fake, FakeTransactor())

        val created =
            service.create(
                CreateSeasonRequest(leagueId = LEAGUE_ONE, name = "Fresh", seasonYear = 2026),
            )

        created.id shouldBe newId
        created.status shouldBe "draft"
        created.updatedAt shouldBe newTime
    }

    test("update delegates to the repository and returns null for unknown id") {
        val existing = season(name = "Orig")
        val fake = FakeSeasonRepository(initial = listOf(existing), clock = { Instant.parse("2026-04-01T00:00:00Z") })
        val service = SeasonService(fake, FakeTransactor())

        val updated = service.update(existing.id, UpdateSeasonRequest(status = "active"))
        updated?.status shouldBe "active"
        updated?.name shouldBe "Orig"

        service.update(SeasonId(UUID.randomUUID()), UpdateSeasonRequest(name = "Nobody")).shouldBeNull()
    }

    test("getRules returns defaults for a season with no custom rules") {
        val existing = season()
        val service = SeasonService(FakeSeasonRepository(initial = listOf(existing)), FakeTransactor())

        val rules = service.getRules(existing.id)

        rules?.payouts shouldBe SeasonRules.DEFAULT_PAYOUTS
        rules?.sideBetRounds shouldBe SeasonRules.DEFAULT_SIDE_BET_ROUNDS
    }

    test("getRules returns null for unknown season") {
        val service = SeasonService(FakeSeasonRepository(), FakeTransactor())
        service.getRules(SeasonId(UUID.randomUUID())).shouldBeNull()
    }
})
