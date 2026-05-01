package com.cwfgw.tournamentLinks

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aa1"))

private fun tournament(
    id: TournamentId = TournamentId(UUID.randomUUID()),
    status: TournamentStatus = TournamentStatus.Upcoming,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = "espn-1",
        name = "Zurich Classic",
        seasonId = SEASON_ID,
        startDate = LocalDate.parse("2026-04-23"),
        endDate = LocalDate.parse("2026-04-26"),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        isTeamEvent = true,
        week = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun golfer(
    id: GolferId = GolferId(UUID.randomUUID()),
    firstName: String = "Matt",
    lastName: String = "Fitzpatrick",
): Golfer =
    Golfer(
        id = id,
        pgaPlayerId = null,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        active = true,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun service(
    tournaments: List<Tournament> = emptyList(),
    golfers: List<Golfer> = emptyList(),
    initialOverrides: List<TournamentPlayerOverride> = emptyList(),
): TournamentLinkService =
    TournamentLinkService(
        repository = FakeTournamentLinkRepository(initial = initialOverrides),
        tournamentRepository = FakeTournamentRepository(initial = tournaments),
        golferRepository = FakeGolferRepository(initial = golfers),
        tx = FakeTransactor(),
    )

class TournamentLinkServiceSpec : FunSpec({

    test("upsert stores an override on an upcoming tournament") {
        val t = tournament()
        val g = golfer()
        val svc = service(tournaments = listOf(t), golfers = listOf(g))

        val result =
            svc.upsert(t.id, UpsertTournamentPlayerOverrideRequest(espnCompetitorId = "abc-123", golferId = g.id))

        result.shouldBeInstanceOf<Result.Ok<TournamentPlayerOverride>>()
        result.value.tournamentId shouldBe t.id
        result.value.golferId shouldBe g.id
    }

    test("upsert returns TournamentNotFound when the tournament doesn't exist") {
        val missingId = TournamentId(UUID.randomUUID())
        val g = golfer()
        val svc = service(golfers = listOf(g))

        val result =
            svc.upsert(missingId, UpsertTournamentPlayerOverrideRequest(espnCompetitorId = "abc-123", golferId = g.id))

        result.shouldBeInstanceOf<Result.Err<TournamentLinkError.TournamentNotFound>>()
        result.error.id shouldBe missingId
    }

    test("upsert returns TournamentFinalized when the tournament has status=Completed") {
        val t = tournament(status = TournamentStatus.Completed)
        val g = golfer()
        val svc = service(tournaments = listOf(t), golfers = listOf(g))

        val result =
            svc.upsert(t.id, UpsertTournamentPlayerOverrideRequest(espnCompetitorId = "abc-123", golferId = g.id))

        result.shouldBeInstanceOf<Result.Err<TournamentLinkError.TournamentFinalized>>()
        result.error.id shouldBe t.id
    }

    test("upsert returns GolferNotFound when the requested golfer doesn't exist") {
        val t = tournament()
        val ghost = GolferId(UUID.randomUUID())
        val svc = service(tournaments = listOf(t))

        val result =
            svc.upsert(t.id, UpsertTournamentPlayerOverrideRequest(espnCompetitorId = "abc-123", golferId = ghost))

        result.shouldBeInstanceOf<Result.Err<TournamentLinkError.GolferNotFound>>()
        result.error.id shouldBe ghost
    }

    test("delete removes an existing override and returns true") {
        val t = tournament()
        val g = golfer()
        val svc =
            service(
                tournaments = listOf(t),
                golfers = listOf(g),
                initialOverrides =
                    listOf(TournamentPlayerOverride(t.id, "abc-123", g.id)),
            )

        val result = svc.delete(t.id, "abc-123")

        result.shouldBeInstanceOf<Result.Ok<Boolean>>()
        result.value shouldBe true
    }

    test("delete returns Ok(false) when no matching override exists — idempotent") {
        val t = tournament()
        val svc = service(tournaments = listOf(t))

        val result = svc.delete(t.id, "never-stored")

        result.shouldBeInstanceOf<Result.Ok<Boolean>>()
        result.value shouldBe false
    }

    test("delete is blocked once the tournament is finalized") {
        val t = tournament(status = TournamentStatus.Completed)
        val g = golfer()
        val svc =
            service(
                tournaments = listOf(t),
                golfers = listOf(g),
                initialOverrides = listOf(TournamentPlayerOverride(t.id, "abc-123", g.id)),
            )

        val result = svc.delete(t.id, "abc-123")

        result.shouldBeInstanceOf<Result.Err<TournamentLinkError.TournamentFinalized>>()
    }

    test("overrideMap returns the persisted overrides keyed by espnCompetitorId") {
        val t = tournament()
        val g = golfer()
        val svc =
            service(
                tournaments = listOf(t),
                golfers = listOf(g),
                initialOverrides =
                    listOf(
                        TournamentPlayerOverride(t.id, "abc-123", g.id),
                        TournamentPlayerOverride(t.id, "xyz-789", g.id),
                    ),
            )

        runBlocking {
            val map = svc.overrideMap(t.id)
            map.keys shouldHaveSize 2
            map["abc-123"] shouldBe g.id
            map["xyz-789"] shouldBe g.id
        }
    }
})
