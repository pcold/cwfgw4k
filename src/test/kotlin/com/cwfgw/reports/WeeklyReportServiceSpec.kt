package com.cwfgw.reports

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.FantasyScoreId
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.RosterEntryId
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamService
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentResultId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.inspectors.forAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val LEAGUE_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000111"))
private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val MASTERS_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))
private val SONY_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b02"))
private val TEAM_A = teamFixture("c01", "BROWN")
private val TEAM_B = teamFixture("c02", "WOMBLE")

private fun teamFixture(
    idHex: String,
    name: String,
): Team =
    Team(
        id = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000$idHex")),
        seasonId = SEASON_ID,
        ownerName = "Owner $name",
        teamName = name,
        teamNumber = null,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

private fun golferFixture(
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

@Suppress("LongParameterList")
private fun tournamentFixture(
    id: TournamentId,
    name: String,
    startDate: String,
    status: TournamentStatus = TournamentStatus.Completed,
    week: String? = null,
    isTeamEvent: Boolean = false,
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
        payoutMultiplier = BigDecimal("1.0000"),
        week = week,
        isTeamEvent = isTeamEvent,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
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
        acquiredAt = Instant.parse("2026-01-01T00:00:00Z"),
        droppedAt = null,
        isActive = true,
    )

private fun resultFixture(
    tournamentId: TournamentId,
    golferId: GolferId,
    position: Int?,
    scoreToPar: Int? = -5,
): TournamentResult =
    TournamentResult(
        id = TournamentResultId(UUID.randomUUID()),
        tournamentId = tournamentId,
        golferId = golferId,
        position = position,
        scoreToPar = scoreToPar,
        totalStrokes = 270,
        earnings = null,
        round1 = null,
        round2 = null,
        round3 = null,
        round4 = null,
        madeCut = position != null,
        pairKey = null,
    )

@Suppress("LongParameterList")
private fun scoreFixture(
    teamId: TeamId,
    tournamentId: TournamentId,
    golferId: GolferId,
    points: BigDecimal,
    position: Int,
    numTied: Int = 1,
): FantasyScore =
    FantasyScore(
        id = FantasyScoreId(UUID.randomUUID()),
        seasonId = SEASON_ID,
        teamId = teamId,
        tournamentId = tournamentId,
        golferId = golferId,
        points = points,
        position = position,
        numTied = numTied,
        basePayout = points,
        ownershipPct = BigDecimal(100),
        payout = points,
        multiplier = BigDecimal.ONE,
        calculatedAt = Instant.parse("2026-04-12T20:00:00Z"),
    )

@Suppress("LongParameterList")
private class Fixture(
    seedSeason: Boolean = true,
    initialTournaments: List<Tournament> = emptyList(),
    initialTeams: List<Team> = emptyList(),
    initialGolfers: List<Golfer> = emptyList(),
    initialRosters: List<RosterEntry> = emptyList(),
    initialResults: List<TournamentResult> = emptyList(),
    initialScores: List<FantasyScore> = emptyList(),
) {
    val seasonRepo: FakeSeasonRepository = FakeSeasonRepository(idFactory = { SEASON_ID })
    val tournamentRepo = FakeTournamentRepository(initial = initialTournaments)
    val teamRepo = FakeTeamRepository(initialTeams = initialTeams, initialRoster = initialRosters)
    val golferRepo = FakeGolferRepository(initial = initialGolfers)
    val scoringRepo = FakeScoringRepository(initialScores = initialScores)
    val service: WeeklyReportService

    init {
        if (seedSeason) {
            kotlinx.coroutines.runBlocking {
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = LEAGUE_ID, name = "2026 Season", seasonYear = 2026),
                )
            }
        }
        initialResults.forEach { result ->
            kotlinx.coroutines.runBlocking { tournamentRepo.upsertResult(result.tournamentId, asUpsertRequest(result)) }
        }
        val seasonService = SeasonService(seasonRepo)
        val tournamentService = TournamentService(tournamentRepo)
        val teamService = TeamService(teamRepo)
        val golferService = GolferService(golferRepo)
        val scoringService =
            ScoringService(
                repository = scoringRepo,
                seasonService = seasonService,
                tournamentService = tournamentService,
                teamService = teamService,
            )
        service =
            WeeklyReportService(
                seasonService = seasonService,
                tournamentService = tournamentService,
                teamService = teamService,
                golferService = golferService,
                scoringService = scoringService,
            )
    }
}

