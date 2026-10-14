package com.cwfgw.tournamentLinks

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTournament
import com.cwfgw.tournaments.TournamentId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.util.UUID

class TournamentLinkRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = TournamentLinkRepository()
    val golferRepo = GolferRepository()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("upsert inserts a new override and listByTournament returns it") {
        val tournamentId = tx.update { seedTournament() }
        val golfer = tx.update { golferRepo.create(CreateGolferRequest(firstName = "Matt", lastName = "Fitzpatrick")) }

        val saved =
            tx.update { repository.upsert(tournamentId, espnCompetitorId = "abc-123", golferId = golfer.id) }

        saved.tournamentId shouldBe tournamentId
        saved.espnCompetitorId shouldBe "abc-123"
        saved.golferId shouldBe golfer.id

        tx.read { repository.listByTournament(tournamentId) } shouldHaveSize 1
    }

    test("upsert on existing key replaces the linked golfer") {
        val tournamentId = tx.update { seedTournament() }
        val (first, second) =
            tx.update {
                golferRepo.create(CreateGolferRequest(firstName = "Alex", lastName = "Fitzpatrick")) to
                    golferRepo.create(CreateGolferRequest(firstName = "Matt", lastName = "Fitzpatrick"))
            }

        tx.update { repository.upsert(tournamentId, "abc-123", first.id) }
        val updated = tx.update { repository.upsert(tournamentId, "abc-123", second.id) }

        updated.golferId shouldBe second.id
        tx.read { repository.listByTournament(tournamentId) } shouldHaveSize 1
    }

    test("listByTournament scopes results to one tournament") {
        val tournamentA = tx.update { seedTournament() }
        val tournamentB = tx.update { seedTournament() }
        val golfer = tx.update { golferRepo.create(CreateGolferRequest(firstName = "Matt", lastName = "Fitzpatrick")) }

        tx.update {
            repository.upsert(tournamentA, "abc-123", golfer.id)
            repository.upsert(tournamentB, "xyz-789", golfer.id)
        }

        tx.read { repository.listByTournament(tournamentA) }.map { it.espnCompetitorId } shouldBe listOf("abc-123")
        tx.read { repository.listByTournament(tournamentB) }.map { it.espnCompetitorId } shouldBe listOf("xyz-789")
    }

    test("delete removes the matching row and returns true") {
        val tournamentId = tx.update { seedTournament() }
        val golfer = tx.update { golferRepo.create(CreateGolferRequest(firstName = "Matt", lastName = "Fitzpatrick")) }
        tx.update { repository.upsert(tournamentId, "abc-123", golfer.id) }

        tx.update { repository.delete(tournamentId, "abc-123") } shouldBe true
        tx.read { repository.listByTournament(tournamentId) }.shouldBeEmpty()
    }

    test("delete returns false when no matching row exists") {
        val tournamentId = tx.update { seedTournament() }

        tx.update { repository.delete(tournamentId, "never-stored") } shouldBe false
    }

    test("listByTournament returns an empty list for an unknown tournament") {
        tx.read { repository.listByTournament(TournamentId(UUID.randomUUID())) }.shouldBeEmpty()
    }
})
