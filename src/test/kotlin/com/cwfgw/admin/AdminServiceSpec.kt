package com.cwfgw.admin

import com.cwfgw.espn.EspnCalendarEntry
import com.cwfgw.espn.EspnService
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
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking
import org.jooq.DSLContext
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val SEASON_START = LocalDate.parse("2026-01-01")
private val SEASON_END = LocalDate.parse("2026-08-31")

private fun golfer(
    idHex: String,
    firstName: String,
    lastName: String,
): Golfer =
    Golfer(
        id = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000$idHex")),
        pgaPlayerId = "pga-$idHex",
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        active = true,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private const val ROSTER_HEADER = "team_number\tteam_name\tround\tplayer_name\townership_pct"

private fun calendarEntry(
    id: String,
    label: String,
    isoDate: String,
): EspnCalendarEntry = EspnCalendarEntry(id = id, label = label, startDate = isoDate)

private class Fixture(
    seedSeason: Boolean = true,
    calendar: List<EspnCalendarEntry> = emptyList(),
    upstreamError: EspnUpstreamException? = null,
    seedGolfers: List<Golfer> = emptyList(),
) {
    val seasonRepo: FakeSeasonRepository
    val tournamentRepo = FakeTournamentRepository()
    val golferRepo = FakeGolferRepository(initial = seedGolfers)
    val teamRepo = FakeTeamRepository()
    val service: AdminService

    init {
        val seasonId = SEASON_ID
        seasonRepo =
            FakeSeasonRepository(idFactory = { seasonId })
        if (seedSeason) {
            runBlocking {
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
                )
            }
        }
        val tournamentService = TournamentService(tournamentRepo)
        val golferService = GolferService(golferRepo)
        val teamService = TeamService(teamRepo)
        val espnService =
            EspnService(
                client = FakeEspnClient(calendar = calendar, upstreamError = upstreamError),
                tournamentService = tournamentService,
                golferService = golferService,
                teamService = teamService,
                seasonService = SeasonService(seasonRepo),
                tournamentLinkRepository = FakeTournamentLinkRepository(),
            )
        service =
            AdminService(
                dsl = stubDsl(),
                seasonService = SeasonService(seasonRepo),
                tournamentService = tournamentService,
                espnService = espnService,
                golferService = golferService,
                teamService = teamService,
            )
    }
}

