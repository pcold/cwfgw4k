package com.cwfgw.admin

import com.cwfgw.espn.EspnCalendarEntry
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.EspnUpstreamException
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

/**
 * Focused coverage for the ESPN-fishing fallback in
 * [AdminService.previewRoster] — picks that don't match the local `golfers`
 * table trigger a pull from ESPN's recent-active player pool, persist any
 * newly-discovered athletes, and re-match. Lives in its own spec so the
 * core matching cases stay in [AdminServiceSpec] and detekt's LargeClass
 * threshold doesn't trip.
 */
class AdminServicePreviewRosterEspnSpec : FunSpec({

    val rosterHeader = "team_number\tteam_name\tround\tplayer_name\townership_pct"
    val recentDate: LocalDate = LocalDate.parse("2026-04-01")
    val recentCalendarEntry =
        EspnCalendarEntry(id = "e-recent", label = "Recent Event", startDate = "2026-04-01T00:00Z")

    fun fixture(
        seedGolfers: List<Golfer> = emptyList(),
        calendar: List<EspnCalendarEntry> = emptyList(),
        scoreboards: Map<LocalDate, List<EspnTournament>> = emptyMap(),
        upstreamError: EspnUpstreamException? = null,
    ): TestFixture {
        val seasonId = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
        val leagueId = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
        val seasonRepo = FakeSeasonRepository(idFactory = { seasonId })
        runBlocking {
            seasonRepo.create(CreateSeasonRequest(leagueId = leagueId, name = "2026 Season", seasonYear = 2026))
        }
        val tournamentRepo = FakeTournamentRepository()
        val golferRepo = FakeGolferRepository(initial = seedGolfers)
        val teamRepo = FakeTeamRepository()
        val tournamentService = TournamentService(tournamentRepo)
        val golferService = GolferService(golferRepo, FakeTransactor())
        val teamService = TeamService(teamRepo)
        val client =
            FakeEspnClient(
                tournamentsByDate = scoreboards,
                calendar = calendar,
                upstreamError = upstreamError,
            )
        val espnService =
            EspnService(
                client = client,
                tournamentService = tournamentService,
                golferService = golferService,
                teamService = teamService,
                seasonService = SeasonService(seasonRepo),
                tournamentLinkRepository = FakeTournamentLinkRepository(),
            )
        return TestFixture(
            golferRepo = golferRepo,
            client = client,
            service =
                AdminService(
                    dsl = DSL.using(SQLDialect.POSTGRES),
                    seasonService = SeasonService(seasonRepo),
                    tournamentService = tournamentService,
                    espnService = espnService,
                    golferService = golferService,
                    golferRepository = golferRepo,
                    teamService = teamService,
                ),
        )
    }

    test("fishes ESPN when picks are unmatched, persists new golfers, and rematches") {
        val f =
            fixture(
                calendar = listOf(recentCalendarEntry),
                scoreboards =
                    mapOf(
                        recentDate to
                            listOf(
                                espnEvent(competitors = listOf(espnPlayer("espn-9478", "Scottie Scheffler"))),
                            ),
                    ),
            )

        val tsv =
            """
            $rosterHeader
            1	BROWN	1	Scottie Scheffler	100
            """.trimIndent()

        val result =
            f.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.matchedCount shouldBe 1
        val match = result.teams.single().picks.single().match.shouldBeInstanceOf<PickMatch.Matched>()
        match.golferName shouldBe "Scottie Scheffler"

        // The ESPN-fish persisted the golfer with its pga_player_id so the next preview hits DB directly.
        val persisted =
            with(noopTransactionContext) { f.golferRepo.findAll(activeOnly = false, search = null) }
                .single { it.pgaPlayerId == "espn-9478" }
        persisted.firstName shouldBe "Scottie"
        persisted.lastName shouldBe "Scheffler"
    }

    test("skips the ESPN fetch entirely when every pick already matches in DB") {
        val scottie =
            Golfer(
                id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000201")),
                pgaPlayerId = "pga-201",
                firstName = "Scottie",
                lastName = "Scheffler",
                country = null,
                worldRanking = null,
                active = true,
                updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
        val f = fixture(seedGolfers = listOf(scottie), calendar = listOf(recentCalendarEntry))

        val tsv =
            """
            $rosterHeader
            1	BROWN	1	Scottie Scheffler	100
            """.trimIndent()

        f.service.previewRoster(tsv).shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()

        // Calendar fetch is the cheap canary that the fish-mode fallback ran. It should NOT have.
        f.client.calendarCalls shouldBe 0
        with(noopTransactionContext) {
            f.golferRepo.findAll(activeOnly = false, search = null)
        } shouldHaveSize 1
    }

    test("matches ESPN names with NFD-decomposable accents against unaccented roster spellings") {
        // 'é' decomposes to 'e' + combining acute under NFD. Same for ñ, í, ó, ü.
        val f =
            fixture(
                calendar = listOf(recentCalendarEntry),
                scoreboards =
                    mapOf(
                        recentDate to
                            listOf(
                                espnEvent(competitors = listOf(espnPlayer("espn-jose", "José Ballester"))),
                            ),
                    ),
            )

        val tsv =
            """
            $rosterHeader
            1	BROWN	1	Jose Ballester	50
            """.trimIndent()

        val result =
            f.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.matchedCount shouldBe 1
        val match = result.teams.single().picks.single().match.shouldBeInstanceOf<PickMatch.Matched>()
        match.golferName shouldBe "José Ballester"
    }

    test("matches Scandinavian ø/å/æ via explicit folding (not NFD-decomposable)") {
        val f =
            fixture(
                calendar = listOf(recentCalendarEntry),
                scoreboards =
                    mapOf(
                        recentDate to
                            listOf(
                                espnEvent(
                                    competitors =
                                        listOf(
                                            espnPlayer("espn-norg", "Niklas Nørgaard-Petersen"),
                                            espnPlayer("espn-aberg", "Ludvig Åberg"),
                                            espnPlayer("espn-hojg", "Rasmus Højgaard"),
                                        ),
                                ),
                            ),
                    ),
            )

        val tsv =
            """
            $rosterHeader
            1	WOMBLE	1	Niklas Norgaard-Petersen	50
            1	WOMBLE	2	Ludvig Aberg	50
            1	WOMBLE	3	Rasmus Hojgaard	50
            """.trimIndent()

        val result =
            f.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.matchedCount shouldBe 3
        result.unmatchedCount shouldBe 0
        result.teams.single().picks.map { (it.match as PickMatch.Matched).golferName } shouldBe
            listOf("Niklas Nørgaard-Petersen", "Ludvig Åberg", "Rasmus Højgaard")
    }

    test("ESPN team-partner rows are skipped when building the active player pool") {
        val partner =
            EspnCompetitor(
                espnId = "team:5:1",
                name = "Phantom Partner",
                order = 1,
                scoreStr = null,
                scoreToPar = null,
                totalStrokes = null,
                roundScores = emptyList(),
                position = 1,
                status = EspnStatus.Active,
                isTeamPartner = true,
                pairKey = "team:5",
            )
        val f =
            fixture(
                calendar = listOf(recentCalendarEntry),
                scoreboards = mapOf(recentDate to listOf(espnEvent(competitors = listOf(partner)))),
            )

        val tsv =
            """
            $rosterHeader
            1	BROWN	1	Phantom Partner	50
            """.trimIndent()

        val result =
            f.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        // The synthetic team:5:1 id must NEVER land in golfers.pga_player_id, so the pick stays unmatched.
        result.unmatchedCount shouldBe 1
        with(noopTransactionContext) { f.golferRepo.findAll(activeOnly = false, search = null) }.shouldHaveSize(0)
    }

    test("treats hyphens and spaces as equivalent so 'Byeong Hun An' matches ESPN's 'Byeong-Hun An'") {
        val f =
            fixture(
                calendar = listOf(recentCalendarEntry),
                scoreboards =
                    mapOf(
                        recentDate to
                            listOf(espnEvent(competitors = listOf(espnPlayer("espn-an", "Byeong-Hun An")))),
                    ),
            )

        val tsv =
            """
            $rosterHeader
            1	BLAU	5	Byeong Hun An	100
            """.trimIndent()

        val result =
            f.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.matchedCount shouldBe 1
        val match = result.teams.single().picks.single().match.shouldBeInstanceOf<PickMatch.Matched>()
        match.golferName shouldBe "Byeong-Hun An"
    }

    test("does not double-create a golfer when ESPN returns an athlete already in the table") {
        // The unique constraint on golfers.pga_player_id would blow up if this filter
        // regressed; assert via row count to catch the regression in the fake too.
        val seeded =
            Golfer(
                id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000c01")),
                pgaPlayerId = "espn-9478",
                firstName = "Scottie",
                lastName = "Scheffler",
                country = null,
                worldRanking = null,
                active = true,
                updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
            )
        val f =
            fixture(
                seedGolfers = listOf(seeded),
                calendar = listOf(recentCalendarEntry),
                scoreboards =
                    mapOf(
                        recentDate to
                            listOf(
                                espnEvent(
                                    competitors =
                                        listOf(
                                            espnPlayer("espn-9478", "Scottie Scheffler"),
                                            espnPlayer("espn-9999", "Brand New"),
                                        ),
                                ),
                            ),
                    ),
            )

        // Roster has Scottie (already in DB) + a new player to force fish-mode.
        val tsv =
            """
            $rosterHeader
            1	BROWN	1	Scottie Scheffler	100
            1	BROWN	2	Brand New	100
            """.trimIndent()

        f.service.previewRoster(tsv).shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()

        val golfers = with(noopTransactionContext) { f.golferRepo.findAll(activeOnly = false, search = null) }
        golfers shouldHaveSize 2
        golfers.count { it.pgaPlayerId == "espn-9478" } shouldBe 1
        golfers.single { it.pgaPlayerId == "espn-9999" }.firstName shouldBe "Brand"
    }

    test("preview survives ESPN being unavailable — picks stay no_match instead of failing") {
        val f =
            fixture(
                calendar = listOf(recentCalendarEntry),
                upstreamError = EspnUpstreamException(status = 503, message = "ESPN down"),
            )

        val tsv =
            """
            $rosterHeader
            1	BROWN	1	Scottie Scheffler	100
            """.trimIndent()

        val result =
            f.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.unmatchedCount shouldBe 1
        result.matchedCount shouldBe 0
        // No golfer was persisted because the fish step bailed cleanly.
        with(noopTransactionContext) { f.golferRepo.findAll(activeOnly = false, search = null) }.shouldHaveSize(0)
    }
})

private data class TestFixture(
    val service: AdminService,
    val golferRepo: FakeGolferRepository,
    val client: FakeEspnClient,
)

private fun espnPlayer(
    espnId: String,
    name: String,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = 1,
        scoreStr = null,
        scoreToPar = null,
        totalStrokes = null,
        roundScores = emptyList(),
        position = 1,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

private fun espnEvent(competitors: List<EspnCompetitor>): EspnTournament =
    EspnTournament(
        espnId = "evt-1",
        name = "Test Event",
        completed = true,
        competitors = competitors,
        isTeamEvent = false,
    )
