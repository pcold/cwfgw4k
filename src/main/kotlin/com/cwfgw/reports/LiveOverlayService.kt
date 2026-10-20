package com.cwfgw.reports

import com.cwfgw.espn.EspnLivePreview
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.LivePreviewContext
import com.cwfgw.espn.LivePreviewError
import com.cwfgw.espn.PreviewLeaderboardEntry
import com.cwfgw.golfers.GolferId
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
import com.cwfgw.scoring.PayoutTable
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal

private val log = KotlinLogging.logger {}

/**
 * In [overlayReport], unless the selected tournament is completed, a prior unfinalized one's live payouts roll into
 * `previous` and the season columns and the selected one's replace its cells' earnings and finish. A pull that fails
 * is skipped, keeping whatever was already overlaid; an unavailable ESPN logs at WARN.
 */
class LiveOverlayService(private val espnService: EspnService) {
    /**
     * Fold ESPN previews for each non-completed candidate into a player-
     * rankings accumulator. Drafted golfers gain one increment + their
     * ownership-adjusted live payout per (team rostering them, projected
     * top-10), mirroring the per-cell counting [WeeklyReportService]
     * uses for completed tournaments. Unrostered top-10 leaderboard
     * entries are folded into the undrafted bucket by display name with
     * a payout computed from [PayoutTable.tieSplitPayout] so the live
     * and persisted paths share a single payout shape. Per-tournament
     * preview failures are logged and skipped — the operator sees the
     * locked-in numbers rather than an error banner.
     */
    internal suspend fun overlayPlayerRankings(
        seasonId: SeasonId,
        base: PlayerRankingsAcc,
        candidates: List<Tournament>,
        rules: SeasonRules,
    ): PlayerRankingsAcc {
        if (candidates.isEmpty()) return base
        val previewCtx = espnService.loadLivePreviewContext(seasonId, candidates)
        return candidates.sortedWith(tournamentOrdering).fold(base) { acc, tournament ->
            val matched =
                fetchPreviewOrLog(previewCtx, tournament, "player rankings live overlay ${tournament.name}")
                    ?: return@fold acc
            foldLivePlayerRankings(acc, matched, rules)
        }
    }

    /**
     * Overlay live data onto the rankings response. Each non-completed
     * candidate tournament adds a new column to the per-team series
     * (with a ` *` suffix in the tournament-name label) and updates
     * cumulative side-bet totals + each team's live weekly.
     */
    internal suspend fun overlayRankings(
        seasonId: SeasonId,
        baseRankings: Rankings,
        liveCandidates: List<Tournament>,
        ctx: RankingsContext,
    ): Rankings {
        if (liveCandidates.isEmpty()) return baseRankings
        val previewCtx = espnService.loadLivePreviewContext(seasonId, liveCandidates)
        return liveCandidates.fold(baseRankings to ctx) { (rankings, rollingCtx), tournament ->
            tryOverlayCandidate(previewCtx, rankings, tournament, rollingCtx) ?: (rankings to rollingCtx)
        }.first
    }

    internal suspend fun tryOverlayCandidate(
        previewCtx: LivePreviewContext,
        rankings: Rankings,
        tournament: Tournament,
        ctx: RankingsContext,
    ): Pair<Rankings, RankingsContext>? {
        val matched =
            fetchPreviewOrLog(previewCtx, tournament, "rankings live overlay ${tournament.name}")
                ?: return null
        return overlayLiveRankings(rankings, tournament, matched, ctx)
    }

