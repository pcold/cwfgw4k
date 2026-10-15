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
import com.cwfgw.tournamentLinks.TournamentPlayerOverride
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
    initialOverrides: List<TournamentPlayerOverride> = emptyList(),
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
        val linkRepo = FakeTournamentLinkRepository(initial = initialOverrides)
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

// The context loads link overrides only for the tournaments handed to it; it never lists the repository's.
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

        preview.espnName shouldBe "The Masters"
    }

    test("previewForTournament answers NoMatchingEvent for a stale id though the date has a lone event") {
        val onlyEvent = espnTournament(espnId = "espn-different")
        val fixture = PreviewFixture(tournamentsByDate = mapOf(START_DATE to listOf(onlyEvent)))

        fixture.previewFor(tournament(pgaTournamentId = "espn-masters")) shouldBe
            Result.Err(LivePreviewError.NoMatchingEvent)
    }

    test("previewForTournament answers NoMatchingEvent for an unlinked tournament though the date has a lone event") {
        val fixture = PreviewFixture(tournamentsByDate = mapOf(START_DATE to listOf(espnTournament())))

        fixture.previewFor(tournament(pgaTournamentId = null)) shouldBe Result.Err(LivePreviewError.NoMatchingEvent)
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

    test("previewForTournament projects per-team earnings across rostered golfers") {
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

        // Team A: rostered #1 ($18); Team B: rostered #11 (out of zone, $0).
        val a = preview.teams.single { it.teamId == TEAM_A_ID }
        val b = preview.teams.single { it.teamId == TEAM_B_ID }
        a.topTenEarnings.compareTo(BigDecimal(18)) shouldBe 0
        b.topTenEarnings.compareTo(BigDecimal.ZERO) shouldBe 0
        b.golferScores shouldBe emptyList()
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

    test("previewForTournament previews a lone event only for the row linked to it, never a stale-id sibling") {
        val scottie = golfer(SCOTTIE_ID, "Scottie", "Scheffler", pgaPlayerId = "p1")
        val event = espnTournament(competitors = listOf(espnCompetitor("p1", "Scottie Scheffler", 1, -10)))
        val stale = tournament(pgaTournamentId = "espn-stale", payoutMultiplier = BigDecimal(3))
        val linkedToEvent =
            tournament(payoutMultiplier = BigDecimal(2))
                .copy(id = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02")))
        val fixture =
            PreviewFixture(
                initialTournaments = listOf(stale, linkedToEvent),
                initialTeams = listOf(team(TEAM_A_ID, "BROWN")),
                initialGolfers = listOf(scottie),
                initialRosters = listOf(rosterEntry(TEAM_A_ID, SCOTTIE_ID, draftRound = 1)),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )
        val ctx = fixture.service.loadLivePreviewContext(SEASON_ID, fixture.initialTournaments)

        fixture.service.previewForTournament(ctx, stale) shouldBe Result.Err(LivePreviewError.NoMatchingEvent)
        val preview = fixture.service.previewForTournament(ctx, linkedToEvent).orFail()
        preview.payoutMultiplier.compareTo(BigDecimal(2)) shouldBe 0
    }

    test("previewForTournament pays by ESPN's team-event flag where the tournament row disagrees") {
        val scottie = golfer(SCOTTIE_ID, "Scottie", "Scheffler", pgaPlayerId = "p1")
        val event = espnTournament(competitors = listOf(espnCompetitor("p1", "Scottie Scheffler", 1, -10)))
        val rowSaysTeamEvent = tournament(isTeamEvent = true)
        val fixture =
            PreviewFixture(
                initialTournaments = listOf(rowSaysTeamEvent),
                initialTeams = listOf(team(TEAM_A_ID, "BROWN")),
                initialGolfers = listOf(scottie),
                initialRosters = listOf(rosterEntry(TEAM_A_ID, SCOTTIE_ID, draftRound = 1)),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val preview = fixture.previewFor(rowSaysTeamEvent).orFail()

        preview.isTeamEvent shouldBe false
        // $18 first place, unhalved: finalize's import writes ESPN's flag over the row before it scores
        preview.teams.single().topTenEarnings.compareTo(BigDecimal(18)) shouldBe 0
    }

    test("previewForTournament applies the tournament's own link overrides") {
        val scottie = golfer(SCOTTIE_ID, "Scottie", "Scheffler", pgaPlayerId = "p1")
        val event = espnTournament(competitors = listOf(espnCompetitor("espn-x", "S. Scheffler-Smith", 1, -10)))
        val linked = tournament()
        val fixture =
            PreviewFixture(
                initialTournaments = listOf(linked),
                initialTeams = listOf(team(TEAM_A_ID, "BROWN")),
                initialGolfers = listOf(scottie),
                initialRosters = listOf(rosterEntry(TEAM_A_ID, SCOTTIE_ID, draftRound = 1)),
                initialOverrides =
                    listOf(
                        TournamentPlayerOverride(
                            tournamentId = TOURNAMENT_ID,
                            espnCompetitorId = "espn-x",
                            golferId = SCOTTIE_ID,
                        ),
                    ),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val preview = fixture.previewFor(linked).orFail()

        preview.teams.single().topTenEarnings.compareTo(BigDecimal(18)) shouldBe 0
        preview.leaderboard.single().teamNames shouldContainExactly listOf("BROWN")
    }

    test("previewForTournament resolves a competitor through its own candidate's override, not a sibling's") {
        val masters = tournament(pgaTournamentId = "espn-masters")
        val heritageStart = START_DATE.plusWeeks(1)
        val heritage =
            tournament(pgaTournamentId = "espn-heritage").copy(
                id = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02")),
                startDate = heritageStart,
                endDate = heritageStart.plusDays(3),
            )
        val shared = listOf(espnCompetitor("espn-x", "Mystery Entrant", 1, -10))
        val fixture =
            PreviewFixture(
                initialTournaments = listOf(masters, heritage),
                initialTeams = listOf(team(TEAM_A_ID, "BROWN"), team(TEAM_B_ID, "WOMBLE")),
                initialGolfers = listOf(golfer(SCOTTIE_ID, "Scottie", "Scheffler"), golfer(RORY_ID, "Rory", "McIlroy")),
                initialRosters =
                    listOf(
                        rosterEntry(TEAM_A_ID, SCOTTIE_ID, draftRound = 1),
                        rosterEntry(TEAM_B_ID, RORY_ID, draftRound = 1),
                    ),
                initialOverrides =
                    listOf(
                        TournamentPlayerOverride(masters.id, espnCompetitorId = "espn-x", golferId = SCOTTIE_ID),
                        TournamentPlayerOverride(heritage.id, espnCompetitorId = "espn-x", golferId = RORY_ID),
                    ),
                tournamentsByDate =
                    mapOf(
                        START_DATE to listOf(espnTournament(espnId = "espn-masters", competitors = shared)),
                        heritageStart to listOf(espnTournament(espnId = "espn-heritage", competitors = shared)),
                    ),
            )
        val ctx = fixture.service.loadLivePreviewContext(SEASON_ID, fixture.initialTournaments)

        val mastersBoard = fixture.service.previewForTournament(ctx, masters).orFail().leaderboard
        val heritageBoard = fixture.service.previewForTournament(ctx, heritage).orFail().leaderboard

        mastersBoard.single().teamNames shouldContainExactly listOf("BROWN")
        heritageBoard.single().teamNames shouldContainExactly listOf("WOMBLE")
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
