package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.FakeLeagueRepository
import com.cwfgw.leagues.LeagueRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.comparables.shouldBeGreaterThanOrEqualTo
import io.kotest.matchers.shouldBe
import java.time.Instant

class LeagueRepositoryContractSpec : FunSpec({
    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    runContract("Postgres", leagueContract) { ContractSubject(tx, LeagueRepository(), PostgresSeeds(tx)) }
    runContract("fake", leagueContract) { ContractSubject(FakeTransactor(), FakeLeagueRepository(), FakeSeeds) }
})

private val leagueContract: List<ContractCase<LeagueRepository>> =
    listOf(
        ContractCase("findAll answers nothing before the first league is created") {
            tx.get { repository.findAll() }.shouldBeEmpty()
        },
        ContractCase("a created league is stamped with the time of the write and findAll lists it by name") {
            val before = Instant.now()

            val created =
                listOf("Bravo Golf", "Castlewood Fantasy Golf", "Alpha Golf").map { name ->
                    tx.update { repository.create(CreateLeagueRequest(name)) }
                }

            created.first().name shouldBe "Bravo Golf"
            created.first().createdAt shouldBeGreaterThanOrEqualTo before.minusSeconds(1)
            tx.get { repository.findAll() } shouldContainExactly created.sortedBy { it.name }
        },
        ContractCase("two leagues may share a name") {
            val first = tx.update { repository.create(CreateLeagueRequest("Castlewood")) }
            val second = tx.update { repository.create(CreateLeagueRequest("Castlewood")) }

            tx.get { repository.findAll() } shouldContainExactlyInAnyOrder listOf(first, second)
        },
    )
