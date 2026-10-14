package com.cwfgw.golfers

import com.cwfgw.db.Transactor
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class GolferRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val repository = GolferRepository()

    test("create persists the golfer and returns a row populated with id and updatedAt") {
        val created =
            tx.update {
                repository.create(
                    CreateGolferRequest(
                        pgaPlayerId = "pga-001",
                        firstName = "Scottie",
                        lastName = "Scheffler",
                        country = "USA",
                        worldRanking = 1,
                    ),
                )
            }

        created.firstName shouldBe "Scottie"
        tx.read { repository.findById(created.id) }.shouldNotBeNull()
    }

    test("findAll sorts by world_ranking asc nulls last then last_name") {
        val unranked =
            tx.update { repository.create(CreateGolferRequest(firstName = "Phil", lastName = "Mickelson")) }
        val ranked2 =
            tx.update {
                repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy", worldRanking = 2))
            }
        val ranked1 =
            tx.update {
                repository.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler", worldRanking = 1))
            }

        val result = tx.read { repository.findAll(search = null) }

        result.map { it.id } shouldContainInOrder listOf(ranked1.id, ranked2.id, unranked.id)
    }

    test("findAll search is case-insensitive substring over first and last name") {
        val rory = tx.update { repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy")) }
        val tiger = tx.update { repository.create(CreateGolferRequest(firstName = "Tiger", lastName = "Woods")) }
        tx.update { repository.create(CreateGolferRequest(firstName = "Phil", lastName = "Mickelson")) }

        val firstNameMatch = tx.read { repository.findAll(search = "ROR") }
        firstNameMatch.map { it.id } shouldContainExactly listOf(rory.id)

        val lastNameMatch = tx.read { repository.findAll(search = "wood") }
        lastNameMatch.map { it.id } shouldContainExactly listOf(tiger.id)
    }

    test("findById returns null for unknown id") {
        tx.read { repository.findById(GolferId(UUID.randomUUID())) }.shouldBeNull()
    }

    test("findByPgaPlayerId returns the golfer with that pga_player_id") {
        tx.update { repository.create(CreateGolferRequest(firstName = "Tiger", lastName = "Woods")) }
        val scottie =
            tx.update {
                repository.create(
                    CreateGolferRequest(
                        pgaPlayerId = "pga-001",
                        firstName = "Scottie",
                        lastName = "Scheffler",
                    ),
                )
            }

        tx.read { repository.findByPgaPlayerId("pga-001") }?.id shouldBe scottie.id
    }

    test("findByPgaPlayerId returns null when no golfer has that pga_player_id") {
        tx.update { repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy")) }

        tx.read { repository.findByPgaPlayerId("missing") }.shouldBeNull()
    }

    test("empty table returns an empty list") {
        tx.read { repository.findAll(search = null) }.shouldBeEmpty()
    }
})
