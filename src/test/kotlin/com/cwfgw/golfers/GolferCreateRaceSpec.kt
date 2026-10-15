package com.cwfgw.golfers

import com.cwfgw.db.Transactor
import com.cwfgw.result.Result
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

/**
 * Sequential inserts on one key: the second trips the same 23505 a concurrent loser would, and a forced
 * interleaving would only add flake.
 */
class GolferCreateRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val repository = GolferRepository()
    val service = GolferService(repository, tx)
    val scottie = CreateGolferRequest(pgaPlayerId = "espn-1", firstName = "Scottie", lastName = "Scheffler")

    test("a create that loses the race on pga_player_id answers PgaPlayerIdTaken and keeps the winner's row") {
        val winner = service.create(scottie).orFail()

        service.create(scottie.copy(firstName = "S.")) shouldBe Result.Err(GolferError.PgaPlayerIdTaken)

        tx.read { repository.findAll(search = null) } shouldContainExactly listOf(winner)
    }

    test("findOrCreate that loses the race returns the winner's row and leaves its transaction usable") {
        val winner = tx.update { repository.create(scottie) }

        val (found, bystander) =
            tx.update {
                repository.findOrCreate(scottie.copy(firstName = "S.")) to
                    repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
            }

        found shouldBe winner
        tx.read { repository.findAll(search = null) }.map { it.id } shouldContainExactly listOf(bystander.id, winner.id)
    }

    test("findOrCreate inserts when nothing holds the pga_player_id") {
        val created = tx.update { repository.findOrCreate(scottie) }

        tx.read { repository.findByPgaPlayerId("espn-1") } shouldBe created
    }
})
