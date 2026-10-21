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

private val TEAM_A_ID = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c01"))
private val TEAM_B_ID = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c02"))
private val TEAM_C_ID = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c03"))
private val SCOTTIE_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d01"))
private val RORY_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d02"))
private val XANDER_ID = GolferId(UUID.fromString("00000000-0000-0000-0000-000000000d03"))

private val DEFAULT_RULES =
    SeasonRules(
        payouts = SeasonRules.DEFAULT_PAYOUTS,
        tieFloor = SeasonRules.DEFAULT_TIE_FLOOR,
        sideBetRounds = SeasonRules.DEFAULT_SIDE_BET_ROUNDS,
        sideBetAmount = SeasonRules.DEFAULT_SIDE_BET_AMOUNT,
    )

private fun preview(
    teams: List<PreviewTeamScore> = emptyList(),
    leaderboard: List<PreviewLeaderboardEntry> = emptyList(),
    multiplier: BigDecimal = BigDecimal.ONE,
    isTeamEvent: Boolean = false,
    fieldGolferIds: Set<GolferId> = emptySet(),
): EspnLivePreview =
    EspnLivePreview(
        espnName = "The Masters",
        payoutMultiplier = multiplier,
        teams = teams,
        leaderboard = leaderboard,
        fieldGolferIds = fieldGolferIds,
        isTeamEvent = isTeamEvent,
    )

private fun previewTeam(
    teamId: TeamId,
    golfers: List<PreviewGolferScore> = emptyList(),
): PreviewTeamScore =
    PreviewTeamScore(
        teamId = teamId,
        topTenEarnings = golfers.fold(BigDecimal.ZERO) { acc, g -> acc.add(g.payout) },
        golferScores = golfers,
    )

private fun previewGolfer(
    golferId: GolferId,
    position: Int,
    payout: BigDecimal,
    numTied: Int = 1,
): PreviewGolferScore =
    PreviewGolferScore(
        golferId = golferId,
        position = position,
        numTied = numTied,
        payout = payout,
    )

private fun leaderboardEntry(
    name: String,
    position: Int?,
    teamNames: List<String> = emptyList(),
    scoreToPar: Int? = -10,
    pairKey: String? = null,
): PreviewLeaderboardEntry =
    PreviewLeaderboardEntry(
        name = name,
        position = position,
        scoreToPar = scoreToPar,
        teamNames = teamNames,
        pairKey = pairKey,
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
        finish = null,
        earnings = earnings,
        ownershipPct = BigDecimal(100),
        seasonEarnings = seasonEarnings,
        seasonTopTens = seasonTopTens,
        inField = false,
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
        scope = ReportScope.Tournament,
        tournament =
            ReportTournamentInfo(
                name = "The Masters",
                startDate = "2026-04-09",
                endDate = "2026-04-12",
                status = TournamentStatus.InProgress,
                payoutMultiplier = BigDecimal.ONE,
                week = null,
            ),
        teams = teams,
        undraftedTopTens = emptyList(),
        sideBetRules = null,
        sideBetDetail = emptyList(),
        standingsOrder = emptyList(),
    )

private val TWENTY_ON_ROUND_EIGHT = ReportSideBetRules(amountPerTeam = BigDecimal(20), rounds = listOf(8))

private fun sideBetEntry(
    teamId: TeamId,
    teamName: String,
    cumulative: BigDecimal,
    outcome: SideBetOutcome?,
): ReportSideBetTeamEntry =
    ReportSideBetTeamEntry(
        teamId = teamId,
        teamName = teamName,
        golferName = "GOLFER",
        cumulativeEarnings = cumulative,
        payout = BigDecimal.ZERO,
        outcome = outcome,
    )

