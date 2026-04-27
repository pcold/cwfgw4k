package com.cwfgw.reports

import com.cwfgw.espn.EspnLivePreview
import com.cwfgw.espn.PreviewGolferScore
import com.cwfgw.espn.PreviewLeaderboardEntry
import com.cwfgw.espn.PreviewTeamScore
import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val TOURNAMENT_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000b01"))
private val TEAM_A_ID = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c01"))
private val TEAM_B_ID = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c02"))
private val SCOTTIE_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d01"))
private val RORY_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d02"))

private val DEFAULT_RULES =
    SeasonRules(
        payouts = SeasonRules.DEFAULT_PAYOUTS,
        tieFloor = SeasonRules.DEFAULT_TIE_FLOOR,
        sideBetRounds = SeasonRules.DEFAULT_SIDE_BET_ROUNDS,
        sideBetAmount = SeasonRules.DEFAULT_SIDE_BET_AMOUNT,
    )

private fun tournament(
    pgaTournamentId: String? = "espn-masters",
    name: String = "The Masters",
): Tournament =
    Tournament(
        id = TOURNAMENT_ID,
        pgaTournamentId = pgaTournamentId,
        name = name,
        seasonId = SEASON_ID,
        startDate = LocalDate.parse("2026-04-09"),
        endDate = LocalDate.parse("2026-04-12"),
        courseName = null,
        status = TournamentStatus.InProgress,
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

private fun preview(
    espnId: String = "espn-masters",
    teams: List<PreviewTeamScore> = emptyList(),
    leaderboard: List<PreviewLeaderboardEntry> = emptyList(),
    multiplier: BigDecimal = BigDecimal.ONE,
    isTeamEvent: Boolean = false,
): EspnLivePreview =
    EspnLivePreview(
        espnName = "The Masters",
        espnId = espnId,
        completed = false,
        payoutMultiplier = multiplier,
        totalCompetitors = teams.sumOf { it.golferScores.size },
        teams = teams,
        leaderboard = leaderboard,
        isTeamEvent = isTeamEvent,
    )

private fun previewTeam(
    teamId: TeamId,
    teamName: String = "TEAM",
    golfers: List<PreviewGolferScore> = emptyList(),
): PreviewTeamScore =
    PreviewTeamScore(
        teamId = teamId,
        teamName = teamName,
        ownerName = "Owner $teamName",
        topTenEarnings = golfers.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.payout) },
        golferScores = golfers,
    )

private fun previewGolfer(
    golferId: GolferId,
    position: Int,
    payout: BigDecimal,
    scoreToPar: Int? = -10,
): PreviewGolferScore =
    PreviewGolferScore(
        golferName = "Test Golfer",
        golferId = golferId,
        position = position,
        numTied = 1,
        scoreToPar = scoreToPar,
        basePayout = payout,
        ownershipPct = BigDecimal(100),
        payout = payout,
    )

private fun leaderboardEntry(
    name: String,
    position: Int,
    rostered: Boolean = false,
    teamName: String? = null,
    scoreToPar: Int? = -10,
): PreviewLeaderboardEntry =
    PreviewLeaderboardEntry(
        name = name,
        position = position,
        scoreToPar = scoreToPar,
        thru = null,
        rostered = rostered,
        teamName = teamName,
        pairKey = null,
    )

private fun cell(
    round: Int,
    golferId: GolferId? = null,
    earnings: BigDecimal = BigDecimal.ZERO,
    seasonEarnings: BigDecimal = BigDecimal.ZERO,
    seasonTopTens: Int = 0,
): ReportCell =
    ReportCell(
        round = round,
        golferName = "GOLFER",
        golferId = golferId,
        positionStr = null,
        scoreToPar = null,
        earnings = earnings,
        topTens = 0,
        ownershipPct = BigDecimal(100),
        seasonEarnings = seasonEarnings,
        seasonTopTens = seasonTopTens,
    )

private fun teamColumn(
    teamId: TeamId,
    teamName: String = "TEAM",
    cells: List<ReportCell> = emptyList(),
    previous: BigDecimal = BigDecimal.ZERO,
    sideBets: BigDecimal = BigDecimal.ZERO,
): ReportTeamColumn =
    ReportTeamColumn(
        teamId = teamId,
        teamName = teamName,
        ownerName = "Owner $teamName",
        cells = cells,
        topTenEarnings = BigDecimal.ZERO,
        weeklyTotal = BigDecimal.ZERO,
        previous = previous,
        subtotal = previous,
        topTenCount = 0,
        topTenMoney = BigDecimal.ZERO,
        sideBets = sideBets,
        totalCash = previous.add(sideBets),
    )

