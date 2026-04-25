package com.cwfgw.admin

import com.cwfgw.espn.EspnCalendarEntry
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnUpstreamException
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SEASON_START = LocalDate.parse("2026-01-01")
private val SEASON_END = LocalDate.parse("2026-08-31")

private fun calendarEntry(
    id: String,
    label: String,
    isoDate: String,
): EspnCalendarEntry = EspnCalendarEntry(id = id, label = label, startDate = isoDate)

private class Fixture(
    seedSeason: Boolean = true,
    calendar: List<EspnCalendarEntry> = emptyList(),
    upstreamError: EspnUpstreamException? = null,
) {
    val seasonRepo: FakeSeasonRepository
    val tournamentRepo = FakeTournamentRepository()
    val service: AdminService

    init {
        val seasonId = SEASON_ID
        seasonRepo =
            FakeSeasonRepository(idFactory = { seasonId })
        if (seedSeason) {
            kotlinx.coroutines.runBlocking {
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
                )
            }
        }
        val tournamentService = TournamentService(tournamentRepo)
        val golferService = GolferService(FakeGolferRepository())
        val teamService = TeamService(FakeTeamRepository())
        val espnService =
            EspnService(
                client = FakeEspnClient(calendar = calendar, upstreamError = upstreamError),
                tournamentService = tournamentService,
                golferService = golferService,
                teamService = teamService,
            )
        service =
            AdminService(
                seasonService = SeasonService(seasonRepo),
                tournamentService = tournamentService,
                espnService = espnService,
            )
    }
}

class AdminServiceSpec : FunSpec({

    test("uploadSeason creates one tournament per ESPN entry that falls inside the date range") {
        val fixture =
            Fixture(
                calendar =
                    listOf(
                        calendarEntry("e-1", "Sony Open", "2026-01-15T00:00Z"),
                        calendarEntry("e-2", "American Express", "2026-01-22T00:00Z"),
                    ),
            )

        val result =
            fixture.service.uploadSeason(SEASON_ID, SEASON_START, SEASON_END)
                .shouldBeInstanceOf<Result.Ok<SeasonImportResult>>()
                .value

        result.created shouldHaveSize 2
        result.skipped.shouldBeEmpty()
        result.created.map { it.name } shouldBe listOf("Sony Open", "American Express")
    }

    test("created tournaments link the ESPN id, default to a 4-day Thu→Sun window, and use multiplier 1.0") {
        val fixture =
            Fixture(calendar = listOf(calendarEntry("e-1", "The Masters", "2026-04-09T00:00Z")))

        val tournament =
            fixture.service.uploadSeason(SEASON_ID, SEASON_START, SEASON_END)
                .shouldBeInstanceOf<Result.Ok<SeasonImportResult>>()
                .value
                .created
                .single()

        tournament.pgaTournamentId shouldBe "e-1"
        tournament.startDate shouldBe LocalDate.parse("2026-04-09")
        tournament.endDate shouldBe LocalDate.parse("2026-04-12")
        tournament.payoutMultiplier.compareTo(java.math.BigDecimal.ONE) shouldBe 0
    }

    test("entries outside the date range are silently filtered out — not even reported as skipped") {
        val fixture =
            Fixture(
                calendar =
                    listOf(
                        calendarEntry("e-in", "InRange", "2026-04-09T00:00Z"),
                        calendarEntry("e-before", "TooEarly", "2025-12-15T00:00Z"),
                        calendarEntry("e-after", "TooLate", "2026-09-15T00:00Z"),
                    ),
            )

        val result =
            fixture.service.uploadSeason(SEASON_ID, SEASON_START, SEASON_END)
                .shouldBeInstanceOf<Result.Ok<SeasonImportResult>>()
                .value

        result.created.map { it.name } shouldBe listOf("InRange")
        result.skipped.shouldBeEmpty()
    }

    test("uploadSeason returns SeasonNotFound when the season id doesn't exist (and never hits ESPN)") {
        val fixture =
            Fixture(
                seedSeason = false,
                calendar = listOf(calendarEntry("e-1", "Sony Open", "2026-01-15T00:00Z")),
            )

        fixture.service.uploadSeason(SEASON_ID, SEASON_START, SEASON_END) shouldBe
            Result.Err(AdminError.SeasonNotFound(SEASON_ID))
        fixture.tournamentRepo.findByPgaTournamentId("e-1") shouldBe null
    }

    test("uploadSeason returns UpstreamUnavailable when ESPN responds with a non-2xx") {
        val fixture =
            Fixture(upstreamError = EspnUpstreamException(status = 503, message = "Service Unavailable"))

        fixture.service.uploadSeason(SEASON_ID, SEASON_START, SEASON_END) shouldBe
            Result.Err(AdminError.UpstreamUnavailable(503))
    }

    test("entries already linked to a tournament land in skipped, not created — so re-runs are safe") {
        val fixture =
            Fixture(calendar = listOf(calendarEntry("e-1", "Sony Open", "2026-01-15T00:00Z")))
        // Pre-seed a tournament with the same pga id, simulating a prior import.
        kotlinx.coroutines.runBlocking {
            fixture.tournamentRepo.create(
                CreateTournamentRequest(
                    pgaTournamentId = "e-1",
                    name = "Sony Open (already)",
                    seasonId = SEASON_ID,
                    startDate = LocalDate.parse("2026-01-15"),
                    endDate = LocalDate.parse("2026-01-18"),
                ),
            )
        }

        val result =
            fixture.service.uploadSeason(SEASON_ID, SEASON_START, SEASON_END)
                .shouldBeInstanceOf<Result.Ok<SeasonImportResult>>()
                .value

        result.created.shouldBeEmpty()
        result.skipped.single().espnEventId shouldBe "e-1"
        result.skipped.single().reason.shouldContain("already linked")
    }

    test("entries with an unparseable ESPN startDate are surfaced in skipped with a clear reason") {
        val fixture =
            Fixture(calendar = listOf(calendarEntry("e-bad", "BadDate", "not-a-date")))

        val result =
            fixture.service.uploadSeason(SEASON_ID, SEASON_START, SEASON_END)
                .shouldBeInstanceOf<Result.Ok<SeasonImportResult>>()
                .value

        result.created.shouldBeEmpty()
        result.skipped.single().espnEventId shouldBe "e-bad"
        result.skipped.single().reason.shouldContain("could not parse")
    }

    test("an empty ESPN calendar returns an empty SeasonImportResult, not an error") {
        val fixture = Fixture(calendar = emptyList())

        val result =
            fixture.service.uploadSeason(SEASON_ID, SEASON_START, SEASON_END)
                .shouldBeInstanceOf<Result.Ok<SeasonImportResult>>()
                .value

        result.created.shouldBeEmpty()
        result.skipped.shouldBeEmpty()
    }
})
