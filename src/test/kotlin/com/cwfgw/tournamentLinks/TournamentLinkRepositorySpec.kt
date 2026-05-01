package com.cwfgw.tournamentLinks

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.UUID

class TournamentLinkRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = TournamentLinkRepository()
    val leagueRepo = LeagueRepository(postgres.dsl)
    val seasonRepo = SeasonRepository()
    val tournamentRepo = TournamentRepository()
    val golferRepo = GolferRepository()
    val tx = Transactor(postgres.dsl)

    suspend fun seedTournament(): TournamentId {
        val league = leagueRepo.create(CreateLeagueRequest(name = "Castlewood Fantasy Golf"))
        return tx.update {
            val season =
                seasonRepo.create(CreateSeasonRequest(leagueId = league.id, name = "2026 Season", seasonYear = 2026))
            tournamentRepo.create(
                CreateTournamentRequest(
                    name = "Zurich Classic",
                    seasonId = season.id,
                    startDate = LocalDate.parse("2026-04-23"),
                    endDate = LocalDate.parse("2026-04-26"),
                ),
            ).id
        }
    }

    test("upsert inserts a new override and listByTournament returns it") {
        val tournamentId = seedTournament()
        val golfer = tx.update { golferRepo.create(CreateGolferRequest(firstName = "Matt", lastName = "Fitzpatrick")) }

        val saved =
            tx.update { repository.upsert(tournamentId, espnCompetitorId = "abc-123", golferId = golfer.id) }

        saved.tournamentId shouldBe tournamentId
        saved.espnCompetitorId shouldBe "abc-123"
        saved.golferId shouldBe golfer.id

        tx.read { repository.listByTournament(tournamentId) } shouldHaveSize 1
    }

    test("upsert on existing key replaces the linked golfer") {
        val tournamentId = seedTournament()
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
        val tournamentA = seedTournament()
        val tournamentB = seedTournament()
        val golfer = tx.update { golferRepo.create(CreateGolferRequest(firstName = "Matt", lastName = "Fitzpatrick")) }

        tx.update {
            repository.upsert(tournamentA, "abc-123", golfer.id)
            repository.upsert(tournamentB, "xyz-789", golfer.id)
        }

        tx.read { repository.listByTournament(tournamentA) }.map { it.espnCompetitorId } shouldBe listOf("abc-123")
        tx.read { repository.listByTournament(tournamentB) }.map { it.espnCompetitorId } shouldBe listOf("xyz-789")
    }

    test("delete removes the matching row and returns true") {
        val tournamentId = seedTournament()
        val golfer = tx.update { golferRepo.create(CreateGolferRequest(firstName = "Matt", lastName = "Fitzpatrick")) }
        tx.update { repository.upsert(tournamentId, "abc-123", golfer.id) }

        tx.update { repository.delete(tournamentId, "abc-123") } shouldBe true
        tx.read { repository.listByTournament(tournamentId) }.shouldBeEmpty()
    }

    test("delete returns false when no matching row exists") {
        val tournamentId = seedTournament()

        tx.update { repository.delete(tournamentId, "never-stored") } shouldBe false
    }

    test("listByTournament returns an empty list for an unknown tournament") {
        tx.read { repository.listByTournament(TournamentId(UUID.randomUUID())) }.shouldBeEmpty()
    }
})