// confirmRoster's transactional path is exercised in AdminServiceConfirmRosterSpec
// against a real Postgres harness; the fake-fixture tests in this file only
// reach validation-path branches that short-circuit before the transaction.
private fun stubDsl(): DSLContext = DSL.using(SQLDialect.POSTGRES)

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

    test("week labels are 1-indexed by chronological position; same-ISO-week tournaments suffix a/b") {
        // Sony Open and American Express are different ISO weeks; the Masters
        // and a fictional alt-field event share a week and should split a/b.
        val fixture =
            Fixture(
                calendar =
                    listOf(
                        // intentionally out of chronological order to prove sorting
                        calendarEntry("e-2", "American Express", "2026-01-22T00:00Z"),
                        calendarEntry("e-1", "Sony Open", "2026-01-15T00:00Z"),
                        calendarEntry("e-3a", "The Masters", "2026-04-09T00:00Z"),
                        calendarEntry("e-3b", "Corales Puntacana", "2026-04-09T00:00Z"),
                    ),
            )

        val created =
            fixture.service.uploadSeason(SEASON_ID, SEASON_START, SEASON_END)
                .shouldBeInstanceOf<Result.Ok<SeasonImportResult>>()
                .value
                .created

        created.map { it.name to it.week } shouldBe
            listOf(
                "Sony Open" to "1",
                "American Express" to "2",
                "The Masters" to "3a",
                "Corales Puntacana" to "3b",
            )
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
        runBlocking {
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

    test("previewRoster matches each pick to a single golfer by full name and reports counts") {
        val scottie = golfer("201", "Scottie", "Scheffler")
        val rory = golfer("202", "Rory", "McIlroy")
        val fixture = Fixture(seedGolfers = listOf(scottie, rory))

        val tsv =
            """
            $ROSTER_HEADER
            1	BROWN	1	Scottie Scheffler	75
            1	BROWN	2	Rory McIlroy	50
            """.trimIndent()

        val result =
            fixture.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.totalPicks shouldBe 2
        result.matchedCount shouldBe 2
        result.ambiguousCount shouldBe 0
        result.unmatchedCount shouldBe 0
        val team = result.teams.single()
        team.teamNumber shouldBe 1
        team.teamName shouldBe "BROWN"
        team.picks.map { it.match } shouldContainExactly
            listOf(
                PickMatch.Matched(golferId = scottie.id, golferName = "Scottie Scheffler"),
                PickMatch.Matched(golferId = rory.id, golferName = "Rory McIlroy"),
            )
    }

    test("previewRoster matches names case-insensitively") {
        val scottie = golfer("201", "Scottie", "Scheffler")
        val fixture = Fixture(seedGolfers = listOf(scottie))

        val tsv =
            """
            $ROSTER_HEADER
            1	BROWN	1	scottie SCHEFFLER	75
            """.trimIndent()

        val result =
            fixture.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.matchedCount shouldBe 1
        result.teams.single().picks.single().match shouldBe
            PickMatch.Matched(golferId = scottie.id, golferName = "Scottie Scheffler")
    }

    test("previewRoster reports NoMatch when the player name doesn't exist in the golfers table") {
        val fixture = Fixture(seedGolfers = listOf(golfer("201", "Scottie", "Scheffler")))

        val tsv =
            """
            $ROSTER_HEADER
            1	BROWN	1	Phantom Player	50
            """.trimIndent()

        val result =
            fixture.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.matchedCount shouldBe 0
        result.unmatchedCount shouldBe 1
        result.teams.single().picks.single().match shouldBe PickMatch.NoMatch
    }

    test("previewRoster reports Ambiguous when multiple golfers share the same full name") {
        val younger = golfer("203", "Justin", "Thomas")
        val older = golfer("204", "Justin", "Thomas")
        val fixture = Fixture(seedGolfers = listOf(younger, older))

        val tsv =
            """
            $ROSTER_HEADER
            1	BROWN	1	Justin Thomas	50
            """.trimIndent()

        val result =
            fixture.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.ambiguousCount shouldBe 1
        result.matchedCount shouldBe 0
        val match = result.teams.single().picks.single().match.shouldBeInstanceOf<PickMatch.Ambiguous>()
        match.candidates.map { it.golferId } shouldContainExactly listOf(younger.id, older.id)
    }

    test("previewRoster summary counts mix matched, ambiguous, and unmatched across multiple teams") {
        val scottie = golfer("201", "Scottie", "Scheffler")
        val twinA = golfer("203", "Justin", "Thomas")
        val twinB = golfer("204", "Justin", "Thomas")
        val fixture = Fixture(seedGolfers = listOf(scottie, twinA, twinB))

        val tsv =
            """
            $ROSTER_HEADER
            1	BROWN	1	Scottie Scheffler	75
            1	BROWN	2	Phantom Player	50
            2	WOMBLE	1	Justin Thomas	60
            2	WOMBLE	2	Scottie Scheffler	25
            """.trimIndent()

        val result =
            fixture.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.totalPicks shouldBe 4
        result.matchedCount shouldBe 2
        result.ambiguousCount shouldBe 1
        result.unmatchedCount shouldBe 1
        result.teams.map { it.teamName } shouldContainExactly listOf("BROWN", "WOMBLE")
    }

    test("previewRoster returns InvalidRoster when the TSV header is wrong — no golfer lookup happens") {
        val fixture = Fixture(seedGolfers = listOf(golfer("201", "Scottie", "Scheffler")))

        val err =
            fixture.service.previewRoster("nope\nrow")
                .shouldBeInstanceOf<Result.Err<AdminError>>()
                .error
                .shouldBeInstanceOf<AdminError.InvalidRoster>()
                .parseError
                .shouldBeInstanceOf<RosterParseError.InvalidHeader>()
        err.message.shouldContain("Header row must be exactly")
    }

    test("previewRoster returns InvalidRoster wrapping per-row errors when rows are malformed") {
        val fixture = Fixture(seedGolfers = emptyList())

        val tsv =
            """
            $ROSTER_HEADER
            1	BROWN	notanumber	Scottie Scheffler	50
            """.trimIndent()

        val rows =
            fixture.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Err<AdminError>>()
                .error
                .shouldBeInstanceOf<AdminError.InvalidRoster>()
                .parseError
                .shouldBeInstanceOf<RosterParseError.InvalidRows>()
                .errors
        rows shouldHaveSize 1
        rows.single().message.shouldContain("invalid round")
    }

    test("previewRoster matches inactive golfers — operator's TSV may pick a deactivated player") {
        val deactivated = golfer("201", "Scottie", "Scheffler").copy(active = false)
        val fixture = Fixture(seedGolfers = listOf(deactivated))

        val tsv =
            """
            $ROSTER_HEADER
            1	BROWN	1	Scottie Scheffler	75
            """.trimIndent()

        val result =
            fixture.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.matchedCount shouldBe 1
        result.teams.single().picks.single().match shouldBe
            PickMatch.Matched(golferId = deactivated.id, golferName = "Scottie Scheffler")
    }

    test("PreviewPick carries init-capped name plus round and ownership through unchanged") {
        val scottie = golfer("201", "Scottie", "Scheffler")
        val fixture = Fixture(seedGolfers = listOf(scottie))

        val tsv =
            """
            $ROSTER_HEADER
            1	BROWN	2	scottie SCHEFFLER	60
            """.trimIndent()

        val pick =
            fixture.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value
                .teams.single().picks.single()

        pick.playerName shouldBe "Scottie Scheffler"
        pick.round shouldBe 2
        pick.ownershipPct shouldBe 60
    }

    test("previewRoster requires a full first+last match — bare last name does not match") {
        val scottie = golfer("201", "Scottie", "Scheffler")
        val fixture = Fixture(seedGolfers = listOf(scottie))

        val tsv =
            """
            $ROSTER_HEADER
            1	BROWN	1	Scheffler	75
            """.trimIndent()

        val result =
            fixture.service.previewRoster(tsv)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.unmatchedCount shouldBe 1
        result.teams.single().picks.single().match shouldBe PickMatch.NoMatch
    }

    test("previewRoster on a header-only TSV returns an empty preview with zero counts") {
        val fixture = Fixture(seedGolfers = listOf(golfer("201", "Scottie", "Scheffler")))

        val result =
            fixture.service.previewRoster(ROSTER_HEADER)
                .shouldBeInstanceOf<Result.Ok<RosterPreviewResult>>()
                .value

        result.teams.shouldBeEmpty()
        result.totalPicks shouldBe 0
        result.matchedCount shouldBe 0
        result.ambiguousCount shouldBe 0
        result.unmatchedCount shouldBe 0
    }

    // confirmRoster's persisting path is exercised against a real Postgres
    // harness in AdminServiceConfirmRosterSpec — fakes can't honestly model
    // the all-or-nothing transactional semantics. The validation-path tests
    // below stay on fakes since they short-circuit before the transaction
    // opens.

    test("confirmRoster returns SeasonNotFound when the season id doesn't exist (and writes nothing)") {
        val fixture = Fixture(seedSeason = false, seedGolfers = listOf(golfer("201", "Scottie", "Scheffler")))

        val request =
            ConfirmRosterRequest(
                seasonId = SEASON_ID,
                teams =
                    listOf(
                        ConfirmedTeam(
                            teamNumber = 1,
                            teamName = "BROWN",
                            picks =
                                listOf(
                                    ConfirmedPick(
                                        round = 1,
                                        ownershipPct = 75,
                                        assignment = GolferAssignment.New("New", "Player"),
                                    ),
                                ),
                        ),
                    ),
            )

        fixture.service.confirmRoster(request) shouldBe Result.Err(AdminError.SeasonNotFound(SEASON_ID))
        fixture.teamRepo.findBySeason(SEASON_ID).shouldBeEmpty()
        fixture.golferRepo.findAll(activeOnly = false, search = null) shouldHaveSize 1
    }

    test("confirmRoster collects all bad existing-golfer ids into one GolferIdsNotFound and writes nothing") {
        val scottie = golfer("201", "Scottie", "Scheffler")
        val fixture = Fixture(seedGolfers = listOf(scottie))
        val ghostA = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000901"))
        val ghostB = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000902"))

        val request =
            ConfirmRosterRequest(
                seasonId = SEASON_ID,
                teams =
                    listOf(
                        ConfirmedTeam(
                            teamNumber = 1,
                            teamName = "BROWN",
                            picks =
                                listOf(
                                    ConfirmedPick(
                                        round = 1,
                                        ownershipPct = 75,
                                        assignment = GolferAssignment.Existing(ghostA),
                                    ),
                                    ConfirmedPick(
                                        round = 2,
                                        ownershipPct = 50,
                                        assignment = GolferAssignment.Existing(scottie.id),
                                    ),
                                ),
                        ),
                        ConfirmedTeam(
                            teamNumber = 2,
                            teamName = "WOMBLE",
                            picks =
                                listOf(
                                    ConfirmedPick(
                                        round = 1,
                                        ownershipPct = 25,
                                        assignment = GolferAssignment.Existing(ghostB),
                                    ),
                                ),
                        ),
                    ),
            )

        val err =
            fixture.service.confirmRoster(request)
                .shouldBeInstanceOf<Result.Err<AdminError>>()
                .error
                .shouldBeInstanceOf<AdminError.GolferIdsNotFound>()
        err.ids shouldContainExactlyInAnyOrder listOf(ghostA, ghostB)
        fixture.teamRepo.findBySeason(SEASON_ID).shouldBeEmpty()
    }

    test("confirmRoster on an empty teams list succeeds with zero counts and no writes") {
        val fixture = Fixture()

        val result =
            fixture.service.confirmRoster(ConfirmRosterRequest(seasonId = SEASON_ID, teams = emptyList()))
                .shouldBeInstanceOf<Result.Ok<RosterUploadResult>>()
                .value

        result.teamsCreated shouldBe 0
        result.golfersCreated shouldBe 0
        result.teams.shouldBeEmpty()
        fixture.teamRepo.findBySeason(SEASON_ID).shouldBeEmpty()
    }
})