    /**
     * Overlay live data onto a single-tournament report. Walks
     * `priorNonCompleted` chronologically (each prior live tournament
     * adds to the running zero-sum total carried into `previous`); then
     * if the selected tournament is itself not yet completed, overlays
     * the projected ESPN leaderboard onto the report's cells.
     */
    suspend fun overlayReport(
        seasonId: SeasonId,
        baseReport: WeeklyReport,
        rules: SeasonRules,
        priorNonCompleted: List<Tournament>,
        selectedTournament: Tournament,
        tournamentId: TournamentId,
    ): WeeklyReport {
        // Finalized tournaments are immutable from the report's perspective;
        // hitting ESPN can't change the numbers and just costs latency / quota.
        if (selectedTournament.status == TournamentStatus.Completed) return baseReport

        val previewCtx = espnService.loadLivePreviewContext(seasonId, priorNonCompleted + selectedTournament)

        val withPrior =
            priorNonCompleted.sortedWith(tournamentOrdering)
                .fold(baseReport) { acc, prior ->
                    val matched =
                        fetchPreviewOrLog(previewCtx, prior, "prior live overlay")
                            ?: return@fold acc
                    overlayPriorLivePreview(acc, matched, rules)
                }

        return when (val selected = espnService.previewForTournament(previewCtx, selectedTournament)) {
            is Result.Ok -> mergeLiveData(withPrior, listOf(selected.value), rules)
            is Result.Err -> {
                logIfUnavailable(selected.error, "live overlay $tournamentId")
                withPrior.withUnpaidPreview(selected.error)
            }
        }
    }

    private suspend fun fetchPreviewOrLog(
        previewCtx: LivePreviewContext,
        tournament: Tournament,
        context: String,
    ): EspnLivePreview? =
        espnService.previewForTournament(previewCtx, tournament).getOrElse { error ->
            logIfUnavailable(error, context)
            null
        }

    private fun logIfUnavailable(
        error: LivePreviewError,
        context: String,
    ) {
        when (error) {
            LivePreviewError.NoMatchingEvent, is LivePreviewError.NotStarted -> Unit
            is LivePreviewError.EspnUnavailable -> log.warn { "$context: ESPN preview failed: ${error.cause}" }
        }
    }
}

/**
 * A field nobody has teed off pays nothing, so only its leaderboard is shown and [WeeklyReport.live] is left as is.
 * ESPN can match the event before it lists a field; the persisted rows are then the only leaderboard.
 */
private fun WeeklyReport.withUnpaidPreview(error: LivePreviewError): WeeklyReport =
    if (error is LivePreviewError.NotStarted && error.field.isNotEmpty()) {
        copy(liveLeaderboard = buildLiveLeaderboard(error.field))
    } else {
        this
    }

/**
 * Carrier for the data [LiveOverlayService.overlayRankings] needs to
 * fold a live tournament into existing rankings: rosters (to apply
 * live earnings × ownership), the rules, the per-round side-bet
 * snapshots, and team count. The snapshots evolve across iterations
 * — one round's cumulative grows as each candidate's live earnings
 * land — so the context is returned alongside the updated rankings
 * and threaded into the next iteration.
 */
internal data class RankingsContext(
    val allRosters: List<com.cwfgw.teams.RosterEntry>,
    val rules: SeasonRules,
    val sideBetPerRound: List<SideBetRoundSnapshot>,
    val numTeams: Int,
)

/**
 * Add a prior tournament's live preview totals to the report's
 * `previous` (zero-sum across teams) and bump per-cell season
 * aggregates. Does NOT touch per-cell `earnings` — those belong to the
 * selected tournament. Recomputes side-bet detail with the live data
 * folded in.
 */
internal fun overlayPriorLivePreview(
    report: WeeklyReport,
    preview: EspnLivePreview,
    rules: SeasonRules,
): WeeklyReport {
    val numTeams = report.teams.size
    if (numTeams == 0) return report

    val totalPot = preview.teams.fold(BigDecimal.ZERO) { acc, team -> acc.add(team.topTenEarnings) }
    val zeroSumByTeam =
        preview.teams.associate { team ->
            team.teamId to team.topTenEarnings.multiply(BigDecimal(numTeams)).subtract(totalPot)
        }
    val golferPayouts: Map<Pair<TeamId, GolferId>, Pair<BigDecimal, Int>> =
        preview.teams.flatMap { team ->
            team.golferScores.map { score -> (team.teamId to score.golferId) to (score.payout to score.position) }
        }.toMap()

    val updatedTeams = report.teams.map { team -> bumpPriorTeamCells(team, zeroSumByTeam, golferPayouts) }
    val updatedSideBetDetail =
        updateSideBetDetailWith(
            sideBetDetail = report.sideBetDetail,
            teams = updatedTeams,
            earningsLookup = { teamId, golferId -> golferPayouts[teamId to golferId]?.first ?: BigDecimal.ZERO },
            numTeams = numTeams,
            sideBetPerTeam = rules.sideBetAmount,
        )
    val sideBetTotals = aggregateSideBetTotals(updatedSideBetDetail)
    val finalTeams =
        updatedTeams.map { team ->
            val newSideBets = sideBetTotals[team.teamId] ?: BigDecimal.ZERO
            team.copy(sideBets = newSideBets, totalCash = team.subtotal.add(newSideBets))
        }

    return report.copy(
        teams = finalTeams,
        sideBetDetail = updatedSideBetDetail,
        standingsOrder = buildStandingsOrder(finalTeams),
        live = true,
    )
}

