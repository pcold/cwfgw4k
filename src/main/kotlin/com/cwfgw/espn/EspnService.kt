package com.cwfgw.espn

import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
import com.cwfgw.scoring.PayoutTable
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamService
import com.cwfgw.tournaments.CreateTournamentResultRequest
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import com.cwfgw.tournaments.UpdateTournamentRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate

private val log = KotlinLogging.logger {}

/**
 * Pulls tournament results from ESPN and writes them into our DB. Composes
 * the [EspnClient] with the existing tournament / golfer / team services —
 * no direct repository access here.
 *
 * Matching strategy per competitor:
 * 1. (Non-team) match by `pga_player_id = competitor.espnId` — strongest signal.
 * 2. Fall back to exact full-name match.
 * 3. Fall back to unique last-name match, preferring a golfer already on one
 *    of this season's rosters when multiple golfers share the last name —
 *    critical for Zurich Classic team-partner rows that carry a synthetic id
 *    and can only be matched by name.
 * 4. Otherwise auto-create a Golfer. Non-team entries keep the ESPN id as
 *    their `pga_player_id`; team-partner rows skip that since the id is
 *    synthetic (`team:X:N`).
 */
class EspnService(
    private val client: EspnClient,
    private val tournamentService: TournamentService,
    private val golferService: GolferService,
    private val teamService: TeamService,
    private val seasonService: SeasonService,
) {
    /**
     * Fetch ESPN's season calendar. Pass-through to the client; surfaced on
     * the service so routes stay service-only and don't pull [EspnClient]
     * into their wiring.
     */
    suspend fun fetchCalendar(): List<EspnCalendarEntry> = client.fetchCalendar()

    /**
     * Dry-run scoring of every ESPN tournament starting on [date] against
     * the league's current rosters. Returns one [EspnLivePreview] per
     * ESPN event — projected per-team payouts + a top-N leaderboard
     * snapshot. Does not write to the DB.
     *
     * The DB tournament for an ESPN event is resolved by exact
     * `pga_tournament_id` match; if no DB row exists we fall through to
     * the ESPN-only defaults (multiplier 1.0). With the calendar-driven
     * uploadSeason flow every operator-managed tournament is linked, so
     * the unlinked branch is essentially defensive.
     */
    suspend fun previewByDate(
        seasonId: SeasonId,
        date: LocalDate,
    ): Result<List<EspnLivePreview>, EspnError> {
        val events = fetchOrFail(date).getOrElse { return Result.Err(it) }
        val rules = seasonService.getRules(seasonId) ?: SeasonRules.defaults()
        val golfers = golferService.list(activeOnly = false, search = null)
        val teams = teamService.listBySeason(seasonId)
        val rosters = teams.flatMap { teamService.getRoster(it.id) }
        val dbTournamentsForDate =
            tournamentService.list(seasonId, status = null).filter { it.startDate == date }
        return Result.Ok(buildLivePreviews(events, golfers, teams, rosters, dbTournamentsForDate, rules))
    }

    suspend fun importByDate(date: LocalDate): Result<EspnImportBatch, EspnError> {
        val events = fetchOrFail(date).getOrElse { return Result.Err(it) }
        val imported = mutableListOf<EspnImport>()
        val unlinked = mutableListOf<UnlinkedEvent>()
        for (event in events.filter { it.completed }) {
            val tournament = tournamentService.findByPgaTournamentId(event.espnId)
            if (tournament == null) {
                unlinked += UnlinkedEvent(espnEventId = event.espnId, espnEventName = event.name)
            } else {
                imported += runImport(tournament, event)
            }
        }
        return Result.Ok(EspnImportBatch(imported = imported, unlinked = unlinked))
    }

    suspend fun importForTournament(tournamentId: TournamentId): Result<EspnImport, EspnError> {
        val tournament =
            tournamentService.get(tournamentId) ?: return Result.Err(EspnError.TournamentNotFound(tournamentId))
        val espnId =
            tournament.pgaTournamentId ?: return Result.Err(EspnError.TournamentNotLinked(tournamentId))
        val events = fetchOrFail(tournament.startDate).getOrElse { return Result.Err(it) }
        val event =
            events.firstOrNull { it.espnId == espnId } ?: return Result.Err(EspnError.EventNotInScoreboard(espnId))
        return Result.Ok(runImport(tournament, event))
    }

    private suspend fun fetchOrFail(date: LocalDate): Result<List<EspnTournament>, EspnError> =
        try {
            Result.Ok(client.fetchScoreboard(date))
        } catch (e: EspnUpstreamException) {
            log.warn(e) { "ESPN scoreboard fetch failed for $date with status ${e.status}" }
            Result.Err(EspnError.UpstreamUnavailable(e.status))
        }

    private suspend fun runImport(
        tournament: Tournament,
        event: EspnTournament,
    ): EspnImport {
        val context = buildMatchingContext(tournament.seasonId)
        val matches = event.competitors.map { matchOrCreate(it, context) }

        val matchedPairs =
            event.competitors
                .zip(matches)
                .mapNotNull { (competitor, match) -> match?.let { competitor to it } }
        val unmatchedNames =
            event.competitors
                .zip(matches)
                .filter { it.second == null }
                .map { it.first.name }
        val createdCount = matches.count { it?.created == true }
        val collisions = detectCollisions(matchedPairs)

        persistResults(tournament.id, matchedPairs)
        syncTournamentFromEvent(tournament, event)

        return EspnImport(
            tournamentId = tournament.id,
            espnEventId = event.espnId,
            espnEventName = event.name,
            completed = event.completed,
            matched = matchedPairs.size,
            created = createdCount,
            unmatched = unmatchedNames,
            collisions = collisions,
        )
    }

    private suspend fun buildMatchingContext(seasonId: SeasonId): MatchingContext {
        val golfers = golferService.list(activeOnly = false, search = null)
        val rosterIds =
            teamService.getRosterView(seasonId)
                .flatMap { it.picks }
                .map { it.golferId }
                .toSet()
        return MatchingContext(golfers = golfers, rosterGolferIds = rosterIds)
    }

    private suspend fun matchOrCreate(
        competitor: EspnCompetitor,
        context: MatchingContext,
    ): GolferMatch? {
        val existing = locateExistingGolfer(competitor, context)
        if (existing != null) return GolferMatch(existing.id, created = false)

        val (firstName, lastName) = splitName(competitor.name) ?: return null
        val created =
            golferService.create(
                CreateGolferRequest(
                    pgaPlayerId = if (competitor.isTeamPartner) null else competitor.espnId,
                    firstName = firstName,
                    lastName = lastName,
                ),
            )
        return GolferMatch(created.id, created = true)
    }

    private suspend fun locateExistingGolfer(
        competitor: EspnCompetitor,
        context: MatchingContext,
    ): Golfer? {
        if (!competitor.isTeamPartner) {
            golferService.findByPgaPlayerId(competitor.espnId)?.let { return it }
        }
        val fullNameMatch =
            context.golfers.singleOrNull { matchesFullName(it, competitor.name) }
        if (fullNameMatch != null) return fullNameMatch

        val lastName = competitor.name.substringAfterLast(' ').takeIf { it.isNotBlank() } ?: return null
        val lastNameMatches = context.golfers.filter { it.lastName.equals(lastName, ignoreCase = true) }
        return when (lastNameMatches.size) {
            0 -> null
            1 -> lastNameMatches.single()
            else -> lastNameMatches.singleOrNull { it.id in context.rosterGolferIds }
        }
    }

    private suspend fun persistResults(
        tournamentId: TournamentId,
        matchedPairs: List<Pair<EspnCompetitor, GolferMatch>>,
    ) {
        val requests =
            matchedPairs.map { (competitor, match) ->
                CreateTournamentResultRequest(
                    golferId = match.golferId,
                    position = competitor.position,
                    scoreToPar = competitor.scoreToPar,
                    totalStrokes = competitor.totalStrokes,
                    round1 = competitor.roundScores.getOrNull(0),
                    round2 = competitor.roundScores.getOrNull(1),
                    round3 = competitor.roundScores.getOrNull(2),
                    round4 = competitor.roundScores.getOrNull(3),
                    madeCut = competitor.madeCut,
                    pairKey = competitor.pairKey,
                )
            }
        if (requests.isNotEmpty()) tournamentService.importResults(tournamentId, requests)
    }

    /**
     * After a scoreboard pass, sync any tournament-level facts ESPN just
     * revealed: the completed flag (so finalize can move on) and the
     * team-event flag (admin uploadSeason can't know this from the
     * calendar alone — partner rows only show up in the scoreboard
     * payload). Updates only when something actually changes.
     */
    private suspend fun syncTournamentFromEvent(
        tournament: Tournament,
        event: EspnTournament,
    ) {
        val statusChange =
            if (event.completed && tournament.status != TournamentStatus.Completed) TournamentStatus.Completed else null
        val teamEventChange = event.isTeamEvent.takeIf { it != tournament.isTeamEvent }
        if (statusChange == null && teamEventChange == null) return
        tournamentService.update(
            tournament.id,
            UpdateTournamentRequest(status = statusChange, isTeamEvent = teamEventChange),
        )
    }

    private fun detectCollisions(matchedPairs: List<Pair<EspnCompetitor, GolferMatch>>): List<String> =
        matchedPairs
            .groupBy { it.second.golferId }
            .filterValues { it.size > 1 }
            .map { (_, pairs) -> pairs.joinToString(", ") { it.first.name } }

    private data class MatchingContext(
        val golfers: List<Golfer>,
        val rosterGolferIds: Set<GolferId>,
    )

    private data class GolferMatch(
        val golferId: GolferId,
        val created: Boolean,
    )
}

