package com.cwfgw.reports

import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.result.Result
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.PayoutTable
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamService
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import java.math.BigDecimal

/**
 * Builds the operator-facing weekly report — the 13-column × 8-round grid
 * the league has used for years, plus the surrounding totals (weekly +/-,
 * standings, side-bet detail, undrafted top-10s). The non-live variant
 * snapshots whatever's currently in the DB; the live overlay (Phase 2)
 * will merge in-progress ESPN scoreboard data on top.
 */
class WeeklyReportService(
    private val seasonService: SeasonService,
    private val tournamentService: TournamentService,
    private val teamService: TeamService,
    private val golferService: GolferService,
    private val scoringService: ScoringService,
    private val liveOverlayService: LiveOverlayService,
) {
    /**
     * Render the report as of one specific tournament. Loads everything the
     * pure assembly needs in one pass and hands it to [assembleWeeklyReport].
     * Cross-season tournament references would be a programmer error in the
     * route layer, not a user-facing failure mode, so we don't validate the
     * tournament's seasonId against the requested seasonId here.
     */
    suspend fun getReport(
        seasonId: SeasonId,
        tournamentId: TournamentId,
        live: Boolean = false,
    ): Result<WeeklyReport, ReportError> {
        seasonService.get(seasonId) ?: return Result.Err(ReportError.SeasonNotFound(seasonId))
        val tournament =
            tournamentService.get(tournamentId)
                ?: return Result.Err(ReportError.TournamentNotFound(tournamentId))

        val rules = seasonService.getRules(seasonId) ?: SeasonRules.defaults()
        val teams = teamService.listBySeason(seasonId)
        val results = tournamentService.getResults(tournamentId)
        val allGolfers = golferService.list(activeOnly = false, search = null)
        val scores = scoringService.getScores(seasonId, tournamentId)
        val allCompletedTournaments = tournamentService.list(seasonId, status = TournamentStatus.Completed)
        val allRosters = teams.flatMap { teamService.getRoster(it.id) }
        val allScores = allCompletedTournaments.flatMap { scoringService.getScores(seasonId, it.id) }

        val inputs =
            WeeklyReportInputs(
                rules = rules,
                teams = teams,
                tournament = tournament,
                results = results,
                allGolfers = allGolfers,
                scores = scores,
                allCompletedTournaments = allCompletedTournaments,
                allRosters = allRosters,
                allScores = allScores,
            )
        val baseReport = assembleWeeklyReport(inputs)
        if (!live) return Result.Ok(baseReport)

        val priorNonCompleted =
            tournamentService.list(seasonId, status = null)
                .filter { it.status != TournamentStatus.Completed && it.id != tournamentId && isBefore(it, tournament) }
        return Result.Ok(
            liveOverlayService.overlayReport(
                seasonId = seasonId,
                baseReport = baseReport,
                rules = rules,
                priorNonCompleted = priorNonCompleted,
                selectedTournament = tournament,
                tournamentId = tournamentId,
            ),
        )
    }

    /**
     * Season-aggregate report: same wire shape as [getReport] but every
     * column rolls up across every completed tournament. The "tournament"
     * info block carries the synthetic id-less placeholder so the UI
     * knows it's looking at a season view and not a specific event.
     */
    suspend fun getSeasonReport(
        seasonId: SeasonId,
        live: Boolean = false,
    ): Result<WeeklyReport, ReportError> {
        seasonService.get(seasonId) ?: return Result.Err(ReportError.SeasonNotFound(seasonId))

        val rules = seasonService.getRules(seasonId) ?: SeasonRules.defaults()
        val teams = teamService.listBySeason(seasonId)
        val allGolfers = golferService.list(activeOnly = false, search = null)
        val completed = tournamentService.list(seasonId, status = TournamentStatus.Completed)
        val allRosters = teams.flatMap { teamService.getRoster(it.id) }
        val allScores = completed.flatMap { scoringService.getScores(seasonId, it.id) }
        val allResults = completed.flatMap { tournamentService.getResults(it.id) }

        val inputs =
            SeasonReportInputs(
                rules = rules,
                teams = teams,
                allGolfers = allGolfers,
                completed = completed,
                allRosters = allRosters,
                allScores = allScores,
                allResults = allResults,
            )
        val baseReport = assembleSeasonReport(inputs)
        if (!live) return Result.Ok(baseReport)

        val nonCompleted =
            tournamentService.list(seasonId, status = null)
                .filter { it.status != TournamentStatus.Completed }
        return Result.Ok(liveOverlayService.overlaySeasonReport(seasonId, baseReport, rules, nonCompleted))
    }

    /**
     * Cumulative team rankings with per-tournament series for the line
     * chart. `through` optionally trims the included tournaments to those
     * at or before a cutoff event so an operator can replay standings as
     * of any past week.
     */
    suspend fun getRankings(
        seasonId: SeasonId,
        throughTournamentId: TournamentId? = null,
        live: Boolean = false,
    ): Result<Rankings, ReportError> {
        seasonService.get(seasonId) ?: return Result.Err(ReportError.SeasonNotFound(seasonId))
        val through =
            throughTournamentId?.let { id ->
                tournamentService.get(id) ?: return Result.Err(ReportError.TournamentNotFound(id))
            }

        val rules = seasonService.getRules(seasonId) ?: SeasonRules.defaults()
        val teams = teamService.listBySeason(seasonId)
        val completed = tournamentService.list(seasonId, status = TournamentStatus.Completed)
        val included = filterThroughTournament(completed, through)
        val allRosters = teams.flatMap { teamService.getRoster(it.id) }
        val allScores = included.flatMap { scoringService.getScores(seasonId, it.id) }

        val sideBetPerRound =
            buildSideBetPerRound(rules, allRosters, allScores, teams.size, rules.sideBetAmount)
        val sideBets = aggregateSideBets(sideBetPerRound)
        val sortedIncluded = included.sortedWith(tournamentOrdering)
        val history = buildCumulativeHistory(sortedIncluded, allScores, teams)
        val currentTotals = history.lastOrNull() ?: teams.associate { it.id to BigDecimal.ZERO }

        val baseTeams =
            teams.map { team ->
                val subtotal = currentTotals[team.id] ?: BigDecimal.ZERO
                val teamSideBets = sideBets[team.id] ?: BigDecimal.ZERO
                TeamRanking(
                    teamId = team.id,
                    teamName = team.teamName,
                    subtotal = subtotal,
                    sideBets = teamSideBets,
                    totalCash = subtotal.add(teamSideBets),
                    series = history.map { snapshot -> (snapshot[team.id] ?: BigDecimal.ZERO).add(teamSideBets) },
                )
            }.sortedByDescending { it.totalCash }
        val baseRankings =
            Rankings(
                teams = baseTeams,
                weeks = sortedIncluded.map { it.week ?: "" },
                tournamentNames = sortedIncluded.map { it.name },
            )
        if (!live) return Result.Ok(baseRankings)

        val liveCandidates = liveCandidatesFor(seasonId, through)
        val ctx =
            RankingsContext(
                allRosters = allRosters,
                rules = rules,
                sideBetPerRound = sideBetPerRound,
                numTeams = teams.size,
            )
        return Result.Ok(liveOverlayService.overlayRankings(seasonId, baseRankings, liveCandidates, ctx))
    }

    /**
     * Live candidates for a rankings overlay: every non-completed
     * tournament strictly before the cutoff, plus the cutoff itself if
     * it's also non-completed. Sorted chronologically so the overlay
     * folds them in the order they'd actually play out.
     */
    private suspend fun liveCandidatesFor(
        seasonId: SeasonId,
        through: Tournament?,
    ): List<Tournament> {
        val nonCompleted =
            tournamentService.list(seasonId, status = null)
                .filter { it.status != TournamentStatus.Completed }
        val candidates =
            if (through == null) {
                nonCompleted
            } else {
                val priorNonCompleted = nonCompleted.filter { isBefore(it, through) }
                val selectedIfLive =
                    if (through.status != TournamentStatus.Completed) listOf(through) else emptyList()
                priorNonCompleted + selectedIfLive
            }
        return candidates.sortedWith(tournamentOrdering)
    }

    /**
     * Top-10 finishes for a single golfer over the season — driven from
     * [TournamentResult] (real leaderboard), not [FantasyScore], so the
     * payouts shown represent what one full-ownership team would have
     * earned, independent of which fantasy teams actually rostered them.
     */
    suspend fun getGolferHistory(
        seasonId: SeasonId,
        golferId: GolferId,
    ): Result<GolferHistory, ReportError> {
        seasonService.get(seasonId) ?: return Result.Err(ReportError.SeasonNotFound(seasonId))
        val golfer = golferService.get(golferId) ?: return Result.Err(ReportError.GolferNotFound(golferId))

        val rules = seasonService.getRules(seasonId) ?: SeasonRules.defaults()
        val completed = tournamentService.list(seasonId, status = TournamentStatus.Completed)
        val byTournament =
            completed.associateWith { tournamentService.getResults(it.id) }

        val entries =
            byTournament
                .mapNotNull { (tournament, results) ->
                    val mine =
                        results.firstOrNull { result ->
                            result.golferId == golferId && (result.position ?: Int.MAX_VALUE) <= TOP_TEN_CUTOFF
                        } ?: return@mapNotNull null
                    val position = mine.position ?: return@mapNotNull null
                    val numTied = results.count { it.position == position }
                    val payout =
                        PayoutTable.tieSplitPayout(
                            position = position,
                            numTied = numTied,
                            multiplier = tournament.payoutMultiplier,
                            rules = rules,
                            isTeamEvent = tournament.isTeamEvent,
                        )
                    GolferHistoryEntry(tournament = tournament.name, position = position, earnings = payout)
                }
                .sortedBy { it.position }

        return Result.Ok(
            GolferHistory(
                golferName = "${golfer.firstName} ${golfer.lastName}",
                golferId = golfer.id,
                totalEarnings = entries.fold(BigDecimal.ZERO) { acc, entry -> acc.add(entry.earnings) },
                topTens = entries.size,
                results = entries,
            ),
        )
    }
}