/** BROWN leads round 8 on Scottie's 10 before the live week; WOMBLE's Rory has earned nothing. */
private fun brownLeadingRoundEight(): WeeklyReport =
    emptyReport(
        listOf(
            teamColumn(TEAM_A_ID, cells = listOf(cell(round = 8, golferId = SCOTTIE_ID))),
            teamColumn(TEAM_B_ID, cells = listOf(cell(round = 8, golferId = RORY_ID))),
        ),
    ).copy(
        sideBetRules = TWENTY_ON_ROUND_EIGHT,
        sideBetDetail =
            listOf(
                ReportSideBetRound(
                    round = 8,
                    teams =
                        listOf(
                            sideBetEntry(TEAM_A_ID, "BROWN", BigDecimal(10), SideBetOutcome.Won),
                            sideBetEntry(TEAM_B_ID, "WOMBLE", BigDecimal.ZERO, SideBetOutcome.Lost),
                        ),
                ),
            ),
    )

private fun roryWinsLive(): EspnLivePreview =
    preview(
        teams =
            listOf(
                previewTeam(teamId = TEAM_A_ID),
                previewTeam(
                    teamId = TEAM_B_ID,
                    golfers = listOf(previewGolfer(RORY_ID, position = 1, payout = BigDecimal(18))),
                ),
            ),
    )

private fun teamRanking(
    teamId: TeamId,
    totalCash: BigDecimal = BigDecimal.ZERO,
): TeamRanking =
    TeamRanking(
        teamId = teamId,
        teamName = "TEAM",
        subtotal = totalCash,
        sideBets = BigDecimal.ZERO,
        totalCash = totalCash,
        series = emptyList(),
    )