private fun bumpPriorTeamCells(
    team: ReportTeamColumn,
    zeroSumByTeam: Map<TeamId, BigDecimal>,
    golferPayouts: Map<Pair<TeamId, GolferId>, Pair<BigDecimal, Int>>,
): ReportTeamColumn {
    val priorWeekly = zeroSumByTeam[team.teamId] ?: BigDecimal.ZERO
    val newPrevious = team.previous.add(priorWeekly)
    val newSubtotal = newPrevious.add(team.weeklyTotal)

    var addedTopTens = 0
    var addedMoney = BigDecimal.ZERO
    val updatedCells =
        team.cells.map { cell ->
            val payout = cell.golferId?.let { golferPayouts[team.teamId to it]?.first }
            if (payout == null) {
                cell
            } else {
                addedTopTens += 1
                addedMoney = addedMoney.add(payout)
                cell.copy(seasonEarnings = cell.seasonEarnings.add(payout), seasonTopTens = cell.seasonTopTens + 1)
            }
        }

    return team.copy(
        previous = newPrevious,
        subtotal = newSubtotal,
        totalCash = newSubtotal.add(team.sideBets),
        cells = updatedCells,
        topTenCount = team.topTenCount + addedTopTens,
        topTenMoney = team.topTenMoney.add(addedMoney),
    )
}

/**
 * Replace per-cell tournament data with live projected payouts and
 * recompute weekly / subtotal / total cash. Also surfaces the live
 * leaderboard for the scoreboard view and rebuilds the undrafted
 * top-10s from it.
 */
internal fun mergeLiveData(
    report: WeeklyReport,
    previews: List<EspnLivePreview>,
    rules: SeasonRules,
): WeeklyReport {
    val liveData = previews.firstOrNull() ?: return report
    val livePayout = livePayoutMap(liveData)

    val numTeams = report.teams.size
    val phase1 = report.teams.map { team -> overlayTeamCells(team, livePayout) }
    val totalPot = phase1.fold(BigDecimal.ZERO) { acc, snapshot -> acc.add(snapshot.weeklyTopTenEarnings) }
    val liveSideBetDetail =
        updateSideBetDetailWith(
            sideBetDetail = report.sideBetDetail,
            teams = phase1.map { it.team },
            earningsLookup = { teamId, golferId -> livePayout[teamId to golferId]?.payout ?: BigDecimal.ZERO },
            numTeams = numTeams,
            sideBetPerTeam = rules.sideBetAmount,
        )
    val liveSideBetTotals = aggregateSideBetTotals(liveSideBetDetail)
    val finalTeams = phase1.map { it.toFinalTeam(numTeams, totalPot, liveSideBetTotals) }
    val updatedUndrafted = buildUndraftedFromLeaderboard(liveData, rules)

    return report.copy(
        teams = finalTeams,
        undraftedTopTens = updatedUndrafted,
        sideBetDetail = liveSideBetDetail,
        standingsOrder = buildStandingsOrder(finalTeams),
        live = true,
        liveLeaderboard = buildLiveLeaderboard(liveData.leaderboard),
    )
}

/**
 * Fold one live tournament into running rankings. Updates each round's
 * cumulative side-bet totals with the live golfer earnings (× ownership
 * pct), recomputes side-bet payouts from the new totals, and bumps
 * each team's `subtotal` / `series` by their projected live weekly +/-.
 * The matched-tournament name gets a ` *` suffix on the chart label so
 * the UI can distinguish projected from finalized columns.
 */