/**
 * Bag of pre-loaded data the pure [assembleWeeklyReport] needs. Bundled
 * into one type so the assembly signature stays short and so future
 * callers (live overlay, season-aggregate report) can build the same
 * shape without re-deriving the parameter list.
 */
internal data class WeeklyReportInputs(
    val rules: SeasonRules,
    val teams: List<Team>,
    val tournament: Tournament,
    val results: List<TournamentResult>,
    val allGolfers: List<Golfer>,
    val scores: List<FantasyScore>,
    val allCompletedTournaments: List<Tournament>,
    val allRosters: List<RosterEntry>,
    val allScores: List<FantasyScore>,
)

/**
 * Bag of pre-loaded data the season-aggregate assembly needs. Different
 * shape from [WeeklyReportInputs] because we don't have a single
 * `tournament` to anchor on — every column rolls across `completed`.
 */
internal data class SeasonReportInputs(
    val rules: SeasonRules,
    val teams: List<Team>,
    val allGolfers: List<Golfer>,
    val completed: List<Tournament>,
    val allRosters: List<RosterEntry>,
    val allScores: List<FantasyScore>,
    val allResults: List<TournamentResult>,
)

// ----- Pure assembly -----

internal fun assembleWeeklyReport(inputs: WeeklyReportInputs): WeeklyReport {
    val derived = deriveReportContext(inputs)
    val teamColumns = inputs.teams.map { team -> buildTeamColumnFromContext(team, inputs, derived) }
    val rosteredGolferIds = inputs.allRosters.map { it.golferId }.toSet()
    val undraftedTopTens =
        buildUndraftedForTournament(
            results = inputs.results,
            rosteredGolferIds = rosteredGolferIds,
            golferMap = derived.golferMap,
            multiplier = inputs.tournament.payoutMultiplier,
            rules = inputs.rules,
            isTeamEvent = inputs.tournament.isTeamEvent,
        )
    val sideBetDetail = buildSideBetDetail(derived.sideBetPerRound, inputs.teams, inputs.allRosters, derived.golferMap)
    val leaderboard =
        buildPersistedLeaderboard(
            results = inputs.results,
            golferMap = derived.golferMap,
            teamsByGolfer = teamNamesByGolfer(inputs.allRosters, inputs.teams),
        )

    return WeeklyReport(
        tournament = buildTournamentInfo(inputs.tournament),
        teams = teamColumns,
        undraftedTopTens = undraftedTopTens,
        sideBetDetail = sideBetDetail,
        standingsOrder = buildStandingsOrder(teamColumns),
        liveLeaderboard = leaderboard,
    )
}

