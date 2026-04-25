package com.cwfgw.scoring

import com.cwfgw.golfers.GolferId
import com.cwfgw.result.Result
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
import java.math.BigDecimal

/**
 * Computes weekly fantasy scoring, season standings, and draft-round side-bet
 * standings.
 *
 * `getScores` and `getStandings` are simple lookups and return a list directly.
 * The compound operations ([calculateScores], [getSideBetStandings],
 * [refreshStandings]) return [Result] so each multi-mode failure (season
 * missing, tournament missing, no teams) can drive its own HTTP status at the
 * route boundary.
 */
class ScoringService(
    private val repository: ScoringRepository,
    private val seasonService: SeasonService,
    private val tournamentService: TournamentService,
    private val teamService: TeamService,
) {
    suspend fun getScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): List<FantasyScore> = repository.getScores(seasonId, tournamentId)

    suspend fun getStandings(seasonId: SeasonId): List<SeasonStanding> = repository.getStandings(seasonId)

    suspend fun deleteScoresByTournament(tournamentId: TournamentId): Int = repository.deleteByTournament(tournamentId)

    suspend fun deleteScoresBySeason(seasonId: SeasonId): Int = repository.deleteBySeason(seasonId)

    suspend fun deleteStandingsBySeason(seasonId: SeasonId): Int = repository.deleteStandingsBySeason(seasonId)

    suspend fun calculateScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): Result<WeeklyScoreResult, ScoringError> {
        seasonService.get(seasonId) ?: return Result.Err(ScoringError.SeasonNotFound)
        val tournament = tournamentService.get(tournamentId) ?: return Result.Err(ScoringError.TournamentNotFound)
        val rules = seasonService.getRules(seasonId) ?: SeasonRules.defaults()
        val results = tournamentService.getResults(tournamentId)
        val teams = teamService.listBySeason(seasonId)
        val rostersByTeam = teams.associate { it.id to teamService.getRoster(it.id) }
        val inputs =
            ScoringInputs(
                seasonId = seasonId,
                tournamentId = tournamentId,
                rules = rules,
                multiplier = tournament.payoutMultiplier,
                isTeamEvent = tournament.isTeamEvent,
                results = results,
                ownersByGolfer = ownersByGolfer(rostersByTeam.values.flatten()),
            )

        val perTeamResults =
            teams.map { team ->
                val roster = rostersByTeam[team.id] ?: emptyList()
                val golferScores = roster.mapNotNull { entry -> scoreGolferForTeam(inputs, team.id, entry) }
                team to golferScores
            }
        return Result.Ok(buildWeeklyResult(tournament, perTeamResults))
    }

    suspend fun refreshStandings(seasonId: SeasonId): Result<List<SeasonStanding>, ScoringError> {
        seasonService.get(seasonId) ?: return Result.Err(ScoringError.SeasonNotFound)
        val teams = teamService.listBySeason(seasonId)
        val standings =
            teams.map { team ->
                val totals = repository.teamSeasonTotals(seasonId, team.id)
                repository.upsertStanding(seasonId, team.id, totals.totalPoints, totals.tournamentsPlayed)
            }
        return Result.Ok(standings)
    }

    suspend fun getSideBetStandings(seasonId: SeasonId): Result<SideBetStandings, ScoringError> {
        seasonService.get(seasonId) ?: return Result.Err(ScoringError.SeasonNotFound)
        val teams = teamService.listBySeason(seasonId)
        if (teams.isEmpty()) return Result.Err(ScoringError.NoTeams)
        val rules = seasonService.getRules(seasonId) ?: SeasonRules.defaults()
        val context =
            SideBetContext(
                seasonId = seasonId,
                teamNames = teams.associate { it.id to it.teamName },
                numTeams = teams.size,
                sideBetAmount = rules.sideBetAmount,
                rosters = teams.flatMap { teamService.getRoster(it.id) },
            )
        val rounds = rules.sideBetRounds.map { round -> buildSideBetRound(context, round) }
        val totals = teamSideBetTotals(teams, rounds, rules.sideBetAmount)
        return Result.Ok(SideBetStandings(rounds = rounds, teamTotals = totals))
    }

    private suspend fun scoreGolferForTeam(
        inputs: ScoringInputs,
        teamId: TeamId,
        entry: RosterEntry,
    ): GolferScoreEntry? {
        val result = inputs.resultsByGolfer[entry.golferId] ?: return null
        val position = result.position ?: return null
        if (position > inputs.rules.payouts.size) return null
        val numTied = inputs.results.count { it.position == position }
        val basePayout =
            PayoutTable.tieSplitPayout(
                position = position,
                numTied = numTied,
                multiplier = inputs.multiplier,
                rules = inputs.rules,
                isTeamEvent = inputs.isTeamEvent,
            )
        val owners = inputs.ownersByGolfer[entry.golferId] ?: emptyList()
        val ownerPayout = PayoutTable.splitOwnership(basePayout, owners)[teamId] ?: basePayout
        val breakdown =
            ScoreBreakdown(
                position = position,
                numTied = numTied,
                basePayout = basePayout,
                ownershipPct = entry.ownershipPct,
                payout = ownerPayout,
                multiplier = inputs.multiplier,
            )
        repository.upsertScore(
            UpsertScore(
                seasonId = inputs.seasonId,
                teamId = teamId,
                tournamentId = inputs.tournamentId,
                golferId = entry.golferId,
                breakdown = breakdown,
            ),
        )
        return GolferScoreEntry(golferId = entry.golferId, payout = ownerPayout, breakdown = breakdown)
    }

    private fun buildWeeklyResult(
        tournament: Tournament,
        perTeamResults: List<Pair<Team, List<GolferScoreEntry>>>,
    ): WeeklyScoreResult {
        val numTeams = perTeamResults.size
        val teamTotals = perTeamResults.map { (team, scores) -> Triple(team, sumPayouts(scores), scores) }
        val totalPot = teamTotals.fold(BigDecimal.ZERO) { acc, triple -> acc.add(triple.second) }
        val teamResults =
            teamTotals.map { (team, topTens, scores) ->
                TeamWeeklyResult(
                    teamId = team.id,
                    teamName = team.teamName,
                    topTens = topTens,
                    weeklyTotal = topTens.multiply(BigDecimal(numTeams)).subtract(totalPot),
                    golferScores = scores,
                )
            }
        return WeeklyScoreResult(
            tournamentId = tournament.id,
            multiplier = tournament.payoutMultiplier,
            numTeams = numTeams,
            totalPot = totalPot,
            teams = teamResults,
        )
    }

    private suspend fun buildSideBetRound(
        context: SideBetContext,
        round: Int,
    ): SideBetRound {
        val roundPicks = context.rosters.filter { it.draftRound == round }
        val sortedEntries =
            roundPicks
                .map { entry ->
                    Triple(
                        entry.teamId,
                        entry.golferId,
                        repository.golferPointTotal(context.seasonId, entry.teamId, entry.golferId),
                    )
                }
                .sortedByDescending { it.third }

        val winnerTriple = sortedEntries.firstOrNull()?.takeIf { it.third > BigDecimal.ZERO }
        val winner =
            winnerTriple?.let { (teamId, golferId, total) ->
                SideBetWinner(
                    teamId = teamId,
                    teamName = context.teamNames[teamId] ?: "",
                    golferId = golferId,
                    cumulativeEarnings = total,
                    netWinnings = context.sideBetAmount.multiply(BigDecimal(context.numTeams - 1)),
                )
            }
        val entries =
            sortedEntries.map { (teamId, golferId, total) ->
                SideBetEntry(
                    teamId = teamId,
                    teamName = context.teamNames[teamId] ?: "",
                    golferId = golferId,
                    cumulativeEarnings = total,
                )
            }
        return SideBetRound(round = round, active = winner != null, winner = winner, entries = entries)
    }

    private fun teamSideBetTotals(
        teams: List<Team>,
        rounds: List<SideBetRound>,
        sideBetAmount: BigDecimal,
    ): List<SideBetTeamTotal> {
        val numTeams = teams.size
        val activeBets = rounds.count { it.active }
        val winnersByRound = rounds.mapNotNull { it.winner?.teamId }
        return teams.map { team ->
            val wins = winnersByRound.count { it == team.id }
            val losses = activeBets - wins
            val net =
                sideBetAmount
                    .multiply(BigDecimal(numTeams - 1))
                    .multiply(BigDecimal(wins))
                    .subtract(sideBetAmount.multiply(BigDecimal(losses)))
            SideBetTeamTotal(teamId = team.id, teamName = team.teamName, wins = wins, net = net)
        }
    }

    private fun ownersByGolfer(allRosters: List<RosterEntry>): Map<GolferId, List<Pair<TeamId, BigDecimal>>> =
        allRosters
            .groupBy { it.golferId }
            .mapValues { (_, entries) -> entries.map { it.teamId to it.ownershipPct } }

    private fun sumPayouts(scores: List<GolferScoreEntry>): BigDecimal =
        scores.fold(BigDecimal.ZERO) { acc, entry -> acc.add(entry.payout) }
}

/**
 * Per-call inputs for [ScoringService.calculateScores]. Bundles the data
 * needed to score every golfer for every team in a single tournament so the
 * inner loop reads as a small function instead of a long parameter list.
 */
private data class ScoringInputs(
    val seasonId: SeasonId,
    val tournamentId: TournamentId,
    val rules: SeasonRules,
    val multiplier: BigDecimal,
    val isTeamEvent: Boolean,
    val results: List<TournamentResult>,
    val ownersByGolfer: Map<GolferId, List<Pair<TeamId, BigDecimal>>>,
) {
    val resultsByGolfer: Map<GolferId, TournamentResult> = results.associateBy { it.golferId }
}

/** Per-call inputs for [ScoringService.getSideBetStandings]. */
private data class SideBetContext(
    val seasonId: SeasonId,
    val teamNames: Map<TeamId, String>,
    val numTeams: Int,
    val sideBetAmount: BigDecimal,
    val rosters: List<RosterEntry>,
)