private fun matchesFullName(
    golfer: Golfer,
    fullName: String,
): Boolean =
    "${golfer.firstName} ${golfer.lastName}".equals(fullName, ignoreCase = true) ||
        golfer.lastName.equals(fullName, ignoreCase = true)

/**
 * Pure: build live previews from already-loaded DB state and parsed ESPN
 * tournaments. Extracted from [EspnService.previewByDate] so the
 * per-tournament scoring math is unit-testable without touching a DB.
 */
@Suppress("LongParameterList")
internal fun buildLivePreviews(
    events: List<EspnTournament>,
    allGolfers: List<Golfer>,
    teams: List<Team>,
    rosters: List<RosterEntry>,
    dbTournamentsForDate: List<Tournament>,
    rules: SeasonRules,
): List<EspnLivePreview> {
    val context = LivePreviewContext.from(allGolfers, teams, rosters, rules)
    return events.map { event -> buildLivePreviewForEvent(event, context, dbTournamentsForDate) }
}

private fun buildLivePreviewForEvent(
    event: EspnTournament,
    ctx: LivePreviewContext,
    dbTournamentsForDate: List<Tournament>,
): EspnLivePreview {
    val matchedDb = dbTournamentsForDate.firstOrNull { it.pgaTournamentId == event.espnId }
    val multiplier = matchedDb?.payoutMultiplier ?: BigDecimal.ONE
    val isTeamEvent = event.isTeamEvent || matchedDb?.isTeamEvent == true

    val matchedCompetitors: List<Pair<EspnCompetitor, Golfer?>> =
        event.competitors.map { competitor -> competitor to ctx.resolveGolfer(competitor) }
    val tiedCounts = event.competitors.groupingBy { it.position }.eachCount()

    val teamPreviews =
        ctx.teams.map { team ->
            buildTeamPreview(team, matchedCompetitors, tiedCounts, ctx, multiplier, isTeamEvent)
        }
    val numTeams = ctx.teams.size
    val totalPot = teamPreviews.fold(BigDecimal.ZERO) { acc, team -> acc.add(team.topTenEarnings) }
    val teamsWithWeekly =
        teamPreviews.map { team ->
            team.copy(weeklyTotal = team.topTenEarnings.multiply(BigDecimal(numTeams)).subtract(totalPot))
        }

    return EspnLivePreview(
        espnName = event.name,
        espnId = event.espnId,
        completed = event.completed,
        payoutMultiplier = multiplier,
        totalCompetitors = event.competitors.size,
        teams = teamsWithWeekly.sortedByDescending { it.weeklyTotal },
        isTeamEvent = isTeamEvent,
        leaderboard = buildLivePreviewLeaderboard(matchedCompetitors, ctx.teams, ctx.rosters),
    )
}