private fun teamNamesByGolfer(
    rosters: List<RosterEntry>,
    teams: List<Team>,
): Map<GolferId, String> {
    val nameByTeam = teams.associate { it.id to it.teamName }
    return rosters
        .groupBy { it.golferId }
        .mapValues { (_, entries) -> entries.mapNotNull { nameByTeam[it.teamId] }.joinToString(" / ") }
}

internal fun buildPersistedLeaderboard(
    results: List<TournamentResult>,
    golferMap: Map<GolferId, Golfer>,
    teamsByGolfer: Map<GolferId, String>,
): List<LiveLeaderboardEntry> =
    results
        .filter { it.position != null }
        .sortedBy { it.position }
        .map { result ->
            val golfer = golferMap[result.golferId]
            val name = golfer?.let { "${it.firstName} ${it.lastName}" } ?: "?"
            val rounds = listOfNotNull(result.round1, result.round2, result.round3, result.round4)
            val teamName = teamsByGolfer[result.golferId]
            LiveLeaderboardEntry(
                name = name,
                position = result.position ?: 0,
                scoreToPar = result.scoreToPar?.let(::formatScoreToPar),
                rostered = teamName != null,
                teamName = teamName,
                pairKey = result.pairKey,
                roundScores = rounds,
                totalStrokes = result.totalStrokes,
            )
        }

/**
 * Pre-derived lookups + per-team aggregates the team-column builder
 * needs. Computed once per report so each column build is O(1) lookups
 * against shared maps rather than O(N) walks of the loaded data.
 */
