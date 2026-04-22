package com.cwfgw.seasons

import com.cwfgw.jooq.tables.references.SEASON_RULE_PAYOUTS
import com.cwfgw.jooq.tables.references.SEASON_RULE_SIDE_BET_ROUNDS
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueId
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.UUID

class SeasonRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = SeasonRepository(postgres.dsl)
    val leagueRepo = LeagueRepository(postgres.dsl)
    var castlewoodId = LeagueId(UUID.randomUUID())

    beforeEach {
        castlewoodId = leagueRepo.create(CreateLeagueRequest(name = "Castlewood Fantasy Golf")).id
    }

    test("create uses DB defaults when request omits optional fields") {
        val created =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "2026 Season", seasonYear = 2026),
            )

        created.leagueId shouldBe castlewoodId
        created.name shouldBe "2026 Season"
        created.seasonYear shouldBe 2026
        created.seasonNumber shouldBe 1
        created.status shouldBe "draft"
        created.maxTeams shouldBe 10
        created.tieFloor.compareTo(BigDecimal.ONE) shouldBe 0
        created.sideBetAmount.compareTo(BigDecimal(15)) shouldBe 0
    }

    test("create respects explicitly supplied values") {
        val created =
            repository.create(
                CreateSeasonRequest(
                    leagueId = castlewoodId,
                    name = "Custom",
                    seasonYear = 2027,
                    seasonNumber = 2,
                    maxTeams = 13,
                    tieFloor = BigDecimal("0.5"),
                    sideBetAmount = BigDecimal("20"),
                ),
            )

        created.seasonNumber shouldBe 2
        created.maxTeams shouldBe 13
        created.tieFloor.compareTo(BigDecimal("0.5")) shouldBe 0
        created.sideBetAmount.compareTo(BigDecimal("20")) shouldBe 0
    }

    test("findAll orders by year desc, number desc, created_at desc") {
        val older =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "2025", seasonYear = 2025),
            )
        val newer =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "2026 S1", seasonYear = 2026, seasonNumber = 1),
            )
        val newest =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "2026 S2", seasonYear = 2026, seasonNumber = 2),
            )

        val result = repository.findAll(leagueId = null, seasonYear = null)

        result.map { it.id } shouldContainExactly listOf(newest.id, newer.id, older.id)
    }

    test("findAll filters by league_id") {
        val otherLeague = leagueRepo.create(CreateLeagueRequest(name = "Other League")).id

        val castlewood =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "Castlewood 2026", seasonYear = 2026),
            )
        repository.create(
            CreateSeasonRequest(leagueId = otherLeague, name = "Other 2026", seasonYear = 2026),
        )

        val result = repository.findAll(leagueId = castlewoodId, seasonYear = null)

        result.map { it.id } shouldContainExactly listOf(castlewood.id)
    }

    test("findAll filters by season year") {
        repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "2025", seasonYear = 2025))
        val match =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "2026", seasonYear = 2026),
            )

        val result = repository.findAll(leagueId = null, seasonYear = 2026)

        result.map { it.id } shouldContainExactly listOf(match.id)
    }

    test("findById returns null for unknown id") {
        repository.findById(SeasonId(UUID.randomUUID())).shouldBeNull()
    }

    test("update applies only supplied fields and bumps updated_at") {
        val created =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "Orig", seasonYear = 2026),
            )

        val updated =
            repository.update(
                created.id,
                UpdateSeasonRequest(status = "active", tieFloor = BigDecimal("2")),
            )

        updated.shouldNotBeNull()
        updated.status shouldBe "active"
        updated.tieFloor.compareTo(BigDecimal("2")) shouldBe 0
        updated.name shouldBe "Orig"
        (updated.updatedAt >= created.updatedAt) shouldBe true
    }

    test("update with no fields returns the existing row unchanged") {
        val created =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "Orig", seasonYear = 2026),
            )

        val result = repository.update(created.id, UpdateSeasonRequest())

        result shouldBe created
    }

    test("update returns null for unknown id") {
        repository.update(SeasonId(UUID.randomUUID()), UpdateSeasonRequest(name = "Ghost")).shouldBeNull()
    }

    test("getRules returns defaults when join tables are empty") {
        val created =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "2026", seasonYear = 2026),
            )

        val rules = repository.getRules(created.id)

        rules.shouldNotBeNull()
        rules.payouts shouldBe SeasonRules.DEFAULT_PAYOUTS
        rules.sideBetRounds shouldBe SeasonRules.DEFAULT_SIDE_BET_ROUNDS
        rules.tieFloor.compareTo(BigDecimal.ONE) shouldBe 0
        rules.sideBetAmount.compareTo(BigDecimal(15)) shouldBe 0
    }

    test("getRules returns custom payouts and rounds when seeded in join tables") {
        val created =
            repository.create(
                CreateSeasonRequest(leagueId = castlewoodId, name = "2026", seasonYear = 2026),
            )
        listOf(1 to BigDecimal("50"), 2 to BigDecimal("25"), 3 to BigDecimal("10")).forEach { (pos, amt) ->
            postgres.dsl.insertInto(SEASON_RULE_PAYOUTS)
                .set(SEASON_RULE_PAYOUTS.SEASON_ID, created.id.value)
                .set(SEASON_RULE_PAYOUTS.POSITION, pos)
                .set(SEASON_RULE_PAYOUTS.AMOUNT, amt)
                .execute()
        }
        listOf(3, 4).forEach { round ->
            postgres.dsl.insertInto(SEASON_RULE_SIDE_BET_ROUNDS)
                .set(SEASON_RULE_SIDE_BET_ROUNDS.SEASON_ID, created.id.value)
                .set(SEASON_RULE_SIDE_BET_ROUNDS.ROUND, round)
                .execute()
        }

        val rules = repository.getRules(created.id)

        rules.shouldNotBeNull()
        rules.payouts shouldContainExactly
            listOf(BigDecimal("50.0000"), BigDecimal("25.0000"), BigDecimal("10.0000"))
        rules.sideBetRounds shouldContainExactly listOf(3, 4)
    }

    test("getRules returns null for unknown season") {
        repository.getRules(SeasonId(UUID.randomUUID())).shouldBeNull()
    }
})
