package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.leagues.LeagueId
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonRules
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.util.UUID

private fun season(
    leagueId: LeagueId,
    year: Int = 2026,
): CreateSeasonRequest = CreateSeasonRequest(leagueId = leagueId, name = "$year Season", seasonYear = year)

class SeasonRepositoryContractSpec : FunSpec({
    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    val cases = seasonRowContract + seasonRulesContract

    runContract("Postgres", cases) { ContractSubject(tx, SeasonRepository(), PostgresSeeds(tx)) }
    runContract("fake", cases) { ContractSubject(FakeTransactor(), FakeSeasonRepository(), FakeSeeds) }
})

private val seasonRowContract: List<ContractCase<SeasonRepository>> =
    listOf(
        ContractCase("a created season takes the column defaults at their stored scale and reads back by id") {
            val request = season(seeds.league())

            val created = tx.update { repository.create(request) }

            created.seasonNumber shouldBe 1
            created.maxTeams shouldBe 10
            created.tieFloor.toPlainString() shouldBe "1.0000"
            created.sideBetAmount.toPlainString() shouldBe "15.0000"
            tx.get { repository.findById(created.id) } shouldBe created
            tx.get { repository.findById(SeasonId(UUID.randomUUID())) }.shouldBeNull()
        },
        ContractCase("the season number counts up within a league and year unless the request names one") {
            val league = seeds.league()

            val numbers =
                listOf(
                    season(league),
                    season(league),
                    season(league).copy(seasonNumber = 7),
                    season(league),
                    season(league, year = 2027),
                    season(seeds.league()),
                ).map { request -> tx.update { repository.create(request) }.seasonNumber }

            numbers shouldContainExactly listOf(1, 2, 7, 8, 1, 1)
        },
        ContractCase("findAll filters by league and year, newest year then highest number first") {
            val league = seeds.league()
            listOf(season(league, 2025), season(league, 2026), season(league, 2026), season(seeds.league(), 2026))
                .forEach { request -> tx.update { repository.create(request) } }

            val inLeague = tx.get { repository.findAll(league, seasonYear = null) }
            val inYear = tx.get { repository.findAll(leagueId = null, seasonYear = 2026) }

            inLeague.map { it.seasonYear to it.seasonNumber } shouldContainExactly
                listOf(2026 to 2, 2026 to 1, 2025 to 1)
            inYear.map { it.leagueId == league } shouldContainExactly listOf(true, false, true)
            tx.get { repository.findAll(league, seasonYear = 2025) }.map { it.seasonNumber } shouldContainExactly
                listOf(1)
        },
        ContractCase("delete answers true for the season and false once it is gone") {
            val request = season(seeds.league())
            val created = tx.update { repository.create(request) }

            tx.update { repository.delete(created.id) } shouldBe true
            tx.update { repository.delete(created.id) } shouldBe false

            tx.get { repository.findById(created.id) }.shouldBeNull()
            tx.get { repository.getRules(created.id) }.shouldBeNull()
        },
    )

private val seasonRulesContract: List<ContractCase<SeasonRepository>> =
    listOf(
        ContractCase("a season created without rules reads back the league defaults") {
            val request = season(seeds.league())
            val created = tx.update { repository.create(request) }

            val rules = tx.get { repository.getRules(created.id) }

            rules shouldBe
                SeasonRules.defaults().copy(tieFloor = created.tieFloor, sideBetAmount = created.sideBetAmount)
            rules?.tieFloor?.toPlainString() shouldBe "1.0000"
            tx.get { repository.getRules(SeasonId(UUID.randomUUID())) }.shouldBeNull()
        },
        ContractCase("rules read back sorted at the stored scale, the floor matching the season row") {
            val custom =
                SeasonRules(
                    payouts = listOf(BigDecimal(20), BigDecimal("7.5")),
                    tieFloor = BigDecimal(3),
                    sideBetRounds = listOf(8, 2),
                    sideBetAmount = BigDecimal(10),
                )
            val request = season(seeds.league()).copy(rules = custom)
            val created = tx.update { repository.create(request) }

            val rules = tx.get { repository.getRules(created.id) }

            rules?.payouts?.map { it.toPlainString() } shouldBe listOf("20.0000", "7.5000")
            rules?.sideBetRounds shouldBe listOf(2, 8)
            rules?.tieFloor?.toPlainString() shouldBe "3.0000"
            rules?.sideBetAmount?.toPlainString() shouldBe "10.0000"
            created.tieFloor shouldBe rules?.tieFloor
        },
        ContractCase("rules with no payouts read back the default payouts, and no side-bet rounds read back none") {
            val bare = SeasonRules.defaults().copy(payouts = emptyList(), sideBetRounds = emptyList())
            val request = season(seeds.league()).copy(rules = bare)
            val created = tx.update { repository.create(request) }

            val rules = tx.get { repository.getRules(created.id) }

            rules?.payouts shouldBe SeasonRules.DEFAULT_PAYOUTS
            rules?.sideBetRounds shouldBe emptyList()
        },
    )