private data class ReportContext(
    val golferMap: Map<GolferId, Golfer>,
    val resultsByGolfer: Map<GolferId, TournamentResult>,
    val scoresByTeamGolfer: Map<Pair<TeamId, GolferId>, FantasyScore>,
    val cumulativeByTeamGolfer: Map<Pair<TeamId, GolferId>, Pair<BigDecimal, Int>>,
    val priorWeeklyByTeam: Map<TeamId, BigDecimal>,
    val cumulativeTopTenCounts: Map<TeamId, Int>,
    val cumulativeTopTenEarnings: Map<TeamId, BigDecimal>,
    val sideBetPerRound: List<SideBetRoundSnapshot>,
    val sideBetResults: Map<TeamId, BigDecimal>,
    val numTiedByPosition: Map<Int, Int>,
)

private fun deriveReportContext(inputs: WeeklyReportInputs): ReportContext {
    val numTeams = inputs.teams.size
    val throughTournaments = inputs.allCompletedTournaments.filter { isOnOrBefore(it, inputs.tournament) }
    val throughIds = throughTournaments.map { it.id }.toSet()
    val throughScores = inputs.allScores.filter { it.tournamentId in throughIds }
    val sideBetPerRound =
        buildSideBetPerRound(inputs.rules, inputs.allRosters, throughScores, numTeams, inputs.rules.sideBetAmount)
    return ReportContext(
        golferMap = inputs.allGolfers.associateBy { it.id },
        resultsByGolfer = inputs.results.associateBy { it.golferId },
        scoresByTeamGolfer = inputs.scores.associateBy { it.teamId to it.golferId },
        cumulativeByTeamGolfer =
            throughScores.groupBy { it.teamId to it.golferId }
                .mapValues { (_, scores) -> scores.sumPoints() to scores.size },
        priorWeeklyByTeam =
            buildPriorWeekly(throughTournaments, throughScores, inputs.tournament, inputs.teams, numTeams),
        cumulativeTopTenCounts = throughScores.groupBy { it.teamId }.mapValues { (_, scores) -> scores.size },
        cumulativeTopTenEarnings = throughScores.groupBy { it.teamId }.mapValues { (_, scores) -> scores.sumPoints() },
        sideBetPerRound = sideBetPerRound,
        sideBetResults = aggregateSideBets(sideBetPerRound),
        numTiedByPosition = inputs.results.mapNotNull { it.position }.groupingBy { it }.eachCount(),
    )
}

private fun buildTeamColumnFromContext(
    team: Team,
    inputs: WeeklyReportInputs,
    ctx: ReportContext,
): ReportTeamColumn =
    buildReportTeamColumn(
        team = team,
        allRosters = inputs.allRosters,
        golferMap = ctx.golferMap,
        resultsByGolfer = ctx.resultsByGolfer,
        scoresByTeamGolfer = ctx.scoresByTeamGolfer,
        tournamentScores = inputs.scores,
        cumulativeByTeamGolfer = ctx.cumulativeByTeamGolfer,
        priorWeeklyByTeam = ctx.priorWeeklyByTeam,
        cumulativeTopTenCounts = ctx.cumulativeTopTenCounts,
        cumulativeTopTenEarnings = ctx.cumulativeTopTenEarnings,
        sideBetResults = ctx.sideBetResults,
        numTiedByPosition = ctx.numTiedByPosition,
        numTeams = inputs.teams.size,
    )

internal fun buildTournamentInfo(tournament: Tournament): ReportTournamentInfo =
    ReportTournamentInfo(
        id = tournament.id,
        name = tournament.name,
        startDate = tournament.startDate.toString(),
        endDate = tournament.endDate.toString(),
        status = tournament.status,
        payoutMultiplier = tournament.payoutMultiplier,
        week = tournament.week,
    )

/**
 * Synthetic tournament info block for the season-aggregate report — no
 * specific tournament, multiplier baseline 1, status `null` so the UI
 * can detect "this is a season view, not a tournament view." Name is a
 * static label rather than localized; the UI overrides anyway.
 */
private fun seasonTournamentInfo(): ReportTournamentInfo =
    ReportTournamentInfo(
        id = null,
        name = "All Tournaments",
        startDate = null,
        endDate = null,
        status = null,
        payoutMultiplier = BigDecimal.ONE,
        week = null,
    )

/**
 * Pure: assemble the season-aggregate report. Each team column shows
 * cumulative season earnings (`weeklyTotal` is the season total for
 * the column; `previous` is zero because there's nothing prior to
 * "all of it"). Cells use [buildSeasonCells] which renders `topTens`
 * counts ("3x") in place of a position string.
 */
