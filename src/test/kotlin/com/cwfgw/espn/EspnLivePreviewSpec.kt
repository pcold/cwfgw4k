package com.cwfgw.espn

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.RosterEntryId
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.orFail
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val TOURNAMENT_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))
private val TEAM_A_ID = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c01"))
private val TEAM_B_ID = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c02"))
private val SCOTTIE_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d01"))
private val RORY_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d02"))
private val START_DATE: LocalDate = LocalDate.parse("2026-04-09")

private fun espnCompetitor(
    espnId: String,
    name: String,
    position: Int,
    scoreToPar: Int,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = scoreToPar.toString(),
        scoreToPar = scoreToPar,
        totalStrokes = 280,
        roundScores = listOf(70, 70, 70),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

private fun espnTournament(
    espnId: String = "401580999",
    name: String = "The Masters",
    competitors: List<EspnCompetitor> = emptyList(),
    isTeamEvent: Boolean = false,
): EspnTournament =
    EspnTournament(
        espnId = espnId,
        name = name,
        completed = false,
        competitors = competitors,
        isTeamEvent = isTeamEvent,
    )

private fun golfer(
    id: GolferId,
    firstName: String,
    lastName: String,
    pgaPlayerId: String? = null,
): Golfer =
    Golfer(
        id = id,
        pgaPlayerId = pgaPlayerId,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        active = true,
        updatedAt = Instant.EPOCH,
    )

private fun team(
    id: TeamId,
    name: String,
): Team =
    Team(
        id = id,
        seasonId = SEASON_ID,
        ownerName = "Owner $name",
        teamName = name,
        teamNumber = null,
        createdAt = Instant.EPOCH,
        updatedAt = Instant.EPOCH,
    )

private fun rosterEntry(
    teamId: TeamId,
    golferId: GolferId,
    draftRound: Int,
    ownershipPct: BigDecimal = BigDecimal(100),
): RosterEntry =
    RosterEntry(
        id = RosterEntryId(UUID.randomUUID()),
        teamId = teamId,
        golferId = golferId,
        acquiredVia = "draft",
        draftRound = draftRound,
        ownershipPct = ownershipPct,
        acquiredAt = Instant.EPOCH,
        droppedAt = null,
        isActive = true,
    )

private fun tournament(
    pgaTournamentId: String? = "401580999",
    isTeamEvent: Boolean = false,
    payoutMultiplier: BigDecimal = BigDecimal.ONE,
): Tournament =
    Tournament(
        id = TOURNAMENT_ID,
        pgaTournamentId = pgaTournamentId,
        name = "The Masters",
        seasonId = SEASON_ID,
        startDate = START_DATE,
        endDate = START_DATE.plusDays(3),
        courseName = null,
        status = TournamentStatus.InProgress,
        purseAmount = null,
        payoutMultiplier = payoutMultiplier,
        week = null,
        isTeamEvent = isTeamEvent,
        createdAt = Instant.EPOCH,
    )

@Suppress("LongParameterList")
private class PreviewFixture(
    val initialTournaments: List<Tournament> = emptyList(),
    initialTeams: List<Team> = emptyList(),
    initialGolfers: List<Golfer> = emptyList(),
    initialRosters: List<RosterEntry> = emptyList(),
    tournamentsByDate: Map<LocalDate, List<EspnTournament>> = emptyMap(),
    upstreamError: EspnUpstreamException? = null,
) {
    val seasonRepo = FakeSeasonRepository(idFactory = { SEASON_ID })
    val tournamentRepo = FakeTournamentRepository(initial = initialTournaments)
    val teamRepo = FakeTeamRepository(initialTeams = initialTeams, initialRoster = initialRosters)
    val golferRepo = FakeGolferRepository(initial = initialGolfers)
    val client = FakeEspnClient(tournamentsByDate = tournamentsByDate, upstreamError = upstreamError)
    val service: EspnService

    init {
        kotlinx.coroutines.runBlocking {
            with(noopTransactionContext) {
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
                )
            }
        }
        val transactor = FakeTransactor()
        val linkRepo = FakeTournamentLinkRepository()
        service =
            EspnService(
                client = client,
                seasonRepository = seasonRepo,
                golferRepository = golferRepo,
                teamRepository = teamRepo,
                tournamentRepository = tournamentRepo,
                linkRepository = linkRepo,
                tx = transactor,
            )
    }
}

// The context matches only the tournaments handed to it; it never reads them back out of the repository.
private suspend fun PreviewFixture.previewFor(candidate: Tournament): Result<EspnLivePreview, LivePreviewError> =
    service.previewForTournament(service.loadLivePreviewContext(SEASON_ID, initialTournaments), candidate)