private fun asUpsertRequest(r: TournamentResult): com.cwfgw.tournaments.CreateTournamentResultRequest =
    com.cwfgw.tournaments.CreateTournamentResultRequest(
        golferId = r.golferId,
        position = r.position,
        scoreToPar = r.scoreToPar,
        totalStrokes = r.totalStrokes,
        earnings = r.earnings,
        round1 = r.round1,
        round2 = r.round2,
        round3 = r.round3,
        round4 = r.round4,
        madeCut = r.madeCut,
        pairKey = r.pairKey,
    )

class WeeklyReportServiceSpec : FunSpec({

    test("getReport returns SeasonNotFound when the season id doesn't exist") {
        val fixture = Fixture(seedSeason = false)
        fixture.service.getReport(SEASON_ID, MASTERS_ID) shouldBe
            Result.Err(ReportError.SeasonNotFound(SEASON_ID))
    }

    test("getReport returns TournamentNotFound when the tournament id doesn't exist") {
        val fixture = Fixture()
        fixture.service.getReport(SEASON_ID, MASTERS_ID) shouldBe
            Result.Err(ReportError.TournamentNotFound(MASTERS_ID))
    }

    test("getReport with no rosters or scores produces 8 empty cells per team and zero totals") {
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
            )

        val report =
            fixture.service.getReport(SEASON_ID, MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value

        report.tournament.name shouldBe "The Masters"
        report.teams shouldHaveSize 2
        report.teams.forAll { col ->
            col.cells shouldHaveSize 8
            col.cells.forAll { cell ->
                cell.golferName shouldBe null
                cell.golferId shouldBe null
                cell.earnings.compareTo(BigDecimal.ZERO) shouldBe 0
            }
            col.weeklyTotal.compareTo(BigDecimal.ZERO) shouldBe 0
            col.totalCash.compareTo(BigDecimal.ZERO) shouldBe 0
        }
    }

    test("getReport surfaces single-tournament earnings and weekly +/- sums to zero across teams") {
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val winner = golferFixture("d01", "Scottie", "Scheffler")
        val miss = golferFixture("d02", "Rando", "Player")
        // Team A drafted the winner in round 1, Team B drafted a non-cash finisher.
        val rosters =
            listOf(
                rosterEntry(TEAM_A.id, winner.id, draftRound = 1),
                rosterEntry(TEAM_B.id, miss.id, draftRound = 1),
            )
        val results =
            listOf(
                resultFixture(MASTERS_ID, winner.id, position = 1),
                resultFixture(MASTERS_ID, miss.id, position = 11, scoreToPar = 5),
            )
        val winnerScore = scoreFixture(TEAM_A.id, MASTERS_ID, winner.id, points = BigDecimal(18), position = 1)
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
                initialGolfers = listOf(winner, miss),
                initialRosters = rosters,
                initialResults = results,
                initialScores = listOf(winnerScore),
            )

        val report =
            fixture.service.getReport(SEASON_ID, MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value
        val a = report.teams.single { it.teamId == TEAM_A.id }
        val b = report.teams.single { it.teamId == TEAM_B.id }

        a.topTenEarnings.compareTo(BigDecimal(18)) shouldBe 0
        a.weeklyTotal.compareTo(BigDecimal(18)) shouldBe 0
        b.weeklyTotal.compareTo(BigDecimal(-18)) shouldBe 0
        a.weeklyTotal.add(b.weeklyTotal).compareTo(BigDecimal.ZERO) shouldBe 0
    }

    test("getReport standings rank teams by totalCash descending with sequential 1..N ranks") {
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val winner = golferFixture("d01", "Scottie", "Scheffler")
        val miss = golferFixture("d02", "Rando", "Player")
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
                initialGolfers = listOf(winner, miss),
                initialRosters =
                    listOf(
                        rosterEntry(TEAM_A.id, winner.id, draftRound = 1),
                        rosterEntry(TEAM_B.id, miss.id, draftRound = 1),
                    ),
                initialResults =
                    listOf(
                        resultFixture(MASTERS_ID, winner.id, position = 1),
                        resultFixture(MASTERS_ID, miss.id, position = 11, scoreToPar = 5),
                    ),
                initialScores =
                    listOf(scoreFixture(TEAM_A.id, MASTERS_ID, winner.id, BigDecimal(18), position = 1)),
            )

        val standings =
            fixture.service.getReport(SEASON_ID, MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value
                .standingsOrder

        standings.map { it.rank } shouldContainExactly listOf(1, 2)
        standings.first().teamName shouldBe TEAM_A.teamName
    }

    test("getReport surfaces top-10 finishers that no team rostered in undraftedTopTens") {
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val rostered = golferFixture("d01", "Scottie", "Scheffler")
        val undrafted = golferFixture("d02", "Rory", "McIlroy")
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
                initialGolfers = listOf(rostered, undrafted),
                initialRosters = listOf(rosterEntry(TEAM_A.id, rostered.id, draftRound = 1)),
                initialResults =
                    listOf(
                        resultFixture(MASTERS_ID, rostered.id, position = 1),
                        resultFixture(MASTERS_ID, undrafted.id, position = 5),
                    ),
            )

        val undraftedList =
            fixture.service.getReport(SEASON_ID, MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value
                .undraftedTopTens

        undraftedList shouldHaveSize 1
        undraftedList.single().name shouldBe "R. McIlroy"
        undraftedList.single().position shouldBe 5
    }

    test("getReport renders a tied position with a 'T' prefix on every cell that shares it") {
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val a = golferFixture("d01", "Scottie", "Scheffler")
        val b = golferFixture("d02", "Rory", "McIlroy")
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
                initialGolfers = listOf(a, b),
                initialRosters =
                    listOf(
                        rosterEntry(TEAM_A.id, a.id, draftRound = 1),
                        rosterEntry(TEAM_B.id, b.id, draftRound = 1),
                    ),
                initialResults =
                    listOf(
                        resultFixture(MASTERS_ID, a.id, position = 1),
                        resultFixture(MASTERS_ID, b.id, position = 1),
                    ),
            )

        val report =
            fixture.service.getReport(SEASON_ID, MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value
        val teamA = report.teams.single { it.teamId == TEAM_A.id }
        val teamB = report.teams.single { it.teamId == TEAM_B.id }
        teamA.cells.first { it.round == 1 }.positionStr shouldBe "T1"
        teamB.cells.first { it.round == 1 }.positionStr shouldBe "T1"
    }

    test("getReport sideBetDetail emits one row per configured side-bet round (default 5,6,7,8)") {
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
            )

        val report =
            fixture.service.getReport(SEASON_ID, MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value

        report.sideBetDetail.map { it.round } shouldContainExactly listOf(5, 6, 7, 8)
        report.sideBetDetail.forAll { round ->
            round.teams shouldHaveSize 2
            round.teams.forAll { entry -> entry.payout.compareTo(BigDecimal.ZERO) shouldBe 0 }
        }
    }

    test("getReport's `previous` field accumulates weekly +/- from prior completed tournaments") {
        val sony = tournamentFixture(SONY_ID, "Sony Open", "2026-01-15")
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val winner = golferFixture("d01", "Scottie", "Scheffler")
        val miss = golferFixture("d02", "Rando", "Player")
        val rosters =
            listOf(
                rosterEntry(TEAM_A.id, winner.id, draftRound = 1),
                rosterEntry(TEAM_B.id, miss.id, draftRound = 1),
            )
        // Prior tournament (Sony): Team A's golfer wins.
        val priorWinnerScore = scoreFixture(TEAM_A.id, SONY_ID, winner.id, BigDecimal(18), position = 1)
        val fixture =
            Fixture(
                initialTournaments = listOf(sony, masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
                initialGolfers = listOf(winner, miss),
                initialRosters = rosters,
                initialResults =
                    listOf(
                        resultFixture(SONY_ID, winner.id, position = 1),
                        resultFixture(MASTERS_ID, miss.id, position = 11, scoreToPar = 5),
                    ),
                initialScores = listOf(priorWinnerScore),
            )

        val report =
            fixture.service.getReport(SEASON_ID, MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value
        val a = report.teams.single { it.teamId == TEAM_A.id }
        val b = report.teams.single { it.teamId == TEAM_B.id }

        // Sony's zero-sum split: A earned $18, total pot $18, N=2 → A's prior = $18×2 - $18 = $18; B's = -$18.
        a.previous.compareTo(BigDecimal(18)) shouldBe 0
        b.previous.compareTo(BigDecimal(-18)) shouldBe 0
        // Sanity: Masters has no rostered scoring so weekly is 0; subtotal = previous.
        a.weeklyTotal.compareTo(BigDecimal.ZERO) shouldBe 0
        a.subtotal.compareTo(BigDecimal(18)) shouldBe 0
    }

    test("getReport renders the picked golfer's last name in upper case") {
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val scottie = golferFixture("d01", "Scottie", "Scheffler")
        val fixture =
            Fixture(
                initialTournaments = listOf(masters),
                initialTeams = listOf(TEAM_A),
                initialGolfers = listOf(scottie),
                initialRosters = listOf(rosterEntry(TEAM_A.id, scottie.id, draftRound = 1)),
            )

        val cell =
            fixture.service.getReport(SEASON_ID, MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value
                .teams.single().cells.first { it.round == 1 }
        cell.golferName shouldBe "SCHEFFLER"
    }

    test("getReport with an empty teams list returns an empty grid plus zero-payout side-bet detail rows") {
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val fixture = Fixture(initialTournaments = listOf(masters))

        val report =
            fixture.service.getReport(SEASON_ID, MASTERS_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value

        report.teams.shouldBeEmpty()
        report.standingsOrder.shouldBeEmpty()
        report.undraftedTopTens.shouldBeEmpty()
        report.sideBetDetail.map { it.round } shouldContainExactly listOf(5, 6, 7, 8)
        report.sideBetDetail.forAll { it.teams.shouldBeEmpty() }
    }

    // ========== getSeasonReport ==========

    test("getSeasonReport returns SeasonNotFound when the season id doesn't exist") {
        val fixture = Fixture(seedSeason = false)
        fixture.service.getSeasonReport(SEASON_ID) shouldBe Result.Err(ReportError.SeasonNotFound(SEASON_ID))
    }

    test("getSeasonReport with no completed tournaments produces empty cells and zero totals") {
        val fixture = Fixture(initialTeams = listOf(TEAM_A, TEAM_B))

        val report =
            fixture.service.getSeasonReport(SEASON_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value

        report.tournament.id shouldBe null
        report.tournament.name shouldBe "All Tournaments"
        report.teams.forAll { col ->
            col.weeklyTotal.compareTo(BigDecimal.ZERO) shouldBe 0
            col.subtotal.compareTo(BigDecimal.ZERO) shouldBe 0
        }
    }

    test("getSeasonReport rolls earnings up across multiple tournaments per team") {
        val sony = tournamentFixture(SONY_ID, "Sony Open", "2026-01-15")
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val winner = golferFixture("d01", "Scottie", "Scheffler")
        val miss = golferFixture("d02", "Rando", "Player")
        val rosters =
            listOf(
                rosterEntry(TEAM_A.id, winner.id, draftRound = 1),
                rosterEntry(TEAM_B.id, miss.id, draftRound = 1),
            )
        val results =
            listOf(
                resultFixture(SONY_ID, winner.id, position = 1),
                resultFixture(MASTERS_ID, winner.id, position = 1),
                resultFixture(MASTERS_ID, miss.id, position = 11, scoreToPar = 5),
            )
        val scores =
            listOf(
                scoreFixture(TEAM_A.id, SONY_ID, winner.id, BigDecimal(18), position = 1),
                scoreFixture(TEAM_A.id, MASTERS_ID, winner.id, BigDecimal(18), position = 1),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(sony, masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
                initialGolfers = listOf(winner, miss),
                initialRosters = rosters,
                initialResults = results,
                initialScores = scores,
            )

        val report =
            fixture.service.getSeasonReport(SEASON_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value
        val a = report.teams.single { it.teamId == TEAM_A.id }
        val b = report.teams.single { it.teamId == TEAM_B.id }

        // A earned $18 in Sony + $18 in Masters = $36; B never earned.
        // Per-tournament weekly: A = +$18, B = -$18; over 2 tournaments: A = +$36, B = -$36.
        a.topTenEarnings.compareTo(BigDecimal(36)) shouldBe 0
        a.weeklyTotal.compareTo(BigDecimal(36)) shouldBe 0
        b.weeklyTotal.compareTo(BigDecimal(-36)) shouldBe 0
        a.topTenCount shouldBe 2
        a.cells.first { it.round == 1 }.positionStr shouldBe "2x"
    }

    test("getSeasonReport's undraftedTopTens aggregates a golfer's payouts across tournaments") {
        val sony = tournamentFixture(SONY_ID, "Sony Open", "2026-01-15")
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val undrafted = golferFixture("d02", "Rory", "McIlroy")
        val fixture =
            Fixture(
                initialTournaments = listOf(sony, masters),
                initialTeams = listOf(TEAM_A),
                initialGolfers = listOf(undrafted),
                initialResults =
                    listOf(
                        resultFixture(SONY_ID, undrafted.id, position = 1),
                        resultFixture(MASTERS_ID, undrafted.id, position = 5),
                    ),
            )

        val undraftedList =
            fixture.service.getSeasonReport(SEASON_ID)
                .shouldBeInstanceOf<Result.Ok<WeeklyReport>>()
                .value
                .undraftedTopTens

        undraftedList shouldHaveSize 1
        undraftedList.single().name shouldBe "R. McIlroy"
        // Sony pos 1 = $18, Masters pos 5 = $7 → $25 total
        undraftedList.single().payout.compareTo(BigDecimal(25)) shouldBe 0
    }

    // ========== getRankings ==========

    test("getRankings returns SeasonNotFound when the season id doesn't exist") {
        val fixture = Fixture(seedSeason = false)
        fixture.service.getRankings(SEASON_ID) shouldBe Result.Err(ReportError.SeasonNotFound(SEASON_ID))
    }

    test("getRankings returns TournamentNotFound when the cutoff tournament id doesn't exist") {
        val fixture = Fixture()
        val ghost = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000fff"))
        fixture.service.getRankings(SEASON_ID, throughTournamentId = ghost) shouldBe
            Result.Err(ReportError.TournamentNotFound(ghost))
    }

    test("getRankings produces one series entry per included tournament with cumulative totals") {
        val sony = tournamentFixture(SONY_ID, "Sony Open", "2026-01-15", week = "2")
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09", week = "15")
        val winner = golferFixture("d01", "Scottie", "Scheffler")
        val rosters = listOf(rosterEntry(TEAM_A.id, winner.id, draftRound = 1))
        val scores =
            listOf(
                scoreFixture(TEAM_A.id, SONY_ID, winner.id, BigDecimal(18), position = 1),
                scoreFixture(TEAM_A.id, MASTERS_ID, winner.id, BigDecimal(18), position = 1),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(sony, masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
                initialGolfers = listOf(winner),
                initialRosters = rosters,
                initialScores = scores,
            )

        val rankings =
            fixture.service.getRankings(SEASON_ID)
                .shouldBeInstanceOf<Result.Ok<Rankings>>()
                .value

        rankings.weeks shouldContainExactly listOf("2", "15")
        rankings.tournamentNames shouldContainExactly listOf("Sony Open", "The Masters")
        rankings.teams.first().teamName shouldBe TEAM_A.teamName
        // Series after Sony = +$18, after Masters = +$36.
        // Team A is the leader; +$18 each tournament (zero-sum vs. the one other team).
        val a = rankings.teams.single { it.teamId == TEAM_A.id }
        a.subtotal.compareTo(BigDecimal(36)) shouldBe 0
        a.series.map { it.compareTo(BigDecimal(0)) } shouldContainExactly listOf(1, 1)
        a.series[0].compareTo(BigDecimal(18)) shouldBe 0
        a.series[1].compareTo(BigDecimal(36)) shouldBe 0
    }

    test("getRankings respects the through cutoff and excludes later tournaments") {
        val sony = tournamentFixture(SONY_ID, "Sony Open", "2026-01-15")
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val winner = golferFixture("d01", "Scottie", "Scheffler")
        val fixture =
            Fixture(
                initialTournaments = listOf(sony, masters),
                initialTeams = listOf(TEAM_A, TEAM_B),
                initialGolfers = listOf(winner),
                initialRosters = listOf(rosterEntry(TEAM_A.id, winner.id, draftRound = 1)),
                initialScores =
                    listOf(
                        scoreFixture(TEAM_A.id, SONY_ID, winner.id, BigDecimal(18), position = 1),
                        scoreFixture(TEAM_A.id, MASTERS_ID, winner.id, BigDecimal(18), position = 1),
                    ),
            )

        val rankings =
            fixture.service.getRankings(SEASON_ID, throughTournamentId = SONY_ID)
                .shouldBeInstanceOf<Result.Ok<Rankings>>()
                .value

        rankings.tournamentNames shouldContainExactly listOf("Sony Open")
        rankings.teams.single { it.teamId == TEAM_A.id }.subtotal.compareTo(BigDecimal(18)) shouldBe 0
    }

    // ========== getGolferHistory ==========

    test("getGolferHistory returns SeasonNotFound when the season id doesn't exist") {
        val fixture = Fixture(seedSeason = false)
        val golferId = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d01"))
        fixture.service.getGolferHistory(SEASON_ID, golferId) shouldBe
            Result.Err(ReportError.SeasonNotFound(SEASON_ID))
    }

    test("getGolferHistory returns GolferNotFound when the golfer id doesn't exist") {
        val ghost = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000999"))
        val fixture = Fixture()
        fixture.service.getGolferHistory(SEASON_ID, ghost) shouldBe Result.Err(ReportError.GolferNotFound(ghost))
    }

    test("getGolferHistory returns an empty results list and zero totals for a golfer with no top-10 finishes") {
        val sony = tournamentFixture(SONY_ID, "Sony Open", "2026-01-15")
        val golfer = golferFixture("d01", "Scottie", "Scheffler")
        val fixture =
            Fixture(
                initialTournaments = listOf(sony),
                initialGolfers = listOf(golfer),
                initialResults = listOf(resultFixture(SONY_ID, golfer.id, position = 25, scoreToPar = 5)),
            )

        val history =
            fixture.service.getGolferHistory(SEASON_ID, golfer.id)
                .shouldBeInstanceOf<Result.Ok<GolferHistory>>()
                .value

        history.golferName shouldBe "Scottie Scheffler"
        history.results.shouldBeEmpty()
        history.topTens shouldBe 0
        history.totalEarnings.compareTo(BigDecimal.ZERO) shouldBe 0
    }

    test("getGolferHistory lists each top-10 finish with its position and tieSplitPayout") {
        val sony = tournamentFixture(SONY_ID, "Sony Open", "2026-01-15")
        val masters = tournamentFixture(MASTERS_ID, "The Masters", "2026-04-09")
        val golfer = golferFixture("d01", "Scottie", "Scheffler")
        val fixture =
            Fixture(
                initialTournaments = listOf(sony, masters),
                initialGolfers = listOf(golfer),
                initialResults =
                    listOf(
                        resultFixture(SONY_ID, golfer.id, position = 1),
                        resultFixture(MASTERS_ID, golfer.id, position = 5),
                    ),
            )

        val history =
            fixture.service.getGolferHistory(SEASON_ID, golfer.id)
                .shouldBeInstanceOf<Result.Ok<GolferHistory>>()
                .value

        history.topTens shouldBe 2
        history.results.map { it.tournament } shouldContainExactly listOf("Sony Open", "The Masters")
        history.results.map { it.position } shouldContainExactly listOf(1, 5)
        // Sony pos 1 = $18, Masters pos 5 = $7 → totals = $25
        history.totalEarnings.compareTo(BigDecimal(25)) shouldBe 0
    }
})