internal fun assembleSeasonReport(inputs: SeasonReportInputs): WeeklyReport {
    val golferMap = inputs.allGolfers.associateBy { it.id }
    val numTeams = inputs.teams.size
    val cumulativeByTeamGolfer =
        inputs.allScores.groupBy { it.teamId to it.golferId }
            .mapValues { (_, scores) -> scores.sumPoints() to scores.size }
    val topTensByTeam = inputs.allScores.groupBy { it.teamId }.mapValues { (_, scores) -> scores.sumPoints() }
    val topTenCountByTeam = inputs.allScores.groupBy { it.teamId }.mapValues { (_, scores) -> scores.size }
    val totalPot = topTensByTeam.values.fold(BigDecimal.ZERO, BigDecimal::add)

    val sideBetPerRound =
        buildSideBetPerRound(inputs.rules, inputs.allRosters, inputs.allScores, numTeams, inputs.rules.sideBetAmount)
    val sideBetResults = aggregateSideBets(sideBetPerRound)

    val teamColumns =
        inputs.teams.map { team ->
            val roster =
                inputs.allRosters.filter { it.teamId == team.id }.sortedBy { it.draftRound ?: Int.MAX_VALUE }
            val cells = buildSeasonCells(roster, golferMap, cumulativeByTeamGolfer, team.id)
            val teamTopTenEarnings = topTensByTeam[team.id] ?: BigDecimal.ZERO
            val weeklyTotal = teamTopTenEarnings.multiply(BigDecimal(numTeams)).subtract(totalPot)
            val sideBetTotal = sideBetResults[team.id] ?: BigDecimal.ZERO
            ReportTeamColumn(
                teamId = team.id,
                teamName = team.teamName,
                ownerName = team.ownerName,
                cells = cells,
                topTenEarnings = teamTopTenEarnings,
                weeklyTotal = weeklyTotal,
                previous = BigDecimal.ZERO,
                subtotal = weeklyTotal,
                topTenCount = topTenCountByTeam[team.id] ?: 0,
                topTenMoney = teamTopTenEarnings,
                sideBets = sideBetTotal,
                totalCash = weeklyTotal.add(sideBetTotal),
            )
        }

    val rosteredGolferIds = inputs.allRosters.map { it.golferId }.toSet()
    val undraftedAgg =
        buildUndraftedAgg(inputs.allResults, inputs.completed, rosteredGolferIds, golferMap, inputs.rules)
    val sideBetDetail = buildSideBetDetail(sideBetPerRound, inputs.teams, inputs.allRosters, golferMap)

    return WeeklyReport(
        tournament = seasonTournamentInfo(),
        teams = teamColumns,
        undraftedTopTens = undraftedAgg,
        sideBetDetail = sideBetDetail,
        standingsOrder = buildStandingsOrder(teamColumns),
    )
}

private fun buildSeasonCells(
    roster: List<RosterEntry>,
    golferMap: Map<GolferId, Golfer>,
    cumulative: Map<Pair<TeamId, GolferId>, Pair<BigDecimal, Int>>,
    teamId: TeamId,
): List<ReportCell> =
    (1..ROUNDS_PER_REPORT).map { round ->
        val entry = roster.firstOrNull { it.draftRound == round } ?: return@map emptyCell(round)
        val (earnings, topTens) = cumulative[teamId to entry.golferId] ?: (BigDecimal.ZERO to 0)
        val golferName = golferMap[entry.golferId]?.lastName?.uppercase() ?: "?"
        val positionStr = if (topTens > 0) "${topTens}x" else null
        ReportCell(
            round = round,
            golferName = golferName,
            golferId = entry.golferId,
            positionStr = positionStr,
            scoreToPar = null,
            earnings = earnings,
            topTens = topTens,
            ownershipPct = entry.ownershipPct,
            seasonEarnings = earnings,
            seasonTopTens = topTens,
        )
    }

/**
 * Aggregate undrafted top-10 finishes across the season. One row per
 * golfer regardless of how many tournaments they finished top-10 in;
 * `payout` sums every qualifying finish's tieSplitPayout. Sorted by
 * total payout descending so the most-missed names surface first.
 */