@Suppress("LongParameterList")
private fun buildTeamPreview(
    team: Team,
    matchedCompetitors: List<Pair<EspnCompetitor, Golfer?>>,
    tiedCounts: Map<Int, Int>,
    ctx: LivePreviewContext,
    multiplier: BigDecimal,
    isTeamEvent: Boolean,
): PreviewTeamScore {
    val rosterByGolfer = ctx.rostersByTeam[team.id].orEmpty().associateBy { it.golferId }
    val golferEarnings =
        matchedCompetitors.mapNotNull { (competitor, golfer) ->
            val resolvedGolfer = golfer ?: return@mapNotNull null
            val entry = rosterByGolfer[resolvedGolfer.id] ?: return@mapNotNull null
            if (competitor.position > ctx.numPlaces) return@mapNotNull null
            val numTied = tiedCounts[competitor.position] ?: 1
            val basePayout =
                PayoutTable.tieSplitPayout(
                    position = competitor.position,
                    numTied = numTied,
                    multiplier = multiplier,
                    rules = ctx.rules,
                    isTeamEvent = isTeamEvent,
                )
            val owners = ctx.golferOwners[resolvedGolfer.id].orEmpty()
            val splits = PayoutTable.splitOwnership(basePayout, owners)
            val ownerPayout = splits[team.id] ?: basePayout
            PreviewGolferScore(
                golferName = "${resolvedGolfer.firstName} ${resolvedGolfer.lastName}",
                golferId = resolvedGolfer.id,
                position = competitor.position,
                numTied = numTied,
                scoreToPar = competitor.scoreToPar,
                basePayout = basePayout,
                ownershipPct = entry.ownershipPct,
                payout = ownerPayout,
            )
        }
    val topTens = golferEarnings.fold(BigDecimal.ZERO) { acc, score -> acc.add(score.payout) }
    return PreviewTeamScore(
        teamId = team.id,
        teamName = team.teamName,
        ownerName = team.ownerName,
        topTenEarnings = topTens,
        golferScores = golferEarnings,
    )
}