internal fun overlayLiveRankings(
    baseRankings: Rankings,
    tournament: Tournament,
    preview: EspnLivePreview,
    ctx: RankingsContext,
): Pair<Rankings, RankingsContext> {
    val numTeams = ctx.numTeams
    val totalPot = preview.teams.fold(BigDecimal.ZERO) { acc, team -> acc.add(team.topTenEarnings) }
    val liveWeekly =
        preview.teams.associate { team ->
            team.teamId to team.topTenEarnings.multiply(BigDecimal(numTeams)).subtract(totalPot)
        }
    val liveByGolfer: Map<Pair<TeamId, GolferId>, BigDecimal> =
        preview.teams.flatMap { team -> team.golferScores.map { (team.teamId to it.golferId) to it.payout } }.toMap()

    val updatedSnapshots = ctx.sideBetPerRound.map { snapshot -> snapshot.foldLive(ctx.allRosters, liveByGolfer) }
    val recomputedSnapshots =
        updatedSnapshots.map { snapshot ->
            snapshot.copy(
                payouts = pickSideBetPayouts(snapshot.teamCumulativeEarnings, numTeams, ctx.rules.sideBetAmount),
            )
        }
    val newSideBets = aggregateRoundPayouts(recomputedSnapshots)
    val updatedTeams =
        baseRankings.teams
            .map { it.applyLive(liveWeekly, newSideBets) }
            .sortedByDescending { it.totalCash }

    val updatedRankings =
        baseRankings.copy(
            teams = updatedTeams,
            weeks = baseRankings.weeks + tournament.week.orEmpty(),
            tournamentNames = baseRankings.tournamentNames + "${preview.espnName} *",
            live = true,
        )
    return updatedRankings to ctx.copy(sideBetPerRound = recomputedSnapshots)
}

private fun SideBetRoundSnapshot.foldLive(
    allRosters: List<com.cwfgw.teams.RosterEntry>,
    liveByGolfer: Map<Pair<TeamId, GolferId>, BigDecimal>,
): SideBetRoundSnapshot {
    val updated =
        allRosters.filter { it.draftRound == round }.fold(teamCumulativeEarnings) { acc, entry ->
            val live = liveByGolfer[entry.teamId to entry.golferId] ?: return@fold acc
            if (live.signum() == 0) {
                acc
            } else {
                acc + (entry.teamId to (acc[entry.teamId] ?: BigDecimal.ZERO).add(live))
            }
        }
    return copy(teamCumulativeEarnings = updated)
}

private fun aggregateRoundPayouts(snapshots: List<SideBetRoundSnapshot>): Map<TeamId, BigDecimal> =
    snapshots
        .flatMap { snap -> snap.payouts.entries.map { it.key to it.value } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO, BigDecimal::add) }

private fun TeamRanking.applyLive(
    liveWeekly: Map<TeamId, BigDecimal>,
    newSideBets: Map<TeamId, BigDecimal>,
): TeamRanking {
    val live = liveWeekly[teamId] ?: BigDecimal.ZERO
    val sideBets = newSideBets[teamId] ?: BigDecimal.ZERO
    val newSubtotal = subtotal.add(live)
    val newTotal = newSubtotal.add(sideBets)
    return copy(
        subtotal = newSubtotal,
        sideBets = sideBets,
        totalCash = newTotal,
        series = series + newTotal,
    )
}

private fun livePayoutMap(liveData: EspnLivePreview): Map<Pair<TeamId, GolferId>, LiveCellInputs> =
    liveData.teams.flatMap { team ->
        team.golferScores.map { score ->
            (team.teamId to score.golferId) to
                LiveCellInputs(
                    payout = score.payout,
                    finish =
                        finishAt(
                            position = score.position,
                            numTied = score.numTied,
                            isTeamEvent = liveData.isTeamEvent,
                        ),
                )
        }
    }.toMap()