internal fun buildUndraftedAgg(
    allResults: List<TournamentResult>,
    completed: List<Tournament>,
    rosteredGolferIds: Set<GolferId>,
    golferMap: Map<GolferId, Golfer>,
    rules: SeasonRules,
): List<UndraftedGolfer> {
    val resultsByTournament = allResults.groupBy { it.tournamentId }
    val tournamentById = completed.associateBy { it.id }
    return allResults
        .filter { result ->
            val position = result.position ?: return@filter false
            position <= TOP_TEN_CUTOFF && result.golferId !in rosteredGolferIds
        }
        .groupBy { it.golferId }
        .map { (golferId, results) ->
            val golfer = golferMap[golferId]
            val name = golfer?.let { "${it.firstName.firstOrNull() ?: '?'}. ${it.lastName}" } ?: "?"
            val totalPayout =
                results.fold(BigDecimal.ZERO) { acc, result ->
                    val tournament = tournamentById[result.tournamentId]
                    val multiplier = tournament?.payoutMultiplier ?: BigDecimal.ONE
                    val isTeamEvent = tournament?.isTeamEvent ?: false
                    val tournamentResults = resultsByTournament[result.tournamentId].orEmpty()
                    val numTied = tournamentResults.count { it.position == result.position }
                    acc.add(
                        PayoutTable.tieSplitPayout(
                            position = result.position ?: NEVER_PAID_POSITION,
                            numTied = numTied,
                            multiplier = multiplier,
                            rules = rules,
                            isTeamEvent = isTeamEvent,
                        ),
                    )
                }
            UndraftedGolfer(name = name, position = null, payout = totalPayout)
        }
        .sortedByDescending { it.payout }
}

/**
 * Cumulative-by-tournament running total per team, returned as one
 * snapshot map per included tournament in chronological order. Used by
 * [getRankings] to render the line-chart series. Each snapshot is the
 * sum of all weekly +/- through that tournament inclusive.
 */
internal fun buildCumulativeHistory(
    sortedTournaments: List<Tournament>,
    allScores: List<FantasyScore>,
    teams: List<Team>,
): List<Map<TeamId, BigDecimal>> {
    val numTeams = teams.size
    val initial = teams.associate { it.id to BigDecimal.ZERO }
    return sortedTournaments
        .runningFold(initial) { cumulative, tournament ->
            val tournamentScores = allScores.filter { it.tournamentId == tournament.id }
            val teamTopTens =
                tournamentScores.groupBy { it.teamId }.mapValues { (_, scores) -> scores.sumPoints() }
            val totalPot = teamTopTens.values.fold(BigDecimal.ZERO, BigDecimal::add)
            teams.associate { team ->
                val weeklyTotal =
                    (teamTopTens[team.id] ?: BigDecimal.ZERO).multiply(BigDecimal(numTeams)).subtract(totalPot)
                team.id to (cumulative[team.id] ?: BigDecimal.ZERO).add(weeklyTotal)
            }
        }
        .drop(1)
}

/**
 * Sum the per-team weekly +/- across every prior tournament in
 * chronological order. Each prior tournament is itself zero-sum across
 * teams: a team's weekly = (its top-10 earnings × N) − (sum of all
 * top-10 earnings). Used as the `previous` running total in the report.
 */
internal fun buildPriorWeekly(
    throughTournaments: List<Tournament>,
    throughScores: List<FantasyScore>,
    tournament: Tournament,
    teams: List<Team>,
    numTeams: Int,
): Map<TeamId, BigDecimal> {
    val priorTournaments = throughTournaments.filter { isBefore(it, tournament) }
    val priorTournamentIds = priorTournaments.map { it.id }.toSet()
    val priorScoresByTournament =
        throughScores
            .filter { it.tournamentId in priorTournamentIds }
            .groupBy { it.tournamentId }
    val perTournamentDeltas =
        priorScoresByTournament.values.flatMap { tournamentScores ->
            val teamTopTens =
                tournamentScores.groupBy { it.teamId }.mapValues { (_, scores) -> scores.sumPoints() }
            val totalPot = teamTopTens.values.fold(BigDecimal.ZERO, BigDecimal::add)
            teams.map { team ->
                val earned = teamTopTens[team.id] ?: BigDecimal.ZERO
                team.id to earned.multiply(BigDecimal(numTeams)).subtract(totalPot)
            }
        }
    return perTournamentDeltas
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, deltas) -> deltas.fold(BigDecimal.ZERO, BigDecimal::add) }
}

