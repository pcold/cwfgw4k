package com.cwfgw.tournamentLinks

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTombstone
import com.cwfgw.testing.seedTombstonedGolfer
import com.cwfgw.testing.seedTournament
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jooq.exception.DataAccessException

private const val CHECK_VIOLATION = "23514"

/** Written past the service, whose own refusal would otherwise answer first: what is under test is the schema's. */
class TournamentLinkTombstoneConstraintSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val links = TournamentLinkRepository()

    test("the schema refuses a new link to a tombstoned golfer") {
        val tournamentId = tx.update { seedTournament() }
        val tombstoned = tx.update { seedTombstonedGolfer("Thomas", "Detry") }

        shouldThrow<DataAccessException> {
            tx.update { links.upsert(tournamentId, "espn-1", tombstoned.id) }
        }.sqlState() shouldBe CHECK_VIOLATION

        tx.read { links.listByTournament(tournamentId) }.shouldBeEmpty()
    }

    test("the schema refuses moving an existing link onto a tombstoned golfer") {
        val tournamentId = tx.update { seedTournament() }
        val live = tx.update { GolferRepository().create(CreateGolferRequest(firstName = "Tom", lastName = "Kim")) }
        val tombstoned = tx.update { seedTombstonedGolfer("Thomas", "Detry") }
        val link = tx.update { links.upsert(tournamentId, "espn-1", live.id) }

        shouldThrow<DataAccessException> {
            tx.update { links.upsert(tournamentId, "espn-1", tombstoned.id) }
        }.sqlState() shouldBe CHECK_VIOLATION

        tx.read { links.listByTournament(tournamentId) } shouldBe listOf(link)
    }

    test("a link written before its golfer was tombstoned survives the tombstone") {
        val tournamentId = tx.update { seedTournament() }
        val golfer =
            tx.update { GolferRepository().create(CreateGolferRequest(firstName = "Thomas", lastName = "Detry")) }
        val link = tx.update { links.upsert(tournamentId, "espn-1", golfer.id) }

        tx.update { seedTombstone(golfer.id) }

        tx.read { links.listByTournament(tournamentId) } shouldBe listOf(link)
    }
})
