package com.cwfgw.reports

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.PayoutTable
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournaments.ESPN_SCHEDULE_ZONE
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentStatus
import com.cwfgw.tournaments.isLiveOverlayCandidate
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Builds the operator-facing weekly report — the 13-column × 8-round grid
 * the league has used for years, plus the surrounding totals (weekly +/-,
 * standings, side-bet detail, undrafted top-10s). The non-live variant
 * snapshots whatever's currently in the DB; the live overlay merges
 * in-progress ESPN scoreboard data on top.
 *
 * Each public read method pre-loads its inputs inside one
 * `tx.read { … }` block before running the pure assembly + optional live
 * overlay. That's a deliberate departure from the usual "compose
 * services" shape — calling another service mid-gather would let each
 * sub-call open its own pool checkout, which is exactly the per-request
 * connection-borrow churn the May 10 cold-start wedge surfaced. Reading
 * everything from repositories under one `tx.read` collapses that to one
 * Hikari validation per request and gives Postgres-MVCC snapshot
 * consistency across the gathered data for free. The pattern mirrors
 * [com.cwfgw.scoring.ScoringService] / [com.cwfgw.admin.AdminService].
 */
@Suppress("LongParameterList")
class WeeklyReportService(
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
    private val golferRepository: GolferRepository,
    private val scoringRepository: ScoringRepository,
    private val liveOverlayService: LiveOverlayService,
    private val tx: Transactor,
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
        val today = LocalDate.now(ESPN_SCHEDULE_ZONE)
        val prepared = gatherReport(seasonId, tournamentId).getOrElse { return Result.Err(it) }
        if (!live || !prepared.tournament.isLiveOverlayCandidate(today)) return Result.Ok(prepared.baseReport)

        val priorNonCompleted =
            prepared.allTournaments.filter { candidate ->
                candidate.isLiveOverlayCandidate(today) &&
                    candidate.id != tournamentId &&
                    isBefore(candidate, prepared.tournament)
            }
        return Result.Ok(
            liveOverlayService.overlayReport(
                seasonId = seasonId,
                baseReport = prepared.baseReport,
                rules = prepared.rules,
                priorNonCompleted = priorNonCompleted,
                selectedTournament = prepared.tournament,
                tournamentId = tournamentId,
            ),
        )
    }

    private suspend fun gatherReport(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): Result<ReportPrepared, ReportError> =
        tx.read {
            seasonRepository.findById(seasonId)
                ?: return@read Result.Err(ReportError.SeasonNotFound(seasonId))
            val tournament =
                tournamentRepository.findById(tournamentId)
                    ?: return@read Result.Err(ReportError.TournamentNotFound(tournamentId))
            val rules = seasonRepository.getRules(seasonId) ?: SeasonRules.defaults()
            val teams = teamRepository.findBySeason(seasonId)
            val results = tournamentRepository.getResults(tournamentId)
            val allGolfers = golferRepository.findAll(activeOnly = false, search = null)
            val scores = scoringRepository.getScores(seasonId, tournamentId)
            val allTournaments = tournamentRepository.findAll(seasonId = seasonId, status = null)
            val allCompletedTournaments = allTournaments.filter { it.status == TournamentStatus.Completed }
            val allRosters = teamRepository.findRostersBySeason(seasonId)
            val allScores =
                scoringRepository.getScoresBySeason(seasonId, allCompletedTournaments.map { it.id })
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
            Result.Ok(
                ReportPrepared(
                    baseReport = assembleWeeklyReport(inputs),
                    tournament = tournament,
                    rules = rules,
                    allTournaments = allTournaments,
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
        val today = LocalDate.now(ESPN_SCHEDULE_ZONE)
        val gathered: Result<SeasonReportPrepared, ReportError> =
            tx.read {
                seasonRepository.findById(seasonId)
                    ?: return@read Result.Err(ReportError.SeasonNotFound(seasonId))
                val rules = seasonRepository.getRules(seasonId) ?: SeasonRules.defaults()
                val teams = teamRepository.findBySeason(seasonId)
                val allGolfers = golferRepository.findAll(activeOnly = false, search = null)
                val allTournaments = tournamentRepository.findAll(seasonId = seasonId, status = null)
                val completed = allTournaments.filter { it.status == TournamentStatus.Completed }
                val completedIds = completed.map { it.id }
                val allRosters = teamRepository.findRostersBySeason(seasonId)
                val allScores = scoringRepository.getScoresBySeason(seasonId, completedIds)
                val allResults = tournamentRepository.getResultsByTournaments(completedIds)
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
                Result.Ok(
                    SeasonReportPrepared(
                        baseReport = assembleSeasonReport(inputs),
                        rules = rules,
                        allTournaments = allTournaments,
                    ),
                )
            }
        val prepared = gathered.getOrElse { return Result.Err(it) }
        if (!live) return Result.Ok(prepared.baseReport)

        val nonCompleted = prepared.allTournaments.filter { it.isLiveOverlayCandidate(today) }
        return Result.Ok(
            liveOverlayService.overlaySeasonReport(seasonId, prepared.baseReport, prepared.rules, nonCompleted),
        )
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
        val today = LocalDate.now(ESPN_SCHEDULE_ZONE)
        val prepared =
            gatherRankings(seasonId, throughTournamentId).getOrElse { return Result.Err(it) }
        if (!live) return Result.Ok(prepared.baseRankings)

        val liveCandidates = liveCandidatesFor(prepared.allTournaments, today, prepared.through)
        return Result.Ok(
            liveOverlayService.overlayRankings(
                seasonId,
                prepared.baseRankings,
                liveCandidates,
                prepared.rankingsContext,
            ),
        )
    }

    private suspend fun gatherRankings(
        seasonId: SeasonId,
        throughTournamentId: TournamentId?,
    ): Result<RankingsPrepared, ReportError> =
        tx.read {
            seasonRepository.findById(seasonId)
                ?: return@read Result.Err(ReportError.SeasonNotFound(seasonId))
            val through =
                throughTournamentId?.let { id ->
                    tournamentRepository.findById(id)
                        ?: return@read Result.Err(ReportError.TournamentNotFound(id))
                }
            val rules = seasonRepository.getRules(seasonId) ?: SeasonRules.defaults()
            val teams = teamRepository.findBySeason(seasonId)
            val allTournaments = tournamentRepository.findAll(seasonId = seasonId, status = null)
            val completed = allTournaments.filter { it.status == TournamentStatus.Completed }
            val included = filterThroughTournament(completed, through)
            val allRosters = teamRepository.findRostersBySeason(seasonId)
            val allScores = scoringRepository.getScoresBySeason(seasonId, included.map { it.id })
            val sideBetPerRound =
                buildSideBetPerRound(rules, allRosters, allScores, teams.size, rules.sideBetAmount)
            val baseRankings = buildBaseRankings(teams, included, allScores, sideBetPerRound)
            Result.Ok(
                RankingsPrepared(
                    baseRankings = baseRankings,
                    allTournaments = allTournaments,
                    through = through,
                    rankingsContext =
                        RankingsContext(
                            allRosters = allRosters,
                            rules = rules,
                            sideBetPerRound = sideBetPerRound,
                            numTeams = teams.size,
                        ),
                ),
            )
        }

    private fun buildBaseRankings(
        teams: List<Team>,
        included: List<Tournament>,
        allScores: List<FantasyScore>,
        sideBetPerRound: List<SideBetRoundSnapshot>,
    ): Rankings {
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
                    series =
                        history.map { snapshot ->
                            (snapshot[team.id] ?: BigDecimal.ZERO).add(teamSideBets)
                        },
                )
            }.sortedByDescending { it.totalCash }
        return Rankings(
            teams = baseTeams,
            weeks = sortedIncluded.map { it.week ?: "" },
            tournamentNames = sortedIncluded.map { it.name },
        )
    }

    /**
     * Per-golfer rankings across the season — replaces the legacy UI
     * flow that fetched one [getReport] per tournament and aggregated
     * client-side. Loads all the same data that flow needed (rosters,
     * scores, results, golfers) once, walks it in pure code, and
     * returns one response. Live overlay folds ESPN previews in via
     * [LiveOverlayService.overlayPlayerRankings] so non-finalized
     * tournaments contribute projected earnings to the totals.
     */
    suspend fun getPlayerRankings(
        seasonId: SeasonId,
        throughTournamentId: TournamentId? = null,
        live: Boolean = false,
    ): Result<PlayerRankings, ReportError> {
        val today = LocalDate.now(ESPN_SCHEDULE_ZONE)
        val prepared =
            gatherPlayerRankings(seasonId, throughTournamentId).getOrElse { return Result.Err(it) }

        val finalAcc =
            if (!live) {
                prepared.baseAcc
            } else {
                liveOverlayService.overlayPlayerRankings(
                    seasonId = seasonId,
                    base = prepared.baseAcc,
                    candidates = liveCandidatesFor(prepared.allTournaments, today, prepared.through),
                    rules = prepared.rules,
                )
            }

        return Result.Ok(composePlayerRankings(finalAcc, prepared.rosterIndex, prepared.golferMap, live = live))
    }

    private suspend fun gatherPlayerRankings(
        seasonId: SeasonId,
        throughTournamentId: TournamentId?,
    ): Result<PlayerRankingsPrepared, ReportError> =
        tx.read {
            seasonRepository.findById(seasonId)
                ?: return@read Result.Err(ReportError.SeasonNotFound(seasonId))
            val through =
                throughTournamentId?.let { id ->
                    tournamentRepository.findById(id)
                        ?: return@read Result.Err(ReportError.TournamentNotFound(id))
                }
            val rules = seasonRepository.getRules(seasonId) ?: SeasonRules.defaults()
            val teams = teamRepository.findBySeason(seasonId)
            val allGolfers = golferRepository.findAll(activeOnly = false, search = null)
            val golferMap = allGolfers.associateBy { it.id }
            val allTournaments = tournamentRepository.findAll(seasonId = seasonId, status = null)
            val completed = allTournaments.filter { it.status == TournamentStatus.Completed }
            val included = filterThroughTournament(completed, through)
            val includedIds = included.map { it.id }
            val allRosters = teamRepository.findRostersBySeason(seasonId)
            val rosteredGolferIds = allRosters.map { it.golferId }.toSet()
            val allScores = scoringRepository.getScoresBySeason(seasonId, includedIds)
            val allResults = tournamentRepository.getResultsByTournaments(includedIds)
            val baseAcc =
                buildPlayerRankingsAcc(
                    allScores = allScores,
                    allResults = allResults,
                    tournamentsById = included.associateBy { it.id },
                    rosteredGolferIds = rosteredGolferIds,
                    golferMap = golferMap,
                    rules = rules,
                )
            Result.Ok(
                PlayerRankingsPrepared(
                    baseAcc = baseAcc,
                    allTournaments = allTournaments,
                    through = through,
                    rules = rules,
                    rosterIndex = buildRosterIndex(teams, allRosters, golferMap),
                    golferMap = golferMap,
                ),
            )
        }

    /**
     * Live candidates for a rankings overlay: every non-completed
     * tournament strictly before the cutoff, plus the cutoff itself if
     * it's also non-completed. Sorted chronologically so the overlay
     * folds them in the order they'd actually play out. Pure — operates
     * on the already-loaded tournament list inside the request, so it
     * doesn't reopen a transaction.
     */
    private fun liveCandidatesFor(
        allTournaments: List<Tournament>,
        today: LocalDate,
        through: Tournament?,
    ): List<Tournament> {
        val liveCandidates = allTournaments.filter { it.isLiveOverlayCandidate(today) }
        val candidates =
            if (through == null) {
                liveCandidates
            } else {
                val priorLiveCandidates = liveCandidates.filter { isBefore(it, through) }
                val selectedIfLive =
                    if (through.isLiveOverlayCandidate(today)) listOf(through) else emptyList()
                priorLiveCandidates + selectedIfLive
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
    ): Result<GolferHistory, ReportError> =
        tx.read {
            seasonRepository.findById(seasonId)
                ?: return@read Result.Err(ReportError.SeasonNotFound(seasonId))
            val golfer =
                golferRepository.findById(golferId)
                    ?: return@read Result.Err(ReportError.GolferNotFound(golferId))
            val rules = seasonRepository.getRules(seasonId) ?: SeasonRules.defaults()
            val completed =
                tournamentRepository
                    .findAll(seasonId = seasonId, status = TournamentStatus.Completed)
            val resultsByTournamentId =
                tournamentRepository.getResultsByTournaments(completed.map { it.id })
                    .groupBy { it.tournamentId }
            val byTournament =
                completed.associateWith { resultsByTournamentId[it.id] ?: emptyList() }

            val entries =
                byTournament
                    .mapNotNull { (tournament, results) ->
                        val mine =
                            results.firstOrNull { result ->
                                result.golferId == golferId &&
                                    (result.position ?: Int.MAX_VALUE) <= TOP_TEN_CUTOFF
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

            Result.Ok(
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
 * Output of each public method's `tx.read` gather block — the base
 * assembled result plus whatever the live-overlay decision and call
 * need afterwards. Holding `allTournaments` (rather than just
 * `liveCandidates`) lets the overlay path apply its own date filter
 * client-side without reopening the transaction, and reuses the same
 * single SQL query for both the completed-only and live-candidate
 * views inside the gather block.
 */
private data class ReportPrepared(
    val baseReport: WeeklyReport,
    val tournament: Tournament,
    val rules: SeasonRules,
    val allTournaments: List<Tournament>,
)

private data class SeasonReportPrepared(
    val baseReport: WeeklyReport,
    val rules: SeasonRules,
    val allTournaments: List<Tournament>,
)

private data class RankingsPrepared(
    val baseRankings: Rankings,
    val allTournaments: List<Tournament>,
    val through: Tournament?,
    val rankingsContext: RankingsContext,
)

private data class PlayerRankingsPrepared(
    val baseAcc: PlayerRankingsAcc,
    val allTournaments: List<Tournament>,
    val through: Tournament?,
    val rules: SeasonRules,
    val rosterIndex: Map<GolferId, PlayerRosterInfo>,
    val golferMap: Map<GolferId, Golfer>,
)

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

// ----- Player rankings assembly -----

/**
 * Per-golfer roster context used to populate `teamName` / `draftRound`
 * on drafted [PlayerRankingsRow] entries. If a golfer is rostered by
 * multiple teams (rare but legal — drafted across multiple fantasy
 * teams), the LAST team encountered wins. Order is whatever the
 * caller produces; in practice [WeeklyReportService.getPlayerRankings]
 * passes them in `teamService.listBySeason` order which is stable
 * across requests.
 */
internal data class PlayerRosterInfo(
    val teamName: String,
    val draftRound: Int,
    val fullName: String,
)

internal fun buildRosterIndex(
    teams: List<Team>,
    allRosters: List<RosterEntry>,
    golferMap: Map<GolferId, Golfer>,
): Map<GolferId, PlayerRosterInfo> {
    val teamNameById = teams.associate { it.id to it.teamName }
    return allRosters
        .mapNotNull { entry -> rosterIndexEntry(entry, teamNameById, golferMap) }
        .toMap()
}

private fun rosterIndexEntry(
    entry: RosterEntry,
    teamNameById: Map<TeamId, String>,
    golferMap: Map<GolferId, Golfer>,
): Pair<GolferId, PlayerRosterInfo>? {
    val teamName = teamNameById[entry.teamId] ?: return null
    val golfer = golferMap[entry.golferId] ?: return null
    val draftRound = entry.draftRound ?: return null
    return entry.golferId to
        PlayerRosterInfo(
            teamName = teamName,
            draftRound = draftRound,
            fullName = "${golfer.firstName} ${golfer.lastName}",
        )
}

/**
 * Build the base accumulator from completed-tournament data:
 *  - Drafted rows come from [FantasyScore]: each row is one (team,
 *    golfer, tournament) finish where the golfer top-10'd. We sum
 *    ownership-adjusted `points` and count rows so a golfer rostered
 *    by N teams who top-10s once contributes N to `topTens` and the
 *    sum of their ownership-adjusted slices to earnings — matching
 *    the legacy UI semantic where each cell counted independently.
 *  - Undrafted rows come from [TournamentResult]: top-10 finishes by
 *    golfers no team rostered, with payouts computed via the same
 *    [PayoutTable.tieSplitPayout] helper [buildUndraftedAgg] uses for
 *    per-tournament reports. Keyed by display name because the
 *    matching ESPN-leaderboard path doesn't carry golferId.
 */
@Suppress("LongParameterList")
internal fun buildPlayerRankingsAcc(
    allScores: List<FantasyScore>,
    allResults: List<TournamentResult>,
    tournamentsById: Map<TournamentId, Tournament>,
    rosteredGolferIds: Set<GolferId>,
    golferMap: Map<GolferId, Golfer>,
    rules: SeasonRules,
): PlayerRankingsAcc {
    val drafted = mutableMapOf<GolferId, DraftedAgg>()
    for (score in allScores) {
        val existing = drafted[score.golferId] ?: DraftedAgg(topTens = 0, totalEarnings = BigDecimal.ZERO)
        drafted[score.golferId] =
            existing.copy(
                topTens = existing.topTens + 1,
                totalEarnings = existing.totalEarnings.add(score.points),
            )
    }

    val resultsByTournament = allResults.groupBy { it.tournamentId }
    val undrafted = mutableMapOf<String, UndraftedAgg>()
    allResults.forEach { result ->
        undraftedRowFromResult(result, tournamentsById, rosteredGolferIds, golferMap, resultsByTournament, rules)
            ?.let { (name, payout) ->
                val existing = undrafted[name] ?: UndraftedAgg(topTens = 0, totalEarnings = BigDecimal.ZERO)
                undrafted[name] =
                    existing.copy(
                        topTens = existing.topTens + 1,
                        totalEarnings = existing.totalEarnings.add(payout),
                    )
            }
    }

    return PlayerRankingsAcc(drafted = drafted.toMap(), undrafted = undrafted.toMap())
}

/**
 * Compute the (display name, payout) pair contributed by one
 * [TournamentResult], or null when it doesn't qualify (no position,
 * outside the top-10, rostered, or missing the lookup data). Pulled
 * out of [buildPlayerRankingsAcc] so the loop body is a one-liner and
 * detekt sees a single early-exit per branch instead of a stack of
 * `continue`s.
 */
@Suppress("LongParameterList")
private fun undraftedRowFromResult(
    result: TournamentResult,
    tournamentsById: Map<TournamentId, Tournament>,
    rosteredGolferIds: Set<GolferId>,
    golferMap: Map<GolferId, Golfer>,
    resultsByTournament: Map<TournamentId, List<TournamentResult>>,
    rules: SeasonRules,
): Pair<String, BigDecimal>? {
    val position = result.position ?: return null
    if (position > TOP_TEN_CUTOFF) return null
    if (result.golferId in rosteredGolferIds) return null
    val tournament = tournamentsById[result.tournamentId] ?: return null
    val golfer = golferMap[result.golferId] ?: return null
    val numTied = resultsByTournament[result.tournamentId].orEmpty().count { it.position == position }
    val payout =
        PayoutTable.tieSplitPayout(
            position = position,
            numTied = numTied,
            multiplier = tournament.payoutMultiplier,
            rules = rules,
            isTeamEvent = tournament.isTeamEvent,
        )
    val name = "${golfer.firstName.firstOrNull() ?: '?'}. ${golfer.lastName}"
    return name to payout
}

internal fun composePlayerRankings(
    acc: PlayerRankingsAcc,
    rosterIndex: Map<GolferId, PlayerRosterInfo>,
    golferMap: Map<GolferId, Golfer>,
    live: Boolean,
): PlayerRankings {
    val draftedRows =
        acc.drafted.entries.map { (golferId, agg) ->
            val roster = rosterIndex[golferId]
            val golfer = golferMap[golferId]
            PlayerRankingsRow(
                key = "g:${golferId.value}",
                golferId = golferId,
                name = roster?.fullName ?: golfer?.let { "${it.firstName} ${it.lastName}" } ?: "?",
                topTens = agg.topTens,
                totalEarnings = agg.totalEarnings,
                teamName = roster?.teamName,
                draftRound = roster?.draftRound,
            )
        }
    val undraftedRows =
        acc.undrafted.entries.map { (name, agg) ->
            PlayerRankingsRow(
                key = "u:$name",
                golferId = null,
                name = name,
                topTens = agg.topTens,
                totalEarnings = agg.totalEarnings,
            )
        }
    val sorted =
        (draftedRows + undraftedRows).sortedWith(
            compareByDescending<PlayerRankingsRow> { it.totalEarnings }
                .thenByDescending { it.topTens }
                .thenBy { it.name },
        )
    return PlayerRankings(players = sorted, live = live)
}
