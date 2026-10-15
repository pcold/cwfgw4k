package com.cwfgw.testing

import com.cwfgw.cache.CacheGeneration
import com.cwfgw.cache.FakeCacheRepository
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentFilter
import com.cwfgw.tournaments.UpdateTournamentRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.UUID

private val SCHEFFLER = CreateGolferRequest(pgaPlayerId = "pga-1", firstName = "Scottie", lastName = "Scheffler")
private val MCILROY = CreateGolferRequest(pgaPlayerId = "pga-2", firstName = "Rory", lastName = "McIlroy")

private val MASTERS =
    CreateTournamentRequest(
        name = "Masters",
        seasonId = SeasonId(UUID.randomUUID()),
        startDate = LocalDate.parse("2026-04-09"),
        endDate = LocalDate.parse("2026-04-12"),
    )

private data object Refused

private class BlockFailed : IllegalStateException("the block failed after writing")

class FakeTransactorSpec : FunSpec({
    val tx = FakeTransactor()

    test("a block that returns keeps every write it made") {
        val golfers = FakeGolferRepository()

        tx.update { golfers.create(SCHEFFLER) }

        tx.get { golfers.findAll(search = null) }.map { it.lastName } shouldContainExactly listOf("Scheffler")
    }

    test("a write the block made before it threw is gone, as a rolled-back transaction leaves nothing") {
        val golfers = FakeGolferRepository()

        shouldThrow<BlockFailed> {
            tx.update {
                golfers.create(SCHEFFLER)
                throw BlockFailed()
            }
        }

        tx.get { golfers.findAll(search = null) }.shouldBeEmpty()
    }

    test("the rollback reaches every fake the block wrote to, not only the last one") {
        val golfers = FakeGolferRepository()
        val tournaments = FakeTournamentRepository()

        shouldThrow<BlockFailed> {
            tx.update {
                golfers.create(SCHEFFLER)
                tournaments.create(MASTERS)
                throw BlockFailed()
            }
        }

        tx.get { golfers.findAll(search = null) }.shouldBeEmpty()
        tx.get { tournaments.findAll(TournamentFilter()) }.shouldBeEmpty()
    }

    test("a row a thrown block overwrote reads back as it was before the block") {
        val tournaments = FakeTournamentRepository()
        val masters = tx.update { tournaments.create(MASTERS) }

        shouldThrow<BlockFailed> {
            tx.update {
                tournaments.update(masters.id, UpdateTournamentRequest(name = "Renamed"))
                throw BlockFailed()
            }
        }

        tx.get { tournaments.findById(masters.id) } shouldBe masters
    }

    test("a counter a thrown block advanced reads back at its value before the block") {
        val cache = FakeCacheRepository()
        tx.update { cache.clear() }

        shouldThrow<BlockFailed> {
            tx.update {
                cache.clear()
                throw BlockFailed()
            }
        }

        tx.get { cache.generation() } shouldBe CacheGeneration(1)
    }

    test("a block returning Err keeps its writes, since Transactor commits on any return that is not a throw") {
        val golfers = FakeGolferRepository()

        val outcome =
            tx.update {
                golfers.create(SCHEFFLER)
                Result.Err(Refused)
            }

        outcome shouldBe Result.Err(Refused)
        tx.get { golfers.findAll(search = null) }.map { it.lastName } shouldContainExactly listOf("Scheffler")
    }

    test("a rollback restores what earlier transactions and seeding wrote, rather than emptying the fake") {
        val golfers = FakeGolferRepository()
        with(noopTransactionContext) { golfers.create(SCHEFFLER) }
        tx.update { golfers.create(MCILROY) }

        shouldThrow<BlockFailed> {
            tx.update {
                golfers.create(CreateGolferRequest(pgaPlayerId = "pga-3", firstName = "Jon", lastName = "Rahm"))
                throw BlockFailed()
            }
        }

        tx.get { golfers.findAll(search = null) }.map { it.lastName } shouldContainExactly
            listOf("McIlroy", "Scheffler")
    }
})