private fun TeamPhase1.toFinalTeam(
    numTeams: Int,
    totalPot: BigDecimal,
    liveSideBetTotals: Map<TeamId, BigDecimal>,
): ReportTeamColumn {
    val sideBets =
        if (liveSideBetTotals.isNotEmpty()) {
            liveSideBetTotals[team.teamId] ?: BigDecimal.ZERO
        } else {
            originalSideBets
        }
    val weeklyTotal = weeklyTopTenEarnings.multiply(BigDecimal(numTeams)).subtract(totalPot)
    val subtotal = previous.add(weeklyTotal)
    return team.copy(
        weeklyTotal = weeklyTotal,
        subtotal = subtotal,
        sideBets = sideBets,
        totalCash = subtotal.add(sideBets),
    )
}

/** Per-team snapshot computed in phase 1 of [mergeLiveData] — feeds phase 2 (recomputed totals + side bets). */
private data class TeamPhase1(
    val team: ReportTeamColumn,
    val weeklyTopTenEarnings: BigDecimal,
    val previous: BigDecimal,
    val originalSideBets: BigDecimal,
)

/** What the live preview tells us about one (team, golfer) pairing — payout + ESPN finish. */
private data class LiveCellInputs(
    val payout: BigDecimal,
    val finish: ReportCellFinish,
)

private fun overlayTeamCells(
    team: ReportTeamColumn,
    livePayout: Map<Pair<TeamId, GolferId>, LiveCellInputs>,
): TeamPhase1 {
    val updatedCells =
        team.cells.map { cell ->
            val live = cell.golferId?.let { livePayout[team.teamId to it] }
            if (live == null) {
                cell.copy(earnings = BigDecimal.ZERO)
            } else {
                cell.copy(
                    earnings = live.payout,
                    positionStr = live.finish.label(),
                    finish = live.finish,
                    seasonEarnings = cell.seasonEarnings.add(live.payout),
                    seasonTopTens = cell.seasonTopTens + 1,
                )
            }
        }
    val weeklyTopTens = updatedCells.fold(BigDecimal.ZERO) { acc, cell -> acc.add(cell.earnings) }
    // Per-cell seasonEarnings/seasonTopTens already include the live hit, so
    // the team totals follow from a sum over cells.
    val liveTopTenCount = updatedCells.sumOf { it.seasonTopTens }
    val liveTopTenMoney = updatedCells.fold(BigDecimal.ZERO) { acc, cell -> acc.add(cell.seasonEarnings) }
    return TeamPhase1(
        team =
            team.copy(
                cells = updatedCells,
                topTenEarnings = weeklyTopTens,
                topTenCount = liveTopTenCount,
                topTenMoney = liveTopTenMoney,
            ),
        weeklyTopTenEarnings = weeklyTopTens,
        previous = team.previous,
        originalSideBets = team.sideBets,
    )
}

internal fun buildLiveLeaderboard(leaderboard: List<PreviewLeaderboardEntry>): List<LiveLeaderboardEntry> =
    leaderboard
        .sortedWith(compareBy(nullsLast()) { it.position })
        .inCompetitorSlots(PreviewLeaderboardEntry::pairKey)
        .map { slot ->
            LiveLeaderboardEntry(
                position = slot.lead.position,
                scoreToPar = slot.lead.scoreToPar,
                players = slot.members.map { LeaderboardPlayer(name = it.name, teamNames = it.teamNames) },
                roundScores = slot.lead.roundScores,
                totalStrokes = slot.lead.totalStrokes,
            )
        }

internal fun buildUndraftedFromLeaderboard(
    liveData: EspnLivePreview,
    rules: SeasonRules,
): List<UndraftedGolfer> {
    val tiedAtPosition = liveData.leaderboard.tiesByPosition()
    return liveData.leaderboard
        .filterNot { it.rostered }
        .mapNotNull { entry -> entry.position?.takeIf { it <= TOP_TEN }?.let { position -> entry.name to position } }
        .sortedBy { (_, position) -> position }
        .map { (name, position) ->
            val numTied = tiedAtPosition[position] ?: 1
            val payout =
                PayoutTable.tieSplitPayout(
                    position = position,
                    numTied = numTied,
                    multiplier = liveData.payoutMultiplier,
                    rules = rules,
                    isTeamEvent = liveData.isTeamEvent,
                )
            UndraftedGolfer(name = name, payout = payout)
        }
}