private fun emptyReport(teams: List<ReportTeamColumn> = emptyList()): WeeklyReport =
    WeeklyReport(
        tournament =
            ReportTournamentInfo(
                id = TOURNAMENT_ID,
                name = "The Masters",
                startDate = "2026-04-09",
                endDate = "2026-04-12",
                status = TournamentStatus.InProgress,
                payoutMultiplier = BigDecimal.ONE,
                week = null,
            ),
        teams = teams,
        undraftedTopTens = emptyList(),
        sideBetDetail = emptyList(),
        standingsOrder = emptyList(),
    )

class LiveOverlayServiceSpec : FunSpec({

    // ----- matchPreview -----

    test("matchPreview resolves by exact pga_tournament_id when one matches") {
        val masters = preview(espnId = "espn-masters")
        val sony = preview(espnId = "espn-sony")
        matchPreview(listOf(sony, masters), tournament(pgaTournamentId = "espn-masters")) shouldBe masters
    }

    test("matchPreview falls through to the only preview when no id match and exactly one is present") {
        val onlyEvent = preview(espnId = "espn-different")
        matchPreview(listOf(onlyEvent), tournament(pgaTournamentId = "espn-masters")) shouldBe onlyEvent
    }

    test("matchPreview returns null when no id match and multiple previews exist") {
        val a = preview(espnId = "espn-a")
        val b = preview(espnId = "espn-b")
        matchPreview(listOf(a, b), tournament(pgaTournamentId = "espn-masters")) shouldBe null
    }

    test("matchPreview returns null on an empty preview list") {
        matchPreview(emptyList(), tournament()) shouldBe null
    }

    // ----- mergeLiveData -----

    test("mergeLiveData returns the input report unchanged when previews list is empty") {
        val base = emptyReport(listOf(teamColumn(TEAM_A_ID)))
        mergeLiveData(base, emptyList(), DEFAULT_RULES) shouldBe base
    }

    test("mergeLiveData replaces per-cell earnings with live projections in non-additive mode") {
        val base =
            emptyReport(
                listOf(
                    teamColumn(
                        TEAM_A_ID,
                        cells = listOf(cell(round = 1, golferId = SCOTTIE_ID, earnings = BigDecimal(5))),
                    ),
                ),
            )
        val live =
            preview(
                teams =
                    listOf(
                        previewTeam(
                            teamId = TEAM_A_ID,
                            golfers = listOf(previewGolfer(SCOTTIE_ID, position = 1, payout = BigDecimal(18))),
                        ),
                    ),
            )

        val overlaid = mergeLiveData(base, listOf(live), DEFAULT_RULES, additive = false)
        overlaid.live shouldBe true
        overlaid.teams.single().cells.single().earnings.compareTo(BigDecimal(18)) shouldBe 0
        overlaid.teams.single().cells.single().positionStr shouldBe "1"
        overlaid.teams.single().cells.single().scoreToPar shouldBe "-10"
    }

    test("mergeLiveData bumps team.topTenMoney + topTenCount for live wins (non-additive mode)") {
        val base =
            emptyReport(
                listOf(
                    teamColumn(
                        TEAM_A_ID,
                        cells =
                            listOf(
                                cell(
                                    round = 1,
                                    golferId = SCOTTIE_ID,
                                    seasonEarnings = BigDecimal(5),
                                    seasonTopTens = 1,
                                ),
                                cell(round = 2, golferId = RORY_ID),
                            ),
                    ).copy(topTenMoney = BigDecimal(5), topTenCount = 1),
                ),
            )
        val live =
            preview(
                teams =
                    listOf(
                        previewTeam(
                            teamId = TEAM_A_ID,
                            golfers = listOf(previewGolfer(RORY_ID, position = 1, payout = BigDecimal(18))),
                        ),
                    ),
            )

        val overlaid = mergeLiveData(base, listOf(live), DEFAULT_RULES, additive = false)
        val team = overlaid.teams.single()
        team.topTenMoney.compareTo(BigDecimal(23)) shouldBe 0
        team.topTenCount shouldBe 2
        // Sanity: cell-level numbers match what the team total claims.
        val cellSum = team.cells.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.seasonEarnings) }
        cellSum.compareTo(team.topTenMoney) shouldBe 0
        team.cells.sumOf { it.seasonTopTens } shouldBe team.topTenCount
    }

    test("mergeLiveData adds live earnings to existing cell.earnings in additive mode") {
        val base =
            emptyReport(
                listOf(
                    teamColumn(
                        TEAM_A_ID,
                        cells = listOf(cell(round = 1, golferId = SCOTTIE_ID, earnings = BigDecimal(5))),
                    ),
                ),
            )
        val live =
            preview(
                teams =
                    listOf(
                        previewTeam(
                            teamId = TEAM_A_ID,
                            golfers = listOf(previewGolfer(SCOTTIE_ID, position = 1, payout = BigDecimal(18))),
                        ),
                    ),
            )

        val overlaid = mergeLiveData(base, listOf(live), DEFAULT_RULES, additive = true)
        overlaid.teams.single().cells.single().earnings.compareTo(BigDecimal(23)) shouldBe 0
    }

    test("mergeLiveData computes weekly +/- as zero-sum across teams") {
        val base =
            emptyReport(
                listOf(
                    teamColumn(TEAM_A_ID, cells = listOf(cell(round = 1, golferId = SCOTTIE_ID))),
                    teamColumn(TEAM_B_ID, cells = listOf(cell(round = 1, golferId = RORY_ID))),
                ),
            )
        val live =
            preview(
                teams =
                    listOf(
                        previewTeam(
                            teamId = TEAM_A_ID,
                            golfers = listOf(previewGolfer(SCOTTIE_ID, position = 1, payout = BigDecimal(18))),
                        ),
                        previewTeam(teamId = TEAM_B_ID),
                    ),
            )

        val overlaid = mergeLiveData(base, listOf(live), DEFAULT_RULES)
        val a = overlaid.teams.single { it.teamId == TEAM_A_ID }
        val b = overlaid.teams.single { it.teamId == TEAM_B_ID }
        a.weeklyTotal.compareTo(BigDecimal(18)) shouldBe 0
        b.weeklyTotal.compareTo(BigDecimal(-18)) shouldBe 0
        a.weeklyTotal.add(b.weeklyTotal).compareTo(BigDecimal.ZERO) shouldBe 0
    }

    // ----- overlayPriorLivePreview -----

    test("overlayPriorLivePreview bumps previous + seasonEarnings without touching this-tournament earnings") {
        val base =
            emptyReport(
                listOf(
                    teamColumn(
                        TEAM_A_ID,
                        cells = listOf(cell(round = 1, golferId = SCOTTIE_ID, earnings = BigDecimal(5))),
                    ),
                    teamColumn(TEAM_B_ID, cells = listOf(cell(round = 1, golferId = RORY_ID))),
                ),
            )
        val priorLive =
            preview(
                teams =
                    listOf(
                        previewTeam(
                            teamId = TEAM_A_ID,
                            golfers = listOf(previewGolfer(SCOTTIE_ID, position = 1, payout = BigDecimal(18))),
                        ),
                        previewTeam(teamId = TEAM_B_ID),
                    ),
            )

        val overlaid = overlayPriorLivePreview(base, priorLive, DEFAULT_RULES)
        val a = overlaid.teams.single { it.teamId == TEAM_A_ID }
        // A had an existing cell.earnings of $5 from THIS tournament — must not change.
        a.cells.single().earnings.compareTo(BigDecimal(5)) shouldBe 0
        // Season aggregates DO update for the prior tournament's win.
        a.cells.single().seasonEarnings.compareTo(BigDecimal(18)) shouldBe 0
        a.cells.single().seasonTopTens shouldBe 1
        // Previous bumped by zero-sum +18; subtotal = previous + (existing) weekly 0
        a.previous.compareTo(BigDecimal(18)) shouldBe 0
        overlaid.live shouldBe true
    }

    // ----- buildLiveLeaderboard / buildUndraftedFromLeaderboard -----

    test("buildLiveLeaderboard sorts by position and formats scoreToPar") {
        val live =
            preview(
                leaderboard =
                    listOf(
                        leaderboardEntry("Third", position = 3, scoreToPar = -5),
                        leaderboardEntry("Leader", position = 1, scoreToPar = -10),
                        leaderboardEntry("Second", position = 2, scoreToPar = 0),
                    ),
            )

        val rendered = buildLiveLeaderboard(live)
        rendered.map { it.name } shouldContainExactly listOf("Leader", "Second", "Third")
        rendered.map { it.scoreToPar } shouldContainExactly listOf("-10", "E", "-5")
    }

    test("buildUndraftedFromLeaderboard returns only top-10 non-rostered competitors with their payouts") {
        val live =
            preview(
                leaderboard =
                    listOf(
                        leaderboardEntry("Rostered Winner", position = 1, rostered = true, teamName = "BROWN"),
                        leaderboardEntry("Undrafted T2", position = 2),
                        leaderboardEntry("Undrafted Outside", position = 11),
                    ),
            )

        val undrafted = buildUndraftedFromLeaderboard(live, DEFAULT_RULES)
        undrafted.map { it.name } shouldContainExactly listOf("Undrafted T2")
        // Position 2 → DEFAULT_PAYOUTS[1] = $12.
        undrafted.single().payout.compareTo(BigDecimal(12)) shouldBe 0
    }

    test("buildUndraftedFromLeaderboard returns empty when every top-10 finisher is rostered") {
        val live =
            preview(
                leaderboard =
                    listOf(
                        leaderboardEntry("Winner", position = 1, rostered = true, teamName = "BROWN"),
                        leaderboardEntry("Second", position = 2, rostered = true, teamName = "WOMBLE"),
                    ),
            )
        buildUndraftedFromLeaderboard(live, DEFAULT_RULES).shouldBeEmpty()
    }
})
