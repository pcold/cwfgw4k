package com.cwfgw.scoring

import com.cwfgw.db.TransactionContext
import com.cwfgw.golfers.GolferId
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentResult
import java.math.BigDecimal

/**
 * Depends on repositories directly, never on other services: a service call mid-flow opens its own
 * transaction on another connection, splitting the gate check, gather and write that [calculateScoresIn]
 * and [refreshStandingsIn] run in one. See `src/CLAUDE.md` → Cross-repo flows.
 */
class ScoringService(
    private val repository: ScoringRepository,
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val teamRepository: TeamRepository,
) {
    /**
     * Takes the caller's transaction rather than opening one, so finalize and reset commit or roll back
     * these writes together with the rest of their flow.
     */
    context(ctx: TransactionContext)
    internal fun calculateScoresIn(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): Result<WeeklyScoreResult, ScoringError> {
        seasonRepository.findById(seasonId) ?: return Result.Err(ScoringError.SeasonNotFound)
        val tournament =
            tournamentRepository.findById(tournamentId)
                ?: return Result.Err(ScoringError.TournamentNotFound)
        val rules = seasonRepository.getRules(seasonId) ?: SeasonRules.defaults()
        val results = tournamentRepository.getResults(tournamentId)
        val teams = teamRepository.findBySeason(seasonId)
        val rostersByTeam = teamRepository.findRostersBySeason(seasonId).groupBy { it.teamId }
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

    /** @see calculateScoresIn */
    context(ctx: TransactionContext)
    internal fun refreshStandingsIn(seasonId: SeasonId): Result<List<SeasonStanding>, ScoringError> {
        seasonRepository.findById(seasonId) ?: return Result.Err(ScoringError.SeasonNotFound)
        val teams = teamRepository.findBySeason(seasonId)
        val standings =
            teams.map { team ->
                val totals = repository.teamSeasonTotals(seasonId, team.id)
                repository.upsertStanding(seasonId, team.id, totals.totalPoints, totals.tournamentsPlayed)
            }
        return Result.Ok(standings)
    }

    context(ctx: TransactionContext)
    private fun scoreGolferForTeam(
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
        val floor = inputs.rules.tieFloor.multiply(inputs.multiplier)
        val ownerPayout = PayoutTable.splitOwnership(basePayout, owners, floor)[teamId] ?: basePayout
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

    private fun ownersByGolfer(allRosters: List<RosterEntry>): Map<GolferId, List<Pair<TeamId, BigDecimal>>> =
        allRosters
            .groupBy { it.golferId }
            .mapValues { (_, entries) -> entries.map { it.teamId to it.ownershipPct } }

    private fun sumPayouts(scores: List<GolferScoreEntry>): BigDecimal =
        scores.fold(BigDecimal.ZERO) { acc, entry -> acc.add(entry.payout) }
}

/**
 * Per-call inputs for [ScoringService.calculateScoresIn]. Bundles the data
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