@Suppress("LongParameterList")
internal fun buildReportTeamColumn(
    team: Team,
    allRosters: List<RosterEntry>,
    golferMap: Map<GolferId, Golfer>,
    resultsByGolfer: Map<GolferId, TournamentResult>,
    scoresByTeamGolfer: Map<Pair<TeamId, GolferId>, FantasyScore>,
    tournamentScores: List<FantasyScore>,
    cumulativeByTeamGolfer: Map<Pair<TeamId, GolferId>, Pair<BigDecimal, Int>>,
    priorWeeklyByTeam: Map<TeamId, BigDecimal>,
    cumulativeTopTenCounts: Map<TeamId, Int>,
    cumulativeTopTenEarnings: Map<TeamId, BigDecimal>,
    sideBetResults: Map<TeamId, BigDecimal>,
    numTiedByPosition: Map<Int, Int>,
    numTeams: Int,
): ReportTeamColumn {
    val roster =
        allRosters
            .filter { it.teamId == team.id }
            .sortedBy { it.draftRound ?: Int.MAX_VALUE }
    val cells =
        buildWeeklyCells(
            roster = roster,
            golferMap = golferMap,
            resultsByGolfer = resultsByGolfer,
            scoresByTeamGolfer = scoresByTeamGolfer,
            cumulativeByTeamGolfer = cumulativeByTeamGolfer,
            numTiedByPosition = numTiedByPosition,
            teamId = team.id,
        )

    val weeklyTopTenEarnings = cells.fold(BigDecimal.ZERO) { acc, cell -> acc.add(cell.earnings) }
    val totalPot =
        tournamentScores.groupBy { it.teamId }
            .values
            .fold(BigDecimal.ZERO) { acc, group -> acc.add(group.sumPoints()) }
    val weeklyTotal = weeklyTopTenEarnings.multiply(BigDecimal(numTeams)).subtract(totalPot)
    val previous = priorWeeklyByTeam[team.id] ?: BigDecimal.ZERO
    val subtotal = previous.add(weeklyTotal)
    val sideBetTotal = sideBetResults[team.id] ?: BigDecimal.ZERO

    return ReportTeamColumn(
        teamId = team.id,
        teamName = team.teamName,
        ownerName = team.ownerName,
        cells = cells,
        topTenEarnings = weeklyTopTenEarnings,
        weeklyTotal = weeklyTotal,
        previous = previous,
        subtotal = subtotal,
        topTenCount = cumulativeTopTenCounts[team.id] ?: 0,
        topTenMoney = cumulativeTopTenEarnings[team.id] ?: BigDecimal.ZERO,
        sideBets = sideBetTotal,
        totalCash = subtotal.add(sideBetTotal),
    )
}

@Suppress("LongParameterList")
private fun buildWeeklyCells(
    roster: List<RosterEntry>,
    golferMap: Map<GolferId, Golfer>,
    resultsByGolfer: Map<GolferId, TournamentResult>,
    scoresByTeamGolfer: Map<Pair<TeamId, GolferId>, FantasyScore>,
    cumulativeByTeamGolfer: Map<Pair<TeamId, GolferId>, Pair<BigDecimal, Int>>,
    numTiedByPosition: Map<Int, Int>,
    teamId: TeamId,
): List<ReportCell> =
    (1..ROUNDS_PER_REPORT).map { round ->
        val entry = roster.firstOrNull { it.draftRound == round }
        if (entry == null) {
            emptyCell(round)
        } else {
            cellForEntry(
                round = round,
                entry = entry,
                teamId = teamId,
                golferMap = golferMap,
                resultsByGolfer = resultsByGolfer,
                scoresByTeamGolfer = scoresByTeamGolfer,
                cumulativeByTeamGolfer = cumulativeByTeamGolfer,
                numTiedByPosition = numTiedByPosition,
            )
        }
    }

@Suppress("LongParameterList")
private fun cellForEntry(
    round: Int,
    entry: RosterEntry,
    teamId: TeamId,
    golferMap: Map<GolferId, Golfer>,
    resultsByGolfer: Map<GolferId, TournamentResult>,
    scoresByTeamGolfer: Map<Pair<TeamId, GolferId>, FantasyScore>,
    cumulativeByTeamGolfer: Map<Pair<TeamId, GolferId>, Pair<BigDecimal, Int>>,
    numTiedByPosition: Map<Int, Int>,
): ReportCell {
    val golferName = golferMap[entry.golferId]?.lastName?.uppercase() ?: "?"
    val result = resultsByGolfer[entry.golferId]
    val score = scoresByTeamGolfer[teamId to entry.golferId]
    val position = result?.position
    val (cumulativeEarnings, cumulativeTopTens) =
        cumulativeByTeamGolfer[teamId to entry.golferId] ?: (BigDecimal.ZERO to 0)
    return ReportCell(
        round = round,
        golferName = golferName,
        golferId = entry.golferId,
        positionStr = position?.let { positionString(it, numTiedByPosition[it] ?: 1) },
        scoreToPar = result?.scoreToPar?.let(::formatScoreToPar),
        earnings = score?.points ?: BigDecimal.ZERO,
        topTens = if (position != null && position <= TOP_TEN_CUTOFF) 1 else 0,
        ownershipPct = entry.ownershipPct,
        seasonEarnings = cumulativeEarnings,
        seasonTopTens = cumulativeTopTens,
        pairKey = result?.pairKey,
    )
}

private fun emptyCell(round: Int): ReportCell =
    ReportCell(
        round = round,
        golferName = null,
        golferId = null,
        positionStr = null,
        scoreToPar = null,
        earnings = BigDecimal.ZERO,
        topTens = 0,
        ownershipPct = BigDecimal(100),
        seasonEarnings = BigDecimal.ZERO,
        seasonTopTens = 0,
    )