/**
 * Pre-derived lookups used by every per-event preview build. Computing
 * these once per [buildLivePreviews] call avoids walking the rosters
 * list for every team and every competitor.
 */
private data class LivePreviewContext(
    val teams: List<Team>,
    val rosters: List<RosterEntry>,
    val rules: SeasonRules,
    val golferByEspnId: Map<String, Golfer>,
    val golferByName: Map<Pair<String, String>, Golfer>,
    val golferByLastName: Map<String, List<Golfer>>,
    val rosteredGolferIds: Set<GolferId>,
    val rostersByTeam: Map<TeamId, List<RosterEntry>>,
    val golferOwners: Map<GolferId, List<Pair<TeamId, BigDecimal>>>,
    val numPlaces: Int,
) {
    fun resolveGolfer(competitor: EspnCompetitor): Golfer? =
        resolveGolfer(competitor, golferByEspnId, golferByName, golferByLastName, rosteredGolferIds)

    companion object {
        fun from(
            allGolfers: List<Golfer>,
            teams: List<Team>,
            rosters: List<RosterEntry>,
            rules: SeasonRules,
        ): LivePreviewContext =
            LivePreviewContext(
                teams = teams,
                rosters = rosters,
                rules = rules,
                golferByEspnId = allGolfers.mapNotNull { golfer -> golfer.pgaPlayerId?.let { it to golfer } }.toMap(),
                golferByName = allGolfers.associateBy { (it.firstName.lowercase() to it.lastName.lowercase()) },
                golferByLastName = allGolfers.groupBy { it.lastName.lowercase() },
                rosteredGolferIds = rosters.map { it.golferId }.toSet(),
                rostersByTeam = rosters.groupBy { it.teamId },
                golferOwners =
                    rosters.groupBy { it.golferId }
                        .mapValues { (_, entries) -> entries.map { it.teamId to it.ownershipPct } },
                numPlaces = rules.payouts.size,
            )
    }
}

