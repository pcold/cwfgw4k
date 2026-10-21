package com.cwfgw.admin

import com.cwfgw.db.TransactionContext
import com.cwfgw.espn.EspnCalendarEntry
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.orFail
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.NewTournament
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.assertions.fail
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000bbb"))

private fun golfer(
    idHex: String,
    pgaPlayerId: String,
    name: String,
): Golfer =
    Golfer(
        id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000$idHex")),
        pgaPlayerId = pgaPlayerId,
        firstName = name.substringBefore(' '),
        lastName = name.substringAfter(' '),
        country = null,
        worldRanking = null,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

/**
 * Every single-key read AdminService once made per row fails the spec, and each batch read is counted, so a path
 * must answer from one batch rather than a loop of either.
 */
private class SingleKeyGolferReadsRefused(
    private val delegate: GolferRepository,
) : GolferRepository by delegate {
    val idBatches = AtomicInteger()
    val pgaIdBatches = AtomicInteger()

    context(ctx: TransactionContext)
    override fun findByIds(ids: Collection<GolferId>): List<Golfer> =
        delegate.findByIds(ids).also { idBatches.incrementAndGet() }

    context(ctx: TransactionContext)
    override fun findByPgaPlayerIds(pgaPlayerIds: Collection<String>): List<Golfer> =
        delegate.findByPgaPlayerIds(pgaPlayerIds).also { pgaIdBatches.incrementAndGet() }

    context(ctx: TransactionContext)
    override fun findById(id: GolferId): Golfer? = fail("golfer ${id.value} was read on its own")

    context(ctx: TransactionContext)
    override fun findByPgaPlayerId(pgaPlayerId: String): Golfer? = fail("pga id $pgaPlayerId was read on its own")
}

/** @see SingleKeyGolferReadsRefused */
private class SingleKeyTournamentReadsRefused(
    private val delegate: TournamentRepository,
) : TournamentRepository by delegate {
    val pgaIdBatches = AtomicInteger()

    context(ctx: TransactionContext)
    override fun findByPgaTournamentIds(pgaTournamentIds: Collection<String>): List<Tournament> =
        delegate.findByPgaTournamentIds(pgaTournamentIds).also { pgaIdBatches.incrementAndGet() }

    context(ctx: TransactionContext)
    override fun findByPgaTournamentId(pgaTournamentId: String): Tournament? =
        fail("ESPN event $pgaTournamentId was read on its own")
}

private class BatchFixture(
    calendar: List<EspnCalendarEntry> = emptyList(),
    seedGolfers: List<Golfer> = emptyList(),
) {
    val tournamentRepo = FakeTournamentRepository()
    val golferRepo = FakeGolferRepository(initial = seedGolfers)
    val teamRepo = FakeTeamRepository()
    val golfers = SingleKeyGolferReadsRefused(golferRepo)
    val tournaments = SingleKeyTournamentReadsRefused(tournamentRepo)
    val service: AdminService

    init {
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        val season = CreateSeasonRequest(leagueId = LeagueId(UUID.randomUUID()), name = "2026", seasonYear = 2026)
        with(noopTransactionContext) { seasonRepo.create(season) }
        val transactor = FakeTransactor()
        service =
            AdminService(
                tx = transactor,
                seasonService = SeasonService(seasonRepo, transactor),
                tournamentService = TournamentService(tournaments, transactor),
                tournamentRepository = tournaments,
                espnService = testEspnService(client = FakeEspnClient(calendar = calendar), tx = transactor),
                golferService = GolferService(golfers, transactor),
                golferRepository = golfers,
                teamRepository = teamRepo,
            )
    }
}

class AdminServiceBatchReadSpec : FunSpec({

    test("a schedule preview finds every already-linked ESPN event in one read") {
        val calendar =
            listOf(
                EspnCalendarEntry(id = "e-1", label = "Sony Open", startDate = "2026-01-15T00:00Z"),
                EspnCalendarEntry(id = "e-2", label = "American Express", startDate = "2026-01-22T00:00Z"),
                EspnCalendarEntry(id = "e-3", label = "Farmers", startDate = "2026-01-29T00:00Z"),
            )
        val fixture = BatchFixture(calendar = calendar)
        val linked =
            with(noopTransactionContext) {
                fixture.tournamentRepo.create(
                    NewTournament(
                        pgaTournamentId = "e-2",
                        name = "American Express",
                        seasonId = SEASON_ID,
                        startDate = LocalDate.parse("2026-01-22"),
                        endDate = LocalDate.parse("2026-01-25"),
                    ),
                )
            }

        val preview =
            fixture.service
                .previewSeasonSchedule(SEASON_ID, LocalDate.parse("2026-01-01"), LocalDate.parse("2026-08-31"))
                .orFail()

        preview.entries.map { it.espnEventId } shouldContainExactly listOf("e-1", "e-3")
        preview.skipped.map { it.reason } shouldContainExactly listOf("already linked to tournament ${linked.id.value}")
        fixture.tournaments.pgaIdBatches.get() shouldBe 1
    }

    test("a roster confirm checks its existing picks and finds its persisted ESPN athletes in one read each") {
        val scottie = golfer(idHex = "301", pgaPlayerId = "espn-1", name = "Scottie Scheffler")
        val rory = golfer(idHex = "302", pgaPlayerId = "espn-2", name = "Rory McIlroy")
        val fixture = BatchFixture(seedGolfers = listOf(scottie, rory))
        val team =
            fullTeam(
                number = 1,
                name = "BROWN",
                ConfirmedPick(round = 1, ownershipPct = 100, assignment = GolferAssignment.Existing(scottie.id)),
                ConfirmedPick(
                    round = 2,
                    ownershipPct = 100,
                    assignment = GolferAssignment.Espn(espnId = "espn-2", name = "Rory McIlroy"),
                ),
            )

        val result = fixture.service.confirmRoster(ConfirmRosterRequest(seasonId = SEASON_ID, teams = listOf(team)))

        result.orFail().teamsCreated shouldBe 1
        val roster = with(noopTransactionContext) { fixture.teamRepo.findRostersBySeason(SEASON_ID) }
        roster.filter { it.draftRound in 1..2 }.sortedBy { it.draftRound }.map { it.golferId } shouldContainExactly
            listOf(scottie.id, rory.id)
        fixture.golfers.idBatches.get() shouldBe 1
        fixture.golfers.pgaIdBatches.get() shouldBe 1
    }

    test("a roster confirm naming an unknown golfer refuses it from the same one read") {
        val scottie = golfer(idHex = "301", pgaPlayerId = "espn-1", name = "Scottie Scheffler")
        val fixture = BatchFixture(seedGolfers = listOf(scottie))
        val ghost = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000999"))
        val team =
            fullTeam(
                number = 1,
                name = "BROWN",
                ConfirmedPick(round = 1, ownershipPct = 100, assignment = GolferAssignment.Existing(scottie.id)),
                ConfirmedPick(round = 2, ownershipPct = 100, assignment = GolferAssignment.Existing(ghost)),
            )

        fixture.service.confirmRoster(ConfirmRosterRequest(seasonId = SEASON_ID, teams = listOf(team))) shouldBe
            Result.Err(AdminError.GolferIdsNotFound(listOf(ghost)))
        fixture.golfers.idBatches.get() shouldBe 1
    }
})