/** Render a leaderboard position with a `T` prefix when more than one golfer shares the position. */
private fun positionString(
    position: Int,
    numTied: Int,
): String = if (numTied > 1) "T$position" else position.toString()

@Suppress("LongParameterList")
internal fun buildUndraftedForTournament(
    results: List<TournamentResult>,
    rosteredGolferIds: Set<GolferId>,
    golferMap: Map<GolferId, Golfer>,
    multiplier: BigDecimal,
    rules: SeasonRules,
    isTeamEvent: Boolean,
): List<UndraftedGolfer> =
    results
        .filter { result ->
            val position = result.position ?: return@filter false
            position <= TOP_TEN_CUTOFF && result.golferId !in rosteredGolferIds
        }
        .sortedBy { it.position ?: Int.MAX_VALUE }
        .map { result ->
            val golfer = golferMap[result.golferId]
            val name = golfer?.let { "${it.firstName.firstOrNull() ?: '?'}. ${it.lastName}" } ?: "?"
            val numTied = results.count { it.position == result.position }
            val payout =
                PayoutTable.tieSplitPayout(
                    position = result.position ?: NEVER_PAID_POSITION,
                    numTied = numTied,
                    multiplier = multiplier,
                    rules = rules,
                    isTeamEvent = isTeamEvent,
                )
            UndraftedGolfer(
                name = name,
                position = result.position,
                payout = payout,
                scoreToPar = result.scoreToPar?.let(::formatScoreToPar),
                pairKey = result.pairKey,
            )
        }

/**
 * Snapshot of one side-bet round: the per-team cumulative earnings the
 * winners are picked from, and the per-team payouts for that round.
 * Carries the round number so [buildSideBetDetail] can emit the rounds in
 * the same order [SeasonRules.sideBetRounds] declares them.
 */
internal data class SideBetRoundSnapshot(
    val round: Int,
    val teamCumulativeEarnings: Map<TeamId, BigDecimal>,
    val payouts: Map<TeamId, BigDecimal>,
)

internal fun buildSideBetPerRound(
    rules: SeasonRules,
    allRosters: List<RosterEntry>,
    allScores: List<FantasyScore>,
    numTeams: Int,
    sideBetPerTeam: BigDecimal,
): List<SideBetRoundSnapshot> =
    rules.sideBetRounds.map { round ->
        val roundPicks = allRosters.filter { it.draftRound == round }
        val teamTotals =
            roundPicks.associate { entry ->
                val total =
                    allScores
                        .filter { it.teamId == entry.teamId && it.golferId == entry.golferId }
                        .sumPoints()
                entry.teamId to total
            }
        val payouts = pickSideBetPayouts(teamTotals, numTeams, sideBetPerTeam)
        SideBetRoundSnapshot(round = round, teamCumulativeEarnings = teamTotals, payouts = payouts)
    }

internal fun aggregateSideBets(perRound: List<SideBetRoundSnapshot>): Map<TeamId, BigDecimal> =
    perRound
        .flatMap { snapshot -> snapshot.payouts.entries.map { it.key to it.value } }
        .groupBy({ it.first }, { it.second })
        .mapValues { (_, amounts) -> amounts.fold(BigDecimal.ZERO, BigDecimal::add) }

internal fun buildSideBetDetail(
    perRound: List<SideBetRoundSnapshot>,
    teams: List<Team>,
    allRosters: List<RosterEntry>,
    golferMap: Map<GolferId, Golfer>,
): List<ReportSideBetRound> =
    perRound.map { snapshot ->
        val teamEntries =
            teams.map { team ->
                val entry = allRosters.firstOrNull { it.teamId == team.id && it.draftRound == snapshot.round }
                val golferName = entry?.let { golferMap[it.golferId]?.lastName?.uppercase() } ?: "—"
                ReportSideBetTeamEntry(
                    teamId = team.id,
                    golferName = golferName,
                    cumulativeEarnings = snapshot.teamCumulativeEarnings[team.id] ?: BigDecimal.ZERO,
                    payout = snapshot.payouts[team.id] ?: BigDecimal.ZERO,
                )
            }
        ReportSideBetRound(round = snapshot.round, teams = teamEntries)
    }

private fun List<FantasyScore>.sumPoints(): BigDecimal = fold(BigDecimal.ZERO) { acc, score -> acc.add(score.points) }

private const val ROUNDS_PER_REPORT = 8
private const val TOP_TEN_CUTOFF = 10

// Sentinel position fed to [PayoutTable.tieSplitPayout] when the result has no position —
// past the payout zone so the table returns 0.
private const val NEVER_PAID_POSITION = 99