class EspnLivePreviewSpec : FunSpec({

    test("previewForTournament answers NoMatchingEvent when ESPN reports no tournaments for the date") {
        val fixture = PreviewFixture(tournamentsByDate = mapOf(START_DATE to emptyList()))

        fixture.previewFor(tournament()) shouldBe Result.Err(LivePreviewError.NoMatchingEvent)
    }

    test("previewForTournament picks the event carrying the tournament's pga_tournament_id among several") {
        val sony = espnTournament(espnId = "espn-sony", name = "Sony Open")
        val masters = espnTournament(espnId = "espn-masters")
        val fixture = PreviewFixture(tournamentsByDate = mapOf(START_DATE to listOf(sony, masters)))

        val preview = fixture.previewFor(tournament(pgaTournamentId = "espn-masters")).orFail()

        preview.espnId shouldBe "espn-masters"
    }

    test("previewForTournament falls back to the only event when its id is not the tournament's") {
        val onlyEvent = espnTournament(espnId = "espn-different")
        val fixture = PreviewFixture(tournamentsByDate = mapOf(START_DATE to listOf(onlyEvent)))

        val preview = fixture.previewFor(tournament(pgaTournamentId = "espn-masters")).orFail()

        preview.espnId shouldBe "espn-different"
    }

    test("previewForTournament answers NoMatchingEvent when no id matches and several events exist") {
        val events = listOf(espnTournament(espnId = "espn-a"), espnTournament(espnId = "espn-b"))
        val fixture = PreviewFixture(tournamentsByDate = mapOf(START_DATE to events))

        fixture.previewFor(tournament(pgaTournamentId = "espn-masters")) shouldBe
            Result.Err(LivePreviewError.NoMatchingEvent)
    }

    test("previewForTournament asks ESPN once, for the tournament's start date") {
        val fixture = PreviewFixture(tournamentsByDate = mapOf(START_DATE to listOf(espnTournament())))

        fixture.previewFor(tournament()).orFail()

        fixture.client.fetchCalls shouldContainExactly listOf(START_DATE)
    }

    test("previewForTournament projects per-team earnings + zero-sum weekly +/- across rostered golfers") {
        val scottie = golfer(SCOTTIE_ID, "Scottie", "Scheffler", pgaPlayerId = "p1")
        val rory = golfer(RORY_ID, "Rory", "McIlroy", pgaPlayerId = "p2")
        val event =
            espnTournament(
                competitors =
                    listOf(
                        espnCompetitor("p1", "Scottie Scheffler", 1, -10),
                        espnCompetitor("p2", "Rory McIlroy", 11, 5),
                    ),
            )
        val fixture =
            PreviewFixture(
                initialTournaments = listOf(tournament()),
                initialTeams = listOf(team(TEAM_A_ID, "BROWN"), team(TEAM_B_ID, "WOMBLE")),
                initialGolfers = listOf(scottie, rory),
                initialRosters =
                    listOf(
                        rosterEntry(TEAM_A_ID, SCOTTIE_ID, draftRound = 1),
                        rosterEntry(TEAM_B_ID, RORY_ID, draftRound = 1),
                    ),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val preview = fixture.previewFor(tournament()).orFail()

        preview.espnId shouldBe "401580999"
        preview.totalCompetitors shouldBe 2
        // Team A: rostered #1 ($18); Team B: rostered #11 (out of zone, $0).
        // Zero-sum across 2 teams: A weekly = 18*2-18 = +18; B weekly = 0*2-18 = -18.
        val a = preview.teams.single { it.teamId == TEAM_A_ID }
        val b = preview.teams.single { it.teamId == TEAM_B_ID }
        a.topTenEarnings.compareTo(BigDecimal(18)) shouldBe 0
        a.weeklyTotal.compareTo(BigDecimal(18)) shouldBe 0
        b.weeklyTotal.compareTo(BigDecimal(-18)) shouldBe 0
        a.weeklyTotal.add(b.weeklyTotal).compareTo(BigDecimal.ZERO) shouldBe 0
    }

    test("previewForTournament honors the matched DB tournament's payout multiplier") {
        val scottie = golfer(SCOTTIE_ID, "Scottie", "Scheffler", pgaPlayerId = "p1")
        val event = espnTournament(competitors = listOf(espnCompetitor("p1", "Scottie Scheffler", 1, -10)))
        val doubled = tournament(payoutMultiplier = BigDecimal(2))
        val fixture =
            PreviewFixture(
                initialTournaments = listOf(doubled),
                initialTeams = listOf(team(TEAM_A_ID, "BROWN")),
                initialGolfers = listOf(scottie),
                initialRosters = listOf(rosterEntry(TEAM_A_ID, SCOTTIE_ID, draftRound = 1)),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val preview = fixture.previewFor(doubled).orFail()

        preview.payoutMultiplier.compareTo(BigDecimal(2)) shouldBe 0
        // $18 first place × 2.0 multiplier = $36
        preview.teams.single().topTenEarnings.compareTo(BigDecimal(36)) shouldBe 0
    }

    test("previewForTournament falls through to multiplier=1 when no DB tournament is linked to the ESPN event") {
        val scottie = golfer(SCOTTIE_ID, "Scottie", "Scheffler", pgaPlayerId = "p1")
        val event = espnTournament(competitors = listOf(espnCompetitor("p1", "Scottie Scheffler", 1, -10)))
        val unlinked = tournament(pgaTournamentId = null, payoutMultiplier = BigDecimal(2))
        val fixture =
            PreviewFixture(
                initialTournaments = listOf(unlinked),
                initialTeams = listOf(team(TEAM_A_ID, "BROWN")),
                initialGolfers = listOf(scottie),
                initialRosters = listOf(rosterEntry(TEAM_A_ID, SCOTTIE_ID, draftRound = 1)),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val preview = fixture.previewFor(unlinked).orFail()

        // Pins a known defect, not the intended result (CWF-112): the multiplier is looked up by
        // event.espnId, which the lone-event fallback matched without, so the tournament's 2 is lost.
        preview.payoutMultiplier.compareTo(BigDecimal.ONE) shouldBe 0
    }

    test("previewForTournament returns the full leaderboard sorted by position") {
        val event =
            espnTournament(
                competitors =
                    listOf(
                        espnCompetitor("p3", "Third Place", 3, -5),
                        espnCompetitor("p1", "First Place", 1, -10),
                        espnCompetitor("p2", "Second Place", 2, -8),
                    ),
            )
        val fixture =
            PreviewFixture(
                initialTeams = listOf(team(TEAM_A_ID, "BROWN")),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val leaderboard = fixture.previewFor(tournament()).orFail().leaderboard

        leaderboard.map { it.name } shouldContainExactly
            listOf("First Place", "Second Place", "Third Place")
        leaderboard.forEach { it.rostered shouldBe false }
    }

    test("previewForTournament returns UpstreamUnavailable when ESPN is down") {
        val fixture = PreviewFixture(upstreamError = EspnUpstreamException(503, "Service Unavailable"))
        val espnDown = EspnError.UpstreamUnavailable(EspnUpstreamFailure.BadStatus(503))

        fixture.previewFor(tournament()) shouldBe Result.Err(LivePreviewError.EspnUnavailable(espnDown))
    }

    test("previewForTournament marks rostered competitors with team name on the leaderboard") {
        val scottie = golfer(SCOTTIE_ID, "Scottie", "Scheffler", pgaPlayerId = "p1")
        val event = espnTournament(competitors = listOf(espnCompetitor("p1", "Scottie Scheffler", 1, -10)))
        val fixture =
            PreviewFixture(
                initialTeams = listOf(team(TEAM_A_ID, "BROWN")),
                initialGolfers = listOf(scottie),
                initialRosters = listOf(rosterEntry(TEAM_A_ID, SCOTTIE_ID, draftRound = 1)),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val leaderboard = fixture.previewFor(tournament()).orFail().leaderboard

        leaderboard shouldHaveSize 1
        leaderboard.single().rostered shouldBe true
        leaderboard.single().teamNames shouldContainExactly listOf("BROWN")
    }

    test("previewForTournament names every team rostering a golfer split across two") {
        val scottie = golfer(SCOTTIE_ID, "Scottie", "Scheffler", pgaPlayerId = "p1")
        val event = espnTournament(competitors = listOf(espnCompetitor("p1", "Scottie Scheffler", 1, -10)))
        val fixture =
            PreviewFixture(
                initialTeams = listOf(team(TEAM_A_ID, "BROWN"), team(TEAM_B_ID, "WOMBLE")),
                initialGolfers = listOf(scottie),
                initialRosters =
                    listOf(
                        rosterEntry(TEAM_A_ID, SCOTTIE_ID, draftRound = 1),
                        rosterEntry(TEAM_B_ID, SCOTTIE_ID, draftRound = 1),
                    ),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val leaderboard = fixture.previewFor(tournament()).orFail().leaderboard

        leaderboard.map { it.teamNames } shouldContainExactly listOf(listOf("BROWN", "WOMBLE"))
    }
})
