package com.cwfgw.tournaments

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.jooq.tables.references.TOURNAMENTS
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.testing.POSTGRES_UUID_ORDER
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jooq.exception.DataAccessException
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class TournamentRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = TournamentRepository()
    val leagueRepo = LeagueRepository()
    val seasonRepo = SeasonRepository()
    val golferRepo = GolferRepository()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    var seasonId = SeasonId(UUID.randomUUID())

    beforeEach {
        seasonId =
            tx.update {
                val league = leagueRepo.create(CreateLeagueRequest(name = "Castlewood Fantasy Golf"))
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = league.id, name = "2026 Season", seasonYear = 2026),
                )
            }.id
    }

    fun create(
        name: String = "The Masters",
        startDate: LocalDate = LocalDate.parse("2026-04-09"),
        endDate: LocalDate = LocalDate.parse("2026-04-12"),
        payoutMultiplier: BigDecimal? = null,
    ): NewTournament =
        NewTournament(
            name = name,
            seasonId = seasonId,
            startDate = startDate,
            endDate = endDate,
            payoutMultiplier = payoutMultiplier,
        )

    test("create persists a tournament with default status=upcoming and payoutMultiplier=1.0000") {
        val created = tx.update { repository.create(create()) }

        created.name shouldBe "The Masters"
        created.seasonId shouldBe seasonId
        created.status shouldBe TournamentStatus.Upcoming
        created.payoutMultiplier.compareTo(BigDecimal.ONE) shouldBe 0
    }

    test("create respects explicit payoutMultiplier") {
        val created = tx.update { repository.create(create(name = "Major", payoutMultiplier = BigDecimal("2.5"))) }

        created.payoutMultiplier.compareTo(BigDecimal("2.5")) shouldBe 0
    }

    test("findAll orders by start_date asc then created_at asc") {
        val (april, may, june) =
            tx.update {
                val april = repository.create(create(name = "April", startDate = LocalDate.parse("2026-04-09")))
                val june = repository.create(create(name = "June", startDate = LocalDate.parse("2026-06-04")))
                val may = repository.create(create(name = "May", startDate = LocalDate.parse("2026-05-07")))
                Triple(april, may, june)
            }

        val result = tx.read { repository.findAll(TournamentFilter()) }

        result.map { it.id } shouldContainExactly listOf(april.id, may.id, june.id)
    }

    test("findAll breaks a start-date tie inside one transaction by id, since the rows share created_at") {
        val sameWeek =
            tx.update {
                (1..6).map { index -> repository.create(create(name = "Event $index")) }
            }

        val result = tx.read { repository.findAll(TournamentFilter(seasonId = seasonId)) }

        sameWeek.map { it.createdAt }.distinct().size shouldBe 1
        result.map { it.id } shouldContainExactly
            sameWeek.sortedWith(compareBy(POSTGRES_UUID_ORDER) { it.id.value }).map { it.id }
    }

    test("findAll filters by season_id") {
        val mine =
            tx.update {
                val otherLeague = leagueRepo.create(CreateLeagueRequest(name = "Other"))
                val otherSeason =
                    seasonRepo.create(
                        CreateSeasonRequest(leagueId = otherLeague.id, name = "Other 2026", seasonYear = 2026),
                    ).id
                val mine = repository.create(create(name = "Mine"))
                repository.create(
                    NewTournament(
                        name = "Theirs",
                        seasonId = otherSeason,
                        startDate = LocalDate.parse("2026-04-09"),
                        endDate = LocalDate.parse("2026-04-12"),
                    ),
                )
                mine
            }

        val result = tx.read { repository.findAll(TournamentFilter(seasonId = seasonId)) }

        result.map { it.id } shouldContainExactly listOf(mine.id)
    }

    test("findAll filters by status") {
        val upcoming =
            tx.update {
                val upcoming = repository.create(create(name = "Upcoming"))
                val completed = repository.create(create(name = "Completed"))
                repository.update(completed.id, UpdateTournamentRequest(status = TournamentStatus.Completed))
                upcoming
            }

        val result = tx.read { repository.findAll(TournamentFilter(statuses = setOf(TournamentStatus.Upcoming))) }

        result.map { it.id } shouldContainExactly listOf(upcoming.id)
    }

    test("the schema refuses a status outside TournamentStatus") {
        val masters = tx.update { repository.create(create()) }

        val refusal =
            shouldThrow<DataAccessException> {
                postgres.dsl.update(TOURNAMENTS)
                    .set(TOURNAMENTS.STATUS, "rained_out")
                    .where(TOURNAMENTS.ID.eq(masters.id.value))
                    .execute()
            }

        refusal.message.shouldNotBeNull() shouldContain "tournaments_status_known"
        tx.read { repository.findById(masters.id) }.shouldNotBeNull().status shouldBe TournamentStatus.Upcoming
    }

    test("the schema admits every TournamentStatus") {
        val masters = tx.update { repository.create(create()) }

        TournamentStatus.entries.forEach { status ->
            tx.update { repository.update(masters.id, UpdateTournamentRequest(status = status)) }
            tx.read { repository.findById(masters.id) }.shouldNotBeNull().status shouldBe status
        }
    }

    test("findById returns null for unknown id") {
        tx.read { repository.findById(TournamentId(UUID.randomUUID())) }.shouldBeNull()
    }

    test("findByPgaTournamentId returns the tournament with that pga_tournament_id") {
        val masters =
            tx.update {
                repository.create(create(name = "Other"))
                repository.create(
                    NewTournament(
                        name = "The Masters",
                        seasonId = seasonId,
                        startDate = LocalDate.parse("2026-04-09"),
                        endDate = LocalDate.parse("2026-04-12"),
                        pgaTournamentId = "401580999",
                    ),
                )
            }

        tx.read { repository.findByPgaTournamentId("401580999") }?.id shouldBe masters.id
    }

    test("findByPgaTournamentId returns null when no tournament has that pga_tournament_id") {
        tx.update { repository.create(create(name = "Other")) }

        tx.read { repository.findByPgaTournamentId("missing") }.shouldBeNull()
    }

    test("update applies only supplied fields") {
        val created = tx.update { repository.create(create(name = "Original")) }

        val updated =
            tx.update { repository.update(created.id, UpdateTournamentRequest(status = TournamentStatus.InProgress)) }

        updated.shouldNotBeNull()
        updated.status shouldBe TournamentStatus.InProgress
        updated.name shouldBe "Original"
    }

    test("update with no fields returns the existing row unchanged") {
        val created = tx.update { repository.create(create(name = "Original")) }

        tx.update { repository.update(created.id, UpdateTournamentRequest()) } shouldBe created
    }

    test("update returns null for unknown id") {
        tx.update {
            repository.update(
                TournamentId(UUID.randomUUID()),
                UpdateTournamentRequest(name = "Ghost"),
            )
        }.shouldBeNull()
    }

    test("upsertResult inserts a result and getResults returns it ordered by position") {
        val (tournament, rory, scottie) =
            tx.update {
                val tournament = repository.create(create())
                val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
                val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
                repository.upsertResult(
                    tournament.id,
                    NewTournamentResult(golferId = rory.id, position = 2, earnings = 1_500_000),
                )
                repository.upsertResult(
                    tournament.id,
                    NewTournamentResult(golferId = scottie.id, position = 1, earnings = 2_500_000),
                )
                Triple(tournament, rory, scottie)
            }

        val results = tx.read { repository.getResults(tournament.id) }

        results.map { it.golferId } shouldContainExactly listOf(scottie.id, rory.id)
        results.first().earnings shouldBe 2_500_000
    }

    test("getResultsByTournaments returns rows for every supplied tournament in one query") {
        val ids =
            tx.update {
                val a = repository.create(create(name = "Open A", startDate = LocalDate.parse("2026-04-01")))
                val b = repository.create(create(name = "Open B", startDate = LocalDate.parse("2026-04-08")))
                val c = repository.create(create(name = "Open C", startDate = LocalDate.parse("2026-04-15")))
                val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
                val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
                repository.upsertResult(a.id, NewTournamentResult(golferId = rory.id, position = 1))
                repository.upsertResult(b.id, NewTournamentResult(golferId = scottie.id, position = 1))
                // Tournament c has no results — its id is passed in but should contribute zero rows.
                Triple(a.id, b.id, c.id)
            }

        val results = tx.read { repository.getResultsByTournaments(listOf(ids.first, ids.second, ids.third)) }

        val byTournament = results.groupBy { it.tournamentId }
        byTournament.keys shouldContainExactlyInAnyOrder setOf(ids.first, ids.second)
        byTournament.getValue(ids.first).single().position shouldBe 1
        byTournament.getValue(ids.second).single().position shouldBe 1
    }

    test("getResultsByTournaments returns empty for an empty id collection") {
        tx.read { repository.getResultsByTournaments(emptyList()) } shouldBe emptyList()
    }

    test("upsertResult replaces an existing row for the same tournament and golfer") {
        val (initial, updated) =
            tx.update {
                val tournament = repository.create(create())
                val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
                val initial =
                    repository.upsertResult(
                        tournament.id,
                        NewTournamentResult(golferId = rory.id, position = 30, madeCut = true),
                    )
                val updated =
                    repository.upsertResult(
                        tournament.id,
                        NewTournamentResult(golferId = rory.id, position = 2, madeCut = true),
                    )
                val resultIds = repository.getResults(tournament.id).map { it.id }
                resultIds shouldContainExactly listOf(initial.id)
                initial to updated
            }

        updated.id shouldBe initial.id
        updated.position shouldBe 2
    }
})
