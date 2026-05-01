package com.cwfgw.leagues

import com.cwfgw.db.Transactor
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class LeagueRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = LeagueRepository()
    val tx = Transactor(postgres.dsl)

    test("findAll returns created leagues sorted by name") {
        tx.update {
            repository.create(CreateLeagueRequest(name = "Bravo Golf"))
            repository.create(CreateLeagueRequest(name = "Alpha Golf"))
        }

        val leagues = tx.read { repository.findAll() }

        leagues.map { it.name } shouldContainExactly listOf("Alpha Golf", "Bravo Golf")
    }

    test("findById returns a created league") {
        val created = tx.update { repository.create(CreateLeagueRequest(name = "Test League")) }

        val found = tx.read { repository.findById(created.id) }

        found shouldBe created
    }

    test("findById returns null for an unknown id") {
        tx.read { repository.findById(LeagueId(UUID.randomUUID())) }.shouldBeNull()
    }

    test("create populates id and createdAt") {
        val before = java.time.Instant.now()

        val created = tx.update { repository.create(CreateLeagueRequest(name = "Castlewood Fantasy Golf")) }

        created.name shouldBe "Castlewood Fantasy Golf"
        (created.createdAt >= before.minusSeconds(1)) shouldBe true
    }
})
