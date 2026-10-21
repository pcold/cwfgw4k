package com.cwfgw.reports

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.leagues.LeagueId
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.orFail
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentPlayerOverride
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000e01"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000e02"))

private fun tournament(
    id: TournamentId,
    name: String,
    startDate: String,
    status: TournamentStatus,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = null,
        name = name,
        seasonId = SEASON_ID,
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(startDate).plusDays(3),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

/** Every transaction the report and its overlay open, through either service's [Transactor]. */
private class CountingTransactor(
    private val delegate: Transactor = FakeTransactor(),
) : Transactor {
    var opened: Int = 0
        private set

    override suspend fun <A> get(block: context(TransactionContext) () -> A): A {
        opened++
        return delegate.get(block)
    }

    override suspend fun <A> read(block: context(TransactionContext) () -> A): A {
        opened++
        return delegate.read(block)
    }

    override suspend fun <A> update(block: context(WriteTransactionContext) () -> A): A {
        opened++
        return delegate.update(block)
    }
}

/** Only the overlay's context reads link overrides, so a read here is that context being loaded. */
private class RecordingLinkRepository(
    private val delegate: TournamentLinkRepository = FakeTournamentLinkRepository(),
) : TournamentLinkRepository by delegate {
    var overrideReads: Int = 0
        private set

    context(ctx: TransactionContext)
    override fun listByTournaments(ids: Collection<TournamentId>): List<TournamentPlayerOverride> {
        overrideReads++
        return delegate.listByTournaments(ids)
    }
}

/** A finalized Sony ahead of a Masters in play, so a live request has a candidate to overlay. */
private class SnapshotFixture {
    val transactor = CountingTransactor()
    val links = RecordingLinkRepository()
    val espn = FakeEspnClient()
    val service: WeeklyReportService

    init {
        val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
        with(noopTransactionContext) {
            seasonRepo.create(CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026))
        }
        val tournamentRepo =
            FakeTournamentRepository(
                initial =
                    listOf(
                        tournament(SONY_ID, "Sony Open", "2026-01-15", TournamentStatus.Completed),
                        tournament(MASTERS_ID, "The Masters", "2026-04-09", TournamentStatus.InProgress),
                    ),
            )
        val teamRepo = FakeTeamRepository()
        val golferRepo = FakeGolferRepository()
        val espnService =
            testEspnService(
                client = espn,
                seasonRepository = seasonRepo,
                golferRepository = golferRepo,
                teamRepository = teamRepo,
                tournamentRepository = tournamentRepo,
                linkRepository = links,
                tx = transactor,
            )
        service =
            WeeklyReportService(
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepo,
                golferRepository = golferRepo,
                scoringRepository = FakeScoringRepository(),
                liveOverlayService = LiveOverlayService(espnService),
                tx = transactor,
            )
    }
}

private class SnapshotEndpoint(
    val name: String,
    val requestBase: suspend (WeeklyReportService) -> Unit,
    val requestLive: suspend (WeeklyReportService) -> Unit,
)

private val ENDPOINTS =
    listOf(
        SnapshotEndpoint(
            name = "getReport",
            requestBase = { service -> service.getReport(SEASON_ID, MASTERS_ID).orFail() },
            requestLive = { service -> service.getReport(SEASON_ID, MASTERS_ID, live = true).orFail() },
        ),
        SnapshotEndpoint(
            name = "getRankings",
            requestBase = { service -> service.getRankings(SEASON_ID, MASTERS_ID).orFail() },
            requestLive = { service -> service.getRankings(SEASON_ID, MASTERS_ID, live = true).orFail() },
        ),
        SnapshotEndpoint(
            name = "getPlayerRankings",
            requestBase = { service -> service.getPlayerRankings(SEASON_ID, MASTERS_ID).orFail() },
            requestLive = { service -> service.getPlayerRankings(SEASON_ID, MASTERS_ID, live = true).orFail() },
        ),
    )

class LiveOverlaySnapshotSpec : FunSpec({

    ENDPOINTS.forEach { endpoint ->
        test("${endpoint.name} with live=true reads the report and the overlay's context in one transaction") {
            val fixture = SnapshotFixture()

            endpoint.requestLive(fixture.service)

            fixture.transactor.opened shouldBe 1
            fixture.links.overrideReads shouldBe 1
            fixture.espn.fetchCalls.shouldNotBeEmpty()
        }

        test("${endpoint.name} with live=false never loads the overlay's context") {
            val fixture = SnapshotFixture()

            endpoint.requestBase(fixture.service)

            fixture.transactor.opened shouldBe 1
            fixture.links.overrideReads shouldBe 0
            fixture.espn.fetchCalls.shouldBeEmpty()
        }
    }

    test("a live report on a completed tournament has no candidate, so it never loads the overlay's context") {
        val fixture = SnapshotFixture()

        fixture.service.getReport(SEASON_ID, SONY_ID, live = true).orFail()

        fixture.transactor.opened shouldBe 1
        fixture.links.overrideReads shouldBe 0
        fixture.espn.fetchCalls.shouldBeEmpty()
    }

    test("live rankings without a cutoff have no candidate, so they never load the overlay's context") {
        val fixture = SnapshotFixture()

        fixture.service.getRankings(SEASON_ID, throughTournamentId = null, live = true).orFail()

        fixture.transactor.opened shouldBe 1
        fixture.links.overrideReads shouldBe 0
        fixture.espn.fetchCalls.shouldBeEmpty()
    }
})
