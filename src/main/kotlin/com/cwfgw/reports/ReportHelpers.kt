package com.cwfgw.reports

import com.cwfgw.teams.TeamId
import com.cwfgw.tournaments.Tournament
import java.math.BigDecimal

// Pure helpers shared between weekly-report assembly and live overlay.
// Lives in its own file so unit tests can hammer it without spinning up a
// service. Anything that needs a DB or ESPN call belongs in the service
// layer; helpers here are total functions of their inputs.

/**
 * Tournament ordering used everywhere a chronological sort is needed:
 * primarily start date, with `week` as the tiebreaker so same-date
 * multi-events (e.g. 8A / 8B) land in a stable, predictable order.
 * `week` is nullable; tournaments without one sort as less than those
 * with — fine in practice because pre-week numbering is the historical
 * default.
 */
val tournamentOrdering: Comparator<Tournament> =
    compareBy<Tournament> { it.startDate }.thenBy { it.week }

/** True when `a` strictly precedes `b` in tournament order. */
fun isBefore(
    a: Tournament,
    b: Tournament,
): Boolean = tournamentOrdering.compare(a, b) < 0

/** True when `a` precedes or equals `b` in tournament order. */
fun isOnOrBefore(
    a: Tournament,
    b: Tournament,
): Boolean = tournamentOrdering.compare(a, b) <= 0

/**
 * Format a score-to-par integer for display: `"E"` for even, `"+5"` for
 * over par, `"-3"` for under. Centralized so every column on the report
 * formats the same way (the UI relies on string equality to highlight
 * leaders).
 */
fun formatScoreToPar(scoreToPar: Int): String =
    when {
        scoreToPar == 0 -> "E"
        scoreToPar > 0 -> "+$scoreToPar"
        else -> scoreToPar.toString()
    }

/**
 * Standings panel rows, sorted by `totalCash` descending and assigned a
 * 1-based rank. Ties produce sequential ranks (no joint-rank logic) —
 * matches the existing UI which prints whatever order the API returns.
 */
fun buildStandingsOrder(teams: List<ReportTeamColumn>): List<StandingsEntry> =
    teams.sortedByDescending { it.totalCash }
        .mapIndexed { index, team ->
            StandingsEntry(rank = index + 1, teamName = team.teamName, totalCash = team.totalCash)
        }

/**
 * Pick side-bet payouts from current cumulative earnings per team. All-
 * zero (start of season) returns empty. Otherwise the team(s) at the
 * highest cumulative split the pot — losers each pay `sideBetPerTeam`,
 * winners share the pool. Tie detection uses `compareTo` because
 * `BigDecimal.equals` is scale-sensitive (10 != 10.00) and the same
 * cumulative arrives at different scales depending on the source path.
 */
fun pickSideBetPayouts(
    cumulativeByTeam: Map<TeamId, BigDecimal>,
    numTeams: Int,
    sideBetPerTeam: BigDecimal,
): Map<TeamId, BigDecimal> {
    if (cumulativeByTeam.isEmpty() || cumulativeByTeam.values.all { it.signum() == 0 }) {
        return emptyMap()
    }
    val highest = cumulativeByTeam.values.max()
    val winners = cumulativeByTeam.filterValues { it.compareTo(highest) == 0 }.keys
    val winnerCount = winners.size
    val winnerCollects =
        sideBetPerTeam.multiply(BigDecimal(numTeams - winnerCount)).divide(BigDecimal(winnerCount))
    return cumulativeByTeam.mapValues { (teamId, _) ->
        if (teamId in winners) winnerCollects else sideBetPerTeam.negate()
    }
}

/** [pickSideBetPayouts] over a list of [ReportSideBetTeamEntry]; updates each entry's `payout`. */
fun recomputeSideBetPayouts(
    entries: List<ReportSideBetTeamEntry>,
    numTeams: Int,
    sideBetPerTeam: BigDecimal,
): List<ReportSideBetTeamEntry> {
    val payouts =
        pickSideBetPayouts(entries.associate { it.teamId to it.cumulativeEarnings }, numTeams, sideBetPerTeam)
    return entries.map { it.copy(payout = payouts[it.teamId] ?: BigDecimal.ZERO) }
}

/**
 * Trim a list of completed tournaments to those at or before a cutoff.
 * `null` cutoff returns the input unchanged; used by rankings so a
 * viewer can replay standings as of any prior week.
 */
fun filterThroughTournament(
    completed: List<Tournament>,
    through: Tournament?,
): List<Tournament> = if (through == null) completed else completed.filter { isOnOrBefore(it, through) }