private val MASTERS_IN_PLAY =
    Tournament(
        id = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000e01")),
        pgaTournamentId = null,
        name = "The Masters",
        seasonId = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000a01")),
        startDate = LocalDate.parse("2026-04-09"),
        endDate = LocalDate.parse("2026-04-12"),
        courseName = null,
        status = TournamentStatus.InProgress,
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        week = "15",
        isTeamEvent = false,
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

class LiveOverlayServiceSpec : FunSpec({

    // ----- mergeLiveData -----

    test("mergeLiveData returns the input report unchanged when previews list is empty") {
        val base = emptyReport(listOf(teamColumn(TEAM_A_ID)))
        mergeLiveData(base, emptyList(), DEFAULT_RULES) shouldBe base
    }

    test("mergeLiveData replaces per-cell earnings with live projections") {
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

        val overlaid = mergeLiveData(base, listOf(live), DEFAULT_RULES)
        overlaid.live shouldBe true
        overlaid.teams.single().cells.single().earnings.compareTo(BigDecimal(18)) shouldBe 0
        overlaid.teams.single().cells.single().positionStr shouldBe "1"
        overlaid.teams.single().cells.single().finish shouldBe ReportCellFinish(position = 1, tied = false)
    }

    test("mergeLiveData marks both cells tied, in finish and label alike, when two rostered golfers share a position") {
        val base =
            emptyReport(
                listOf(
                    teamColumn(
                        TEAM_A_ID,
                        cells = listOf(cell(round = 1, golferId = SCOTTIE_ID), cell(round = 2, golferId = RORY_ID)),
                    ),
                ),
            )
        val sharedSecond =
            listOf(
                previewGolfer(SCOTTIE_ID, position = 2, payout = BigDecimal(11), numTied = 2),
                previewGolfer(RORY_ID, position = 2, payout = BigDecimal(11), numTied = 2),
            )
        val live = preview(teams = listOf(previewTeam(teamId = TEAM_A_ID, golfers = sharedSecond)))

        val cells = mergeLiveData(base, listOf(live), DEFAULT_RULES).teams.single().cells

        cells.map { it.finish } shouldContainExactly List(2) { ReportCellFinish(position = 2, tied = true) }
        cells.map { it.positionStr } shouldContainExactly listOf("T2", "T2")
    }

    test("mergeLiveData leaves a golfer split across two teams untied when he stands alone at his position") {
        val base =
            emptyReport(
                listOf(
                    teamColumn(TEAM_A_ID, cells = listOf(cell(round = 1, golferId = SCOTTIE_ID))),
                    teamColumn(TEAM_B_ID, cells = listOf(cell(round = 1, golferId = SCOTTIE_ID))),
                ),
            )
        val aloneInFirst = listOf(previewGolfer(SCOTTIE_ID, position = 1, payout = BigDecimal(9), numTied = 1))
        val live =
            preview(
                teams =
                    listOf(
                        previewTeam(teamId = TEAM_A_ID, golfers = aloneInFirst),
                        previewTeam(teamId = TEAM_B_ID, golfers = aloneInFirst),
                    ),
            )

        val cells = mergeLiveData(base, listOf(live), DEFAULT_RULES).teams.flatMap { it.cells }

        cells.map { it.finish } shouldContainExactly List(2) { ReportCellFinish(position = 1, tied = false) }
        cells.map { it.positionStr } shouldContainExactly listOf("1", "1")
    }

    test("mergeLiveData marks a rostered golfer tied when the field ties him with an undrafted one") {
        val base = emptyReport(listOf(teamColumn(TEAM_A_ID, cells = listOf(cell(round = 1, golferId = SCOTTIE_ID)))))
        val tiedWithUndrafted = previewGolfer(SCOTTIE_ID, position = 2, payout = BigDecimal(11), numTied = 2)
        val live = preview(teams = listOf(previewTeam(teamId = TEAM_A_ID, golfers = listOf(tiedWithUndrafted))))

        val cell = mergeLiveData(base, listOf(live), DEFAULT_RULES).teams.single().cells.single()

        cell.finish shouldBe ReportCellFinish(position = 2, tied = true)
        cell.positionStr shouldBe "T2"
    }

    test("mergeLiveData marks a team event's partner tied when a second pair shares the position") {
        val base = emptyReport(listOf(teamColumn(TEAM_A_ID, cells = listOf(cell(round = 1, golferId = SCOTTIE_ID)))))
        val oneOfFourPartnerRows = previewGolfer(SCOTTIE_ID, position = 2, payout = BigDecimal("5.5"), numTied = 4)
        val live =
            preview(
                teams = listOf(previewTeam(teamId = TEAM_A_ID, golfers = listOf(oneOfFourPartnerRows))),
                isTeamEvent = true,
            )

        val cell = mergeLiveData(base, listOf(live), DEFAULT_RULES).teams.single().cells.single()

        cell.finish shouldBe ReportCellFinish(position = 2, tied = true)
        cell.positionStr shouldBe "T2"
    }

    test("mergeLiveData leaves a team event's partner untied when his pair has the position to itself") {
        val base = emptyReport(listOf(teamColumn(TEAM_A_ID, cells = listOf(cell(round = 1, golferId = SCOTTIE_ID)))))
        val oneOfTwoPartnerRows = previewGolfer(SCOTTIE_ID, position = 1, payout = BigDecimal(9), numTied = 2)
        val live =
            preview(
                teams = listOf(previewTeam(teamId = TEAM_A_ID, golfers = listOf(oneOfTwoPartnerRows))),
                isTeamEvent = true,
            )

        val cell = mergeLiveData(base, listOf(live), DEFAULT_RULES).teams.single().cells.single()

        cell.finish shouldBe ReportCellFinish(position = 1, tied = false)
        cell.positionStr shouldBe "1"
    }

    test("mergeLiveData bumps team.topTenMoney + topTenCount for live wins") {
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

        val overlaid = mergeLiveData(base, listOf(live), DEFAULT_RULES)
        val team = overlaid.teams.single()
        team.topTenMoney.compareTo(BigDecimal(23)) shouldBe 0
        team.topTenCount shouldBe 2
        // Sanity: cell-level numbers match what the team total claims.
        val cellSum = team.cells.fold(BigDecimal.ZERO) { acc, c -> acc.add(c.seasonEarnings) }
        cellSum.compareTo(team.topTenMoney) shouldBe 0
        team.cells.sumOf { it.seasonTopTens } shouldBe team.topTenCount
    }

    test("mergeLiveData side-bet folds this-week live payouts into the per-round cumulative") {
        val rodgersCell =
            cell(round = 8, golferId = SCOTTIE_ID, seasonEarnings = BigDecimal(10), seasonTopTens = 1)
        val coodyCell = cell(round = 8, golferId = RORY_ID)
        val base =
            emptyReport(
                listOf(
                    teamColumn(TEAM_A_ID, cells = listOf(rodgersCell))
                        .copy(topTenMoney = BigDecimal(10), topTenCount = 1),
                    teamColumn(TEAM_B_ID, cells = listOf(coodyCell)),
                ),
            ).copy(
                sideBetDetail =
                    listOf(
                        ReportSideBetRound(
                            round = 8,
                            teams =
                                listOf(
                                    ReportSideBetTeamEntry(
                                        teamId = TEAM_A_ID,
                                        teamName = "BROWN",
                                        golferName = "RODGERS",
                                        cumulativeEarnings = BigDecimal(10),
                                        payout = BigDecimal.ZERO,
                                        outcome = null,
                                    ),
                                    ReportSideBetTeamEntry(
                                        teamId = TEAM_B_ID,
                                        teamName = "WOMBLE",
                                        golferName = "COODY",
                                        cumulativeEarnings = BigDecimal.ZERO,
                                        payout = BigDecimal.ZERO,
                                        outcome = null,
                                    ),
                                ),
                        ),
                    ),
            )
        val live =
            preview(
                teams =
                    listOf(
                        previewTeam(teamId = TEAM_A_ID),
                        previewTeam(
                            teamId = TEAM_B_ID,
                            golfers = listOf(previewGolfer(RORY_ID, position = 2, payout = BigDecimal(10))),
                        ),
                    ),
            )

        val overlaid = mergeLiveData(base, listOf(live), DEFAULT_RULES)
        val r8 = overlaid.sideBetDetail.single()
        val a = r8.teams.single { it.teamId == TEAM_A_ID }
        val b = r8.teams.single { it.teamId == TEAM_B_ID }
        a.cumulativeEarnings.compareTo(BigDecimal(10)) shouldBe 0
        b.cumulativeEarnings.compareTo(BigDecimal(10)) shouldBe 0
        a.payout.compareTo(b.payout) shouldBe 0
    }

    test("mergeLiveData re-settles each side-bet outcome on the live cumulative and keeps the season's rules") {
        val twentyPerTeam = DEFAULT_RULES.copy(sideBetAmount = BigDecimal(20))

        val overlaid = mergeLiveData(brownLeadingRoundEight(), listOf(roryWinsLive()), twentyPerTeam)

        overlaid.sideBetRules shouldBe TWENTY_ON_ROUND_EIGHT
        val roundEight = overlaid.sideBetDetail.single().teams
        roundEight.map { it.teamName to it.outcome } shouldContainExactly
            listOf("WOMBLE" to SideBetOutcome.Won, "BROWN" to SideBetOutcome.Lost)
        roundEight.map { it.payout } shouldContainExactly listOf(BigDecimal(20), BigDecimal(-20))
    }

    test("mergeLiveData re-ranks a side-bet round the live week ties in the report's team order, not the base's") {
        val wombleFirst = brownLeadingRoundEight().let { base -> base.copy(teams = base.teams.reversed()) }
        val roryTiesScottie =
            preview(
                teams =
                    listOf(
                        previewTeam(teamId = TEAM_A_ID),
                        previewTeam(
                            teamId = TEAM_B_ID,
                            golfers = listOf(previewGolfer(RORY_ID, position = 2, payout = BigDecimal(10))),
                        ),
                    ),
            )

        val overlaid = mergeLiveData(wombleFirst, listOf(roryTiesScottie), DEFAULT_RULES)

        overlaid.sideBetDetail.single().teams.map { it.teamName } shouldContainExactly listOf("WOMBLE", "BROWN")
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

    test("mergeLiveData marks every cell whose golfer is in the field, paid or yet to tee off, and pays the paid") {
        val base =
            emptyReport(
                listOf(
                    teamColumn(
                        TEAM_A_ID,
                        cells =
                            listOf(
                                cell(round = 1, golferId = SCOTTIE_ID),
                                cell(round = 2, golferId = RORY_ID),
                                cell(round = 3, golferId = XANDER_ID),
                                cell(round = 4),
                            ),
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
                fieldGolferIds = setOf(SCOTTIE_ID, RORY_ID),
            )

        val cells = mergeLiveData(base, listOf(live), DEFAULT_RULES).teams.single().cells

        cells.map { it.inField } shouldContainExactly listOf(true, true, false, false)
        cells.map { it.earnings.toInt() } shouldContainExactly listOf(18, 0, 0, 0)
    }

    test("mergeLiveData marks cells from the selected preview's field alone") {
        val base = emptyReport(listOf(teamColumn(TEAM_A_ID, cells = listOf(cell(round = 1, golferId = SCOTTIE_ID)))))
        val previews = listOf(preview(), preview(fieldGolferIds = setOf(SCOTTIE_ID)))

        mergeLiveData(base, previews, DEFAULT_RULES).teams.single().cells.single().inField shouldBe false
    }

    // ----- overlayPriorLivePreview -----

    test("overlayPriorLivePreview never marks a cell from a prior tournament's field") {
        val base = emptyReport(listOf(teamColumn(TEAM_A_ID, cells = listOf(cell(round = 1, golferId = SCOTTIE_ID)))))
        val priorLive = preview(teams = listOf(previewTeam(TEAM_A_ID)), fieldGolferIds = setOf(SCOTTIE_ID))

        overlayPriorLivePreview(base, priorLive, DEFAULT_RULES).teams.single().cells.single().inField shouldBe false
    }

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

    test("overlayPriorLivePreview bumps team.topTenMoney + topTenCount for the prior tournament's paid cells only") {
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
                                cell(round = 2, golferId = XANDER_ID),
                                cell(round = 3, golferId = RORY_ID),
                            ),
                    ).copy(topTenMoney = BigDecimal(5), topTenCount = 1),
                ),
            )
        val priorLive =
            preview(
                teams =
                    listOf(
                        previewTeam(
                            teamId = TEAM_A_ID,
                            golfers =
                                listOf(
                                    previewGolfer(SCOTTIE_ID, position = 1, payout = BigDecimal(18)),
                                    previewGolfer(RORY_ID, position = 2, payout = BigDecimal(10)),
                                ),
                        ),
                    ),
            )

        val team = overlayPriorLivePreview(base, priorLive, DEFAULT_RULES).teams.single()

        team.topTenMoney.compareTo(BigDecimal(33)) shouldBe 0
        team.topTenCount shouldBe 3
        team.cells.map { it.seasonTopTens } shouldContainExactly listOf(2, 0, 1)
    }

    test("overlayPriorLivePreview re-settles each side-bet outcome and keeps the season's rules") {
        val twentyPerTeam = DEFAULT_RULES.copy(sideBetAmount = BigDecimal(20))

        val overlaid = overlayPriorLivePreview(brownLeadingRoundEight(), roryWinsLive(), twentyPerTeam)

        overlaid.sideBetRules shouldBe TWENTY_ON_ROUND_EIGHT
        val roundEight = overlaid.sideBetDetail.single().teams
        roundEight.map { it.teamName to it.outcome } shouldContainExactly
            listOf("WOMBLE" to SideBetOutcome.Won, "BROWN" to SideBetOutcome.Lost)
        roundEight.map { it.payout } shouldContainExactly listOf(BigDecimal(20), BigDecimal(-20))
    }

    // ----- totals after an overlay -----

    test("mergeLiveData totals the overlaid teams where the base report's totals were all zero") {
        val base = brownLeadingRoundEight()
        val twentyPerTeam = DEFAULT_RULES.copy(sideBetAmount = BigDecimal(20))

        val overlaid = mergeLiveData(base, listOf(roryWinsLive()), twentyPerTeam)

        base.cashTotals.won.signum() shouldBe 0
        base.sideBetTotals.won.signum() shouldBe 0
        overlaid.teams.map { it.totalCash.toInt() } shouldContainExactly listOf(-38, 38)
        overlaid.cashTotals.won.compareTo(BigDecimal(38)) shouldBe 0
        overlaid.cashTotals.lost.compareTo(BigDecimal(-38)) shouldBe 0
        overlaid.teams.map { it.sideBets.toInt() } shouldContainExactly listOf(-20, 20)
        overlaid.sideBetTotals.won.compareTo(BigDecimal(20)) shouldBe 0
        overlaid.sideBetTotals.lost.compareTo(BigDecimal(-20)) shouldBe 0
    }

    test("overlayPriorLivePreview totals the overlaid teams where the base report's totals were all zero") {
        val base = brownLeadingRoundEight()
        val twentyPerTeam = DEFAULT_RULES.copy(sideBetAmount = BigDecimal(20))

        val overlaid = overlayPriorLivePreview(base, roryWinsLive(), twentyPerTeam)

        base.cashTotals.won.signum() shouldBe 0
        base.sideBetTotals.won.signum() shouldBe 0
        overlaid.teams.map { it.totalCash.toInt() } shouldContainExactly listOf(-38, 38)
        overlaid.cashTotals.won.compareTo(BigDecimal(38)) shouldBe 0
        overlaid.cashTotals.lost.compareTo(BigDecimal(-38)) shouldBe 0
        overlaid.teams.map { it.sideBets.toInt() } shouldContainExactly listOf(-20, 20)
        overlaid.sideBetTotals.won.compareTo(BigDecimal(20)) shouldBe 0
        overlaid.sideBetTotals.lost.compareTo(BigDecimal(-20)) shouldBe 0
    }

    test("mergeLiveData totals the overlaid weekly +/- where the base report's weekly totals were all zero") {
        val base = brownLeadingRoundEight()

        val overlaid = mergeLiveData(base, listOf(roryWinsLive()), DEFAULT_RULES)

        base.weeklyTotals.won.signum() shouldBe 0
        base.weeklyTotals.lost.signum() shouldBe 0
        overlaid.teams.map { it.weeklyTotal.toInt() } shouldContainExactly listOf(-18, 18)
        overlaid.weeklyTotals.won.compareTo(BigDecimal(18)) shouldBe 0
        overlaid.weeklyTotals.lost.compareTo(BigDecimal(-18)) shouldBe 0
        overlaid.weeklyTotals.net.signum() shouldBe 0
    }

    // ----- weekly and side-bet standings -----

    test("mergeLiveData reorders the weekly standings by the overlaid weekly +/-") {
        val base = brownLeadingRoundEight()

        val overlaid = mergeLiveData(base, listOf(roryWinsLive()), DEFAULT_RULES)

        base.weeklyStandings shouldContainExactly listOf(TEAM_A_ID, TEAM_B_ID)
        overlaid.weeklyStandings shouldContainExactly listOf(TEAM_B_ID, TEAM_A_ID)
    }

    test("mergeLiveData reorders the side-bet standings by the overlaid side bets") {
        val base = brownLeadingRoundEight()
        val twentyPerTeam = DEFAULT_RULES.copy(sideBetAmount = BigDecimal(20))

        val overlaid = mergeLiveData(base, listOf(roryWinsLive()), twentyPerTeam)

        base.sideBetStandings shouldContainExactly listOf(TEAM_A_ID, TEAM_B_ID)
        overlaid.sideBetStandings shouldContainExactly listOf(TEAM_B_ID, TEAM_A_ID)
    }

    test("weekly standings keep the report's team order through a tie, whatever each total's scale") {
        val report =
            emptyReport(
                listOf(
                    teamColumn(TEAM_A_ID).copy(weeklyTotal = BigDecimal("-4")),
                    teamColumn(TEAM_B_ID).copy(weeklyTotal = BigDecimal("5.00")),
                    teamColumn(TEAM_C_ID).copy(weeklyTotal = BigDecimal("5")),
                ),
            )

        report.weeklyStandings shouldContainExactly listOf(TEAM_B_ID, TEAM_C_ID, TEAM_A_ID)
    }

    test("side-bet standings keep the report's team order through a tie, whatever each total's scale") {
        val report =
            emptyReport(
                listOf(
                    teamColumn(TEAM_A_ID).copy(sideBets = BigDecimal("-40")),
                    teamColumn(TEAM_B_ID).copy(sideBets = BigDecimal("20.00")),
                    teamColumn(TEAM_C_ID).copy(sideBets = BigDecimal("20")),
                ),
            )

        report.sideBetStandings shouldContainExactly listOf(TEAM_B_ID, TEAM_C_ID, TEAM_A_ID)
    }

    // ----- overlayLiveRankings -----

    test("overlayLiveRankings ranks the standings on the overlaid totals, not the stored ones") {
        val brownLeadsByTen =
            Rankings(
                teams = listOf(teamRanking(TEAM_A_ID, totalCash = BigDecimal(10)), teamRanking(TEAM_B_ID)),
                weeks = emptyList(),
                tournamentNames = emptyList(),
            )
        val context =
            RankingsContext(
                allRosters = emptyList(),
                rules = DEFAULT_RULES,
                sideBetPerRound = emptyList(),
                numTeams = 2,
            )

        val (overlaid, _) = overlayLiveRankings(brownLeadsByTen, MASTERS_IN_PLAY, roryWinsLive(), context)

        // Rory's 18 is +18 for WOMBLE and -18 for BROWN: WOMBLE 18, BROWN -8.
        overlaid.standings shouldContainExactly
            listOf(
                TeamStanding.Leader(
                    teamId = TEAM_B_ID,
                    tied = false,
                    aheadOf = StandingGap(position = 2, tied = false, amount = BigDecimal(26)),
                ),
                TeamStanding.Chaser(
                    teamId = TEAM_A_ID,
                    tied = false,
                    position = 2,
                    behind = StandingGap(position = 1, tied = false, amount = BigDecimal(26)),
                    behindLeader = BigDecimal(26),
                ),
            )
    }

    // ----- buildLiveLeaderboard / buildUndraftedFromLeaderboard -----

    test("buildLiveLeaderboard sorts by position and passes scoreToPar through as a number") {
        val live =
            preview(
                leaderboard =
                    listOf(
                        leaderboardEntry("Third", position = 3, scoreToPar = -5),
                        leaderboardEntry("Leader", position = 1, scoreToPar = -10),
                        leaderboardEntry("Second", position = 2, scoreToPar = 0),
                    ),
            )

        val rendered = buildLiveLeaderboard(live.leaderboard)
        rendered.map { row -> row.players.map { it.name } } shouldContainExactly
            listOf(listOf("Leader"), listOf("Second"), listOf("Third"))
        rendered.map { it.scoreToPar } shouldContainExactly listOf(-10, 0, -5)
    }

    test("buildLiveLeaderboard lists the slots yet to tee off after every placed one, in the order given") {
        val rendered =
            buildLiveLeaderboard(
                listOf(
                    leaderboardEntry("Late Wave", position = null, scoreToPar = null),
                    leaderboardEntry("Leader", position = 1, scoreToPar = -3),
                    leaderboardEntry("Later Wave", position = null, scoreToPar = null),
                ),
            )

        rendered.map { row -> row.players.single().name to row.position } shouldContainExactly
            listOf("Leader" to 1, "Late Wave" to null, "Later Wave" to null)
    }

    test("buildLiveLeaderboard makes one row of a team event's partners, each with the teams rostering him") {
        val bothTeams = listOf("BROWN", "WOMBLE")
        val live =
            preview(
                leaderboard =
                    listOf(
                        leaderboardEntry("Novak", position = 1, teamNames = bothTeams, pairKey = "team:1"),
                        leaderboardEntry("Griffin", position = 1, pairKey = "team:1"),
                        leaderboardEntry("Solo", position = 2),
                    ),
            )

        val rendered = buildLiveLeaderboard(live.leaderboard)

        rendered.map { it.players } shouldContainExactly
            listOf(
                listOf(LeaderboardPlayer("Novak", bothTeams), LeaderboardPlayer("Griffin", emptyList())),
                listOf(LeaderboardPlayer("Solo", emptyList())),
            )
        rendered.map { it.rostered } shouldContainExactly listOf(true, false)
    }

    test("mergeLiveData replaces the report's leaderboard with the live field") {
        val stale = listOf(LeaderboardPlayer("Stale", emptyList()))
        val persisted = LiveLeaderboardEntry(position = 1, scoreToPar = -5, players = stale)
        val base = emptyReport(listOf(teamColumn(TEAM_A_ID))).copy(liveLeaderboard = listOf(persisted))
        val live = preview(leaderboard = listOf(leaderboardEntry("Leader", position = 1)))

        val overlaid = mergeLiveData(base, listOf(live), DEFAULT_RULES)

        overlaid.liveLeaderboard.flatMap { it.players }.map { it.name } shouldContainExactly listOf("Leader")
    }

    test("buildUndraftedFromLeaderboard returns only top-10 non-rostered competitors with their payouts") {
        val live =
            preview(
                leaderboard =
                    listOf(
                        leaderboardEntry("Rostered Winner", position = 1, teamNames = listOf("BROWN")),
                        leaderboardEntry("Undrafted T2", position = 2),
                        leaderboardEntry("Undrafted Outside", position = 11),
                    ),
            )

        val undrafted = buildUndraftedFromLeaderboard(live, DEFAULT_RULES)
        undrafted.map { it.name } shouldContainExactly listOf("Undrafted T2")
        // Position 2 → DEFAULT_PAYOUTS[1] = $12.
        undrafted.single().payout.compareTo(BigDecimal(12)) shouldBe 0
    }

    test("buildUndraftedFromLeaderboard pays no competitor yet to tee off and counts none in a tie") {
        val live =
            preview(
                leaderboard =
                    listOf(
                        leaderboardEntry("Leader", position = 1),
                        leaderboardEntry("Late Wave", position = null, scoreToPar = null),
                    ),
            )

        val undrafted = buildUndraftedFromLeaderboard(live, DEFAULT_RULES)

        undrafted.map { it.name } shouldContainExactly listOf("Leader")
        undrafted.single().payout.compareTo(BigDecimal(18)) shouldBe 0
    }

    test("foldLivePlayerRankings credits no top ten to an undrafted competitor yet to tee off") {
        val live =
            preview(
                leaderboard =
                    listOf(
                        leaderboardEntry("Leader", position = 1),
                        leaderboardEntry("Late Wave", position = null, scoreToPar = null),
                    ),
            )

        val folded = foldLivePlayerRankings(PlayerRankingsAcc(emptyMap(), emptyMap()), live, DEFAULT_RULES)

        folded.undrafted.keys shouldContainExactly setOf("Leader")
        folded.undrafted.getValue("Leader").totalEarnings.compareTo(BigDecimal(18)) shouldBe 0
    }

    test("buildUndraftedFromLeaderboard returns empty when every top-10 finisher is rostered") {
        val live =
            preview(
                leaderboard =
                    listOf(
                        leaderboardEntry("Winner", position = 1, teamNames = listOf("BROWN")),
                        leaderboardEntry("Second", position = 2, teamNames = listOf("WOMBLE")),
                    ),
            )
        buildUndraftedFromLeaderboard(live, DEFAULT_RULES).shouldBeEmpty()
    }
})