@Suppress("LongParameterList")
private fun resolveGolfer(
    competitor: EspnCompetitor,
    golferByEspnId: Map<String, Golfer>,
    golferByName: Map<Pair<String, String>, Golfer>,
    golferByLastName: Map<String, List<Golfer>>,
    rosteredGolferIds: Set<GolferId>,
): Golfer? {
    if (!competitor.isTeamPartner) {
        golferByEspnId[competitor.espnId]?.let { return it }
    }
    val parts = competitor.name.split(WHITESPACE_RUN, limit = 2)
    val (first, last) =
        if (parts.size >= 2) parts[0].lowercase() to parts[1].lowercase() else "" to parts[0].lowercase()
    golferByName[first to last]?.let { return it }
    val byLastName = golferByLastName[last] ?: return null
    return when (byLastName.size) {
        1 -> byLastName.single()
        else ->
            // Team-event partners arrive as last-name-only; with multiple matches, prefer a rostered golfer if
            // exactly one exists. Scoped to partners — regular competitors should match by ESPN id directly.
            if (competitor.isTeamPartner) {
                byLastName.singleOrNull { it.id in rosteredGolferIds }
            } else {
                null
            }
    }
}

private fun buildLivePreviewLeaderboard(
    matchedCompetitors: List<Pair<EspnCompetitor, Golfer?>>,
    teams: List<Team>,
    rosters: List<RosterEntry>,
): List<PreviewLeaderboardEntry> {
    val rosterByGolfer = rosters.associateBy { it.golferId }
    val teamsById = teams.associateBy { it.id }
    return matchedCompetitors
        .sortedBy { (competitor, _) -> competitor.position }
        .map { (competitor, golfer) ->
            val rosterEntry = golfer?.let { rosterByGolfer[it.id] }
            PreviewLeaderboardEntry(
                name = competitor.name,
                position = competitor.position,
                scoreToPar = competitor.scoreToPar,
                thru = competitor.roundScores.takeIf { it.isNotEmpty() }?.let { "${it.size} rounds" },
                rostered = rosterEntry != null,
                teamName = rosterEntry?.let { teamsById[it.teamId]?.teamName },
                pairKey = competitor.pairKey,
                roundScores = competitor.roundScores,
                totalStrokes = competitor.totalStrokes,
            )
        }
}

private val WHITESPACE_RUN = Regex("\\s+")

/**
 * Split an ESPN display name into (firstName, lastName). Single-word names
 * (e.g. a team-partner row with just "Novak") become last-name-only with an
 * empty first name; the domain lets that through and admins can clean up
 * later. Returns null for an empty/blank name.
 */
private fun splitName(fullName: String): Pair<String, String>? {
    val trimmed = fullName.trim()
    if (trimmed.isEmpty()) return null
    val firstSpace = trimmed.indexOf(' ')
    return if (firstSpace < 0) {
        "" to trimmed
    } else {
        trimmed.substring(0, firstSpace) to trimmed.substring(firstSpace + 1).trim()
    }
}
