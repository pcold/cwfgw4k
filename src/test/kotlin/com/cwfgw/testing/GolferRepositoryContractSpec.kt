package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.UNIQUE_PGA_PLAYER_ID
import com.cwfgw.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

private val SCHEFFLER =
    CreateGolferRequest(pgaPlayerId = "pga-1", firstName = "Scottie", lastName = "Scheffler", worldRanking = 1)

class GolferRepositoryContractSpec : FunSpec({
    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    runContract("Postgres", golferContract) { ContractSubject(tx, GolferRepository(), PostgresSeeds(tx)) }
    runContract("fake", golferContract) { ContractSubject(FakeTransactor(), FakeGolferRepository(), FakeSeeds) }
})

private val golferContract: List<ContractCase<GolferRepository>> =
    listOf(
        ContractCase("a created golfer reads back by id and by pga id, and an unknown key reads null") {
            val created = tx.update { repository.create(SCHEFFLER.copy(country = "USA")) }

            created.country shouldBe "USA"
            tx.get { repository.findById(created.id) } shouldBe created
            tx.get { repository.findByPgaPlayerId("pga-1") } shouldBe created
            tx.get { repository.findById(GolferId(UUID.randomUUID())) }.shouldBeNull()
            tx.get { repository.findByPgaPlayerId("pga-missing") }.shouldBeNull()
        },
        ContractCase("findAll orders by world ranking with the unranked last, then by last name") {
            listOf(
                CreateGolferRequest(firstName = "Phil", lastName = "Mickelson"),
                CreateGolferRequest(firstName = "Rory", lastName = "McIlroy", worldRanking = 2),
                CreateGolferRequest(firstName = "Tommy", lastName = "Fleetwood"),
                SCHEFFLER,
            ).forEach { request -> tx.update { repository.create(request) } }

            tx.get { repository.findAll(search = null) }.map { it.lastName } shouldContainExactly
                listOf("Scheffler", "McIlroy", "Fleetwood", "Mickelson")
        },
        ContractCase("search matches a case-insensitive substring of either name") {
            tx.update {
                repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
                repository.create(CreateGolferRequest(firstName = "Tiger", lastName = "Woods"))
            }

            tx.get { repository.findAll(search = "ROR") }.map { it.lastName } shouldContainExactly listOf("McIlroy")
            tx.get { repository.findAll(search = "wood") }.map { it.lastName } shouldContainExactly listOf("Woods")
            tx.get { repository.findAll(search = "nobody") }.shouldBeEmpty()
        },
        ContractCase("a second golfer on a taken pga id is outraced, and two without one never collide") {
            tx.update { repository.create(SCHEFFLER) }

            tx.updateUnlessOutraced(UNIQUE_PGA_PLAYER_ID) {
                repository.create(SCHEFFLER.copy(firstName = "Other"))
            } shouldBe
                Result.Err(Outraced)
            tx.update {
                repository.create(CreateGolferRequest(firstName = "Amateur", lastName = "One"))
                repository.create(CreateGolferRequest(firstName = "Amateur", lastName = "Two"))
            }
            tx.get { repository.findAll(search = null) } shouldHaveSize 3
        },
        ContractCase("findOrCreate answers the holder of a taken pga id and creates on a free one") {
            val holder = tx.update { repository.create(SCHEFFLER) }

            tx.update { repository.findOrCreate(SCHEFFLER.copy(lastName = "Renamed")) } shouldBe holder
            val created = tx.update { repository.findOrCreate(SCHEFFLER.copy(pgaPlayerId = "pga-2")) }

            created.pgaPlayerId shouldBe "pga-2"
            tx.get { repository.findAll(search = null) } shouldHaveSize 2
        },
        ContractCase("a backfill sets the pga id on a row without one, leaving its other columns as they were") {
            val idless = tx.update { repository.create(SCHEFFLER.copy(pgaPlayerId = null)) }

            val backfilled = tx.update { repository.backfillPgaPlayerId(idless.id, "pga-1") }

            backfilled shouldBe idless.copy(pgaPlayerId = "pga-1")
            tx.get { repository.findByPgaPlayerId("pga-1") } shouldBe backfilled
        },
        ContractCase("a backfill onto a row that already has a pga id changes nothing and answers null") {
            val held = tx.update { repository.create(SCHEFFLER) }

            tx.update { repository.backfillPgaPlayerId(held.id, "pga-2") }.shouldBeNull()
            tx.get { repository.findById(held.id) } shouldBe held
        },
        ContractCase("a backfill of a pga id another row holds changes nothing and answers null") {
            val holder = tx.update { repository.create(SCHEFFLER) }
            val idless = tx.update { repository.create(SCHEFFLER.copy(pgaPlayerId = null)) }

            tx.update { repository.backfillPgaPlayerId(idless.id, "pga-1") }.shouldBeNull()
            tx.get { repository.findAll(search = null) } shouldContainExactlyInAnyOrder listOf(holder, idless)
        },
    )
