package com.cwfgw.tournaments

import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class TournamentRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = TournamentRepository(postgres.dsl)
    val leagueRepo = LeagueRepository(postgres.dsl)
    val seasonRepo = SeasonRepository(postgres.dsl)
    val golferRepo = GolferRepository(postgres.dsl)
    var seasonId = SeasonId(UUID.randomUUID())

    beforeEach {
        val league = leagueRepo.create(CreateLeagueRequest(name = "Castlewood Fantasy Golf"))
        seasonId =
            seasonRepo.create(
                CreateSeasonRequest(leagueId = league.id, name = "2026 Season", seasonYear = 2026),
            ).id
    }

    fun create(
        name: String = "The Masters",
        startDate: LocalDate = LocalDate.parse("2026-04-09"),
        endDate: LocalDate = LocalDate.parse("2026-04-12"),
        payoutMultiplier: BigDecimal? = null,
    ): CreateTournamentRequest =
        CreateTournamentRequest(
            name = name,
            seasonId = seasonId,
            startDate = startDate,
            endDate = endDate,
            payoutMultiplier = payoutMultiplier,
        )

    test("create persists a tournament with default status=upcoming and payoutMultiplier=1.0000") {
        val created = repository.create(create())

        created.name shouldBe "The Masters"
        created.seasonId shouldBe seasonId
        created.status shouldBe TournamentStatus.Upcoming
        created.payoutMultiplier.compareTo(BigDecimal.ONE) shouldBe 0
    }

    test("create respects explicit payoutMultiplier") {
        val created = repository.create(create(name = "Major", payoutMultiplier = BigDecimal("2.5")))

        created.payoutMultiplier.compareTo(BigDecimal("2.5")) shouldBe 0
    }

    test("findAll orders by start_date asc then created_at asc") {
        val april = repository.create(create(name = "April", startDate = LocalDate.parse("2026-04-09")))
        val june = repository.create(create(name = "June", startDate = LocalDate.parse("2026-06-04")))
        val may = repository.create(create(name = "May", startDate = LocalDate.parse("2026-05-07")))

        val result = repository.findAll(seasonId = null, status = null)

        result.map { it.id } shouldContainExactly listOf(april.id, may.id, june.id)
    }

    test("findAll filters by season_id") {
        val otherLeague = leagueRepo.create(CreateLeagueRequest(name = "Other"))
        val otherSeason =
            seasonRepo.create(
                CreateSeasonRequest(leagueId = otherLeague.id, name = "Other 2026", seasonYear = 2026),
            ).id
        val mine = repository.create(create(name = "Mine"))
        repository.create(
            CreateTournamentRequest(
                name = "Theirs",
                seasonId = otherSeason,
                startDate = LocalDate.parse("2026-04-09"),
                endDate = LocalDate.parse("2026-04-12"),
            ),
        )

        val result = repository.findAll(seasonId = seasonId, status = null)

        result.map { it.id } shouldContainExactly listOf(mine.id)
    }

    test("findAll filters by status") {
        val upcoming = repository.create(create(name = "Upcoming"))
        val completed = repository.create(create(name = "Completed"))
        repository.update(completed.id, UpdateTournamentRequest(status = TournamentStatus.Completed))

        val result = repository.findAll(seasonId = null, status = TournamentStatus.Upcoming)

        result.map { it.id } shouldContainExactly listOf(upcoming.id)
    }

    test("findById returns null for unknown id") {
        repository.findById(TournamentId(UUID.randomUUID())).shouldBeNull()
    }

    test("findByPgaTournamentId returns the tournament with that pga_tournament_id") {
        repository.create(create(name = "Other"))
        val masters =
            repository.create(
                CreateTournamentRequest(
                    name = "The Masters",
                    seasonId = seasonId,
                    startDate = LocalDate.parse("2026-04-09"),
                    endDate = LocalDate.parse("2026-04-12"),
                    pgaTournamentId = "401580999",
                ),
            )

        repository.findByPgaTournamentId("401580999")?.id shouldBe masters.id
    }

    test("findByPgaTournamentId returns null when no tournament has that pga_tournament_id") {
        repository.create(create(name = "Other"))

        repository.findByPgaTournamentId("missing").shouldBeNull()
    }

    test("update applies only supplied fields") {
        val created = repository.create(create(name = "Original"))

        val updated = repository.update(created.id, UpdateTournamentRequest(status = TournamentStatus.InProgress))

        updated.shouldNotBeNull()
        updated.status shouldBe TournamentStatus.InProgress
        updated.name shouldBe "Original"
    }

    test("update with no fields returns the existing row unchanged") {
        val created = repository.create(create(name = "Original"))

        repository.update(created.id, UpdateTournamentRequest()) shouldBe created
    }

    test("update returns null for unknown id") {
        repository.update(
            TournamentId(UUID.randomUUID()),
            UpdateTournamentRequest(name = "Ghost"),
        ).shouldBeNull()
    }

    test("upsertResult inserts a result and getResults returns it ordered by position") {
        val tournament = repository.create(create())
        val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
        val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))

        repository.upsertResult(
            tournament.id,
            CreateTournamentResultRequest(golferId = rory.id, position = 2, earnings = 1_500_000),
        )
        repository.upsertResult(
            tournament.id,
            CreateTournamentResultRequest(golferId = scottie.id, position = 1, earnings = 2_500_000),
        )

        val results = repository.getResults(tournament.id)

        results.map { it.golferId } shouldContainExactly listOf(scottie.id, rory.id)
        results.first().earnings shouldBe 2_500_000
    }

    test("upsertResult replaces an existing row for the same tournament and golfer") {
        val tournament = repository.create(create())
        val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))

        val initial =
            repository.upsertResult(
                tournament.id,
                CreateTournamentResultRequest(golferId = rory.id, position = 30, madeCut = true),
            )
        val updated =
            repository.upsertResult(
                tournament.id,
                CreateTournamentResultRequest(golferId = rory.id, position = 2, madeCut = true),
            )

        updated.id shouldBe initial.id
        updated.position shouldBe 2
        repository.getResults(tournament.id).map { it.id } shouldContainExactly listOf(initial.id)
    }
})