private fun List<PreviewLeaderboardEntry>.tiesByPosition(): Map<Int, Int> =
    mapNotNull { it.position }.groupingBy { it }.eachCount()

/**
 * Walk the existing side-bet detail rounds, fold the supplied live
 * earnings into each team's cumulative, and recompute payouts. Lets
 * both the prior-overlay and the in-progress overlay share the same
 * winner-picking logic.
 */
internal fun updateSideBetDetailWith(
    sideBetDetail: List<ReportSideBetRound>,
    teams: List<ReportTeamColumn>,
    earningsLookup: (TeamId, GolferId) -> BigDecimal,
    numTeams: Int,
    sideBetPerTeam: BigDecimal,
): List<ReportSideBetRound> =
    sideBetDetail.map { round ->
        val updatedEntries =
            round.teams.map { entry ->
                val cell =
                    teams.firstOrNull { it.teamId == entry.teamId }
                        ?.cells?.firstOrNull { it.round == round.round }
                val gid = cell?.golferId
                val liveEarnings = if (gid == null) BigDecimal.ZERO else earningsLookup(entry.teamId, gid)
                entry.copy(cumulativeEarnings = entry.cumulativeEarnings.add(liveEarnings))
            }
        round.copy(teams = recomputeSideBetPayouts(updatedEntries, numTeams, sideBetPerTeam))
    }

internal fun aggregateSideBetTotals(sideBetDetail: List<ReportSideBetRound>): Map<TeamId, BigDecimal> =
    sideBetDetail
        .flatMap { round -> round.teams.map { it.teamId to it.payout } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, payouts) -> payouts.fold(BigDecimal.ZERO, BigDecimal::add) }

private const val TOP_TEN = 10

internal fun foldLivePlayerRankings(
    acc: PlayerRankingsAcc,
    preview: EspnLivePreview,
    rules: SeasonRules,
): PlayerRankingsAcc {
    val drafted = acc.drafted.toMutableMap()
    preview.teams.flatMap { it.golferScores }.forEach { golferScore ->
        val existing = drafted[golferScore.golferId] ?: DraftedAgg(topTens = 0, totalEarnings = BigDecimal.ZERO)
        drafted[golferScore.golferId] =
            existing.copy(
                topTens = existing.topTens + 1,
                totalEarnings = existing.totalEarnings.add(golferScore.payout),
            )
    }

    val undrafted = acc.undrafted.toMutableMap()
    val tiedAtPosition = preview.leaderboard.tiesByPosition()
    preview.leaderboard.forEach { entry ->
        liveUndraftedPayout(entry, preview, rules, tiedAtPosition)?.let { payout ->
            val existing = undrafted[entry.name] ?: UndraftedAgg(topTens = 0, totalEarnings = BigDecimal.ZERO)
            undrafted[entry.name] =
                existing.copy(
                    topTens = existing.topTens + 1,
                    totalEarnings = existing.totalEarnings.add(payout),
                )
        }
    }

    return acc.copy(drafted = drafted.toMap(), undrafted = undrafted.toMap(), live = true)
}

/**
 * Per-leaderboard-entry payout contribution for the live undrafted
 * branch, or null when the entry doesn't qualify (rostered or outside
 * the top-10). Pulled out of [foldLivePlayerRankings] so the loop body
 * stays linear instead of a stack of `continue` early exits.
 */
private fun liveUndraftedPayout(
    entry: PreviewLeaderboardEntry,
    preview: EspnLivePreview,
    rules: SeasonRules,
    tiedAtPosition: Map<Int, Int>,
): BigDecimal? {
    if (entry.rostered) return null
    val position = entry.position ?: return null
    if (position > TOP_TEN) return null
    val numTied = tiedAtPosition[position] ?: 1
    return PayoutTable.tieSplitPayout(
        position = position,
        numTied = numTied,
        multiplier = preview.payoutMultiplier,
        rules = rules,
        isTeamEvent = preview.isTeamEvent,
    )
}
