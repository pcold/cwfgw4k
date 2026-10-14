package com.cwfgw.espn

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
import com.cwfgw.scoring.PayoutTable
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournamentLinks.TournamentCompetitorListing
import com.cwfgw.tournamentLinks.TournamentCompetitorView
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.CreateTournamentResultRequest
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentStatus
import com.cwfgw.tournaments.UpdateTournamentRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val log = KotlinLogging.logger {}

/**
 * A named competitor [locateExistingGolferIn] can't match becomes a new golfer. A team-partner row gets no
 * `pga_player_id`, because its ESPN id is synthetic (`team:X:N`).
 */
@Suppress("LongParameterList")
class EspnService(
    private val client: EspnClient,
    private val seasonRepository: SeasonRepository,
    private val golferRepository: GolferRepository,
    private val teamRepository: TeamRepository,
    private val tournamentRepository: TournamentRepository,
    private val linkRepository: TournamentLinkRepository,
    private val tx: Transactor,
) {
    /** Surfaced here so callers depend on this service and never take an [EspnClient] themselves. */
    suspend fun fetchCalendar(): List<EspnCalendarEntry> = client.fetchCalendar()

    /**
     * Build a deduped pool of currently-active golfers by unioning the
     * competitor lists from the most recent [recentEventCount] PGA
     * scoreboards. Used by the roster matcher to discover players that
     * haven't been seen in our `golfers` table yet — see
     * [com.cwfgw.admin.AdminService.previewRoster] for the persist-and-
     * rematch flow.
     *
     * Calendar parse failures and individual scoreboard fetch failures are
     * absorbed (logged as warnings, contributing zero athletes) so a single
     * upstream hiccup doesn't black out the entire pool.
     */
    suspend fun fetchActivePlayers(recentEventCount: Int = DEFAULT_RECENT_EVENT_COUNT): List<EspnAthlete> {
        val calendar =
            try {
                client.fetchCalendar()
            } catch (e: EspnUpstreamException) {
                log.warn(e) { "ESPN calendar fetch failed during athlete-pool build" }
                return emptyList()
            }
        val today = LocalDate.now()
        val recentDates =
            calendar.mapNotNull { entry -> parseEspnDate(entry.startDate) }
                .filter { !it.isAfter(today) }
                .sortedDescending()
                .take(recentEventCount)
        log.info { "Fetching ESPN athletes from ${recentDates.size} recent events" }
        return recentDates
            .flatMap { date -> fetchScoreboardOrEmpty(date).flatMap(EspnTournament::competitors) }
            .filterNot { it.isTeamPartner }
            .map { EspnAthlete(espnId = it.espnId, name = it.name) }
            .distinctBy { it.espnId }
    }

    private suspend fun fetchScoreboardOrEmpty(date: LocalDate): List<EspnTournament> =
        try {
            client.fetchScoreboard(date)
        } catch (e: EspnUpstreamException) {
            log.warn(e) { "ESPN scoreboard fetch failed for $date during athlete-pool build" }
            emptyList()
        }

    private fun parseEspnDate(raw: String): LocalDate? =
        try {
            LocalDate.parse(raw.take(ISO_DATE_LENGTH))
        } catch (e: DateTimeParseException) {
            log.warn(e) { "ESPN sent an unparseable startDate for athlete pool: '$raw'" }
            null
        }

    /**
     * Called once per overlay and reused across its fold, so the gather is not paid per candidate.
     * [candidates] must hold every tournament later handed to [previewForTournament]: one left out
     * previews without its link overrides.
     */
    internal suspend fun loadLivePreviewContext(
        seasonId: SeasonId,
        candidates: List<Tournament>,
    ): LivePreviewContext =
        tx.read {
            LivePreviewContext.from(
                allGolfers = golferRepository.findAll(search = null),
                teams = teamRepository.findBySeason(seasonId),
                rosters = teamRepository.findRostersBySeason(seasonId),
                rules = seasonRepository.getRules(seasonId) ?: SeasonRules.defaults(),
                overridesByTournament =
                    candidates.associate { candidate ->
                        candidate.id to
                            linkRepository.listByTournament(candidate.id)
                                .associate { it.espnCompetitorId to it.golferId }
                    },
            )
        }

    /**
     * Must stay DB-free and at one scoreboard request, keyed by start date as the client's cache is: the
     * overlay calls this once per candidate inside its fold, with [ctx] already loaded.
     */
    internal suspend fun previewForTournament(
        ctx: LivePreviewContext,
        tournament: Tournament,
    ): Result<EspnLivePreview, LivePreviewError> {
        val events =
            fetchEspnEvents(tournament.startDate).getOrElse { return Result.Err(LivePreviewError.EspnUnavailable(it)) }
        val event = matchEvent(events, tournament) ?: return Result.Err(LivePreviewError.NoMatchingEvent)
        return Result.Ok(buildLivePreviewForEvent(event, tournament, ctx))
    }

    /**
     * Network-only ESPN scoreboard fetch — wraps the upstream client and
     * maps the typed exception to [EspnError]. Exposed to orchestration
     * services (e.g. [com.cwfgw.tournaments.TournamentOpsService]) that
     * need to separate the network call from the persistence step so the
     * latter can join an outer `tx.update` without holding a Postgres
     * connection across an HTTP round trip (CWF-25).
     */
    suspend fun fetchEspnEvents(date: LocalDate): Result<List<EspnTournament>, EspnError.UpstreamUnavailable> =
        try {
            Result.Ok(client.fetchScoreboard(date))
        } catch (e: EspnUpstreamException) {
            log.warn(e) { "ESPN scoreboard fetch failed for $date: ${e.message}" }
            Result.Err(EspnError.UpstreamUnavailable(e.failure))
        }

    /**
     * Dry-run "what would each ESPN competitor link to right now?" for the
     * admin link-management UI. Fetches the live scoreboard, runs the
     * override-aware matcher, and returns one row per competitor with the
     * resolved [Golfer] (if any) plus a flag indicating whether the
     * resolution came from a manual override.
     *
     * Does not persist anything. The auto-create behavior of the import
     * path is intentionally skipped — unmatched competitors return
     * `linkedGolfer = null` so the admin can pick one explicitly.
     */
    suspend fun listCompetitorsForLinking(tournamentId: TournamentId): Result<TournamentCompetitorListing, EspnError> {
        val tournament =
            tx.get { tournamentRepository.findById(tournamentId) }
                ?: return Result.Err(EspnError.TournamentNotFound(tournamentId))
        val espnId =
            tournament.pgaTournamentId ?: return Result.Err(EspnError.TournamentNotLinked(tournamentId))
        val events = fetchEspnEvents(tournament.startDate).getOrElse { return Result.Err(it) }
        val event =
            events.firstOrNull { it.espnId == espnId } ?: return Result.Err(EspnError.EventNotInScoreboard(espnId))

        val views =
            tx.read {
                val context = buildMatchingContextIn(tournament.seasonId, tournamentId)
                event.competitors.map { competitor ->
                    TournamentCompetitorView(
                        espnCompetitorId = competitor.espnId,
                        name = competitor.name,
                        position = competitor.position,
                        isTeamPartner = competitor.isTeamPartner,
                        linkedGolfer = locateExistingGolferIn(competitor, context),
                        hasOverride = competitor.espnId in context.overrides,
                    )
                }
            }
        return Result.Ok(
            TournamentCompetitorListing(
                tournamentId = tournamentId,
                isFinalized = tournament.status == TournamentStatus.Completed,
                competitors = views,
            ),
        )
    }

    /**
     * Writes into the caller's transaction so finalize persists the import and the scores atomically.
     */
    context(ctx: TransactionContext)
    internal fun persistImportIn(
        tournament: Tournament,
        event: EspnTournament,
    ) {
        val matchingContext = buildMatchingContextIn(tournament.seasonId, tournament.id)
        val matchedPairs =
            event.competitors.mapNotNull { competitor ->
                matchOrCreateIn(competitor, matchingContext)?.let { golferId -> competitor to golferId }
            }
        persistResultsIn(tournament.id, matchedPairs)
        syncTournamentFromEventIn(tournament, event)
    }

    context(ctx: TransactionContext)
    private fun buildMatchingContextIn(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): MatchingContext {
        val golfers = golferRepository.findAll(search = null)
        val rosterIds =
            teamRepository.getRosterView(seasonId)
                .flatMap { it.picks }
                .map { it.golferId }
                .toSet()
        val overrides =
            linkRepository.listByTournament(tournamentId)
                .associate { it.espnCompetitorId to it.golferId }
        return MatchingContext(golfers = golfers, rosterGolferIds = rosterIds, overrides = overrides)
    }

    context(ctx: TransactionContext)
    private fun matchOrCreateIn(
        competitor: EspnCompetitor,
        context: MatchingContext,
    ): GolferId? {
        val existing = locateExistingGolferIn(competitor, context)
        if (existing != null) return existing.id

        val (firstName, lastName) = splitName(competitor.name) ?: return null
        val created =
            golferRepository.create(
                CreateGolferRequest(
                    pgaPlayerId = if (competitor.isTeamPartner) null else competitor.espnId,
                    firstName = firstName,
                    lastName = lastName,
                ),
            )
        return created.id
    }

    context(ctx: TransactionContext)
    private fun locateExistingGolferIn(
        competitor: EspnCompetitor,
        context: MatchingContext,
    ): Golfer? {
        // Manual override wins. Lets an admin disambiguate ESPN partner rows
        // (last-name only) when multiple rostered golfers share the surname.
        context.overrides[competitor.espnId]?.let { golferId ->
            golferRepository.findById(golferId)?.let { return it }
        }
        if (!competitor.isTeamPartner) {
            golferRepository.findByPgaPlayerId(competitor.espnId)?.let { return it }
        }
        val fullNameMatch =
            context.golfers.singleOrNull { matchesFullName(it, competitor.name) }
        if (fullNameMatch != null) return fullNameMatch

        val lastName = competitor.name.substringAfterLast(' ').takeIf { it.isNotBlank() } ?: return null
        val lastNameMatches = context.golfers.filter { it.lastName.equals(lastName, ignoreCase = true) }
        return when (lastNameMatches.size) {
            0 -> null
            1 -> lastNameMatches.single()
            else ->
                // Team-event partners arrive as last-name-only, so preferring the rostered golfer on
                // ambiguity is the only way to resolve them. Regular competitors carry a full name and a
                // stable ESPN id; guessing on surname alone risks silently attaching an unrelated golfer's
                // result to an existing rostered golfer who happens to share the surname (CWF-35 — an
                // unfamiliar co-sanctioned-event player named "Lee" collided into a rostered "Lee").
                if (competitor.isTeamPartner) {
                    lastNameMatches.singleOrNull { it.id in context.rosterGolferIds }
                } else {
                    null
                }
        }
    }

    context(ctx: TransactionContext)
    private fun persistResultsIn(
        tournamentId: TournamentId,
        matchedPairs: List<Pair<EspnCompetitor, GolferId>>,
    ) {
        val requests =
            matchedPairs.map { (competitor, golferId) ->
                CreateTournamentResultRequest(
                    golferId = golferId,
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
        requests.forEach { tournamentRepository.upsertResult(tournamentId, it) }
    }

    /**
     * After a scoreboard pass, sync any tournament-level facts ESPN just
     * revealed: the completed flag (so finalize can move on) and the
     * team-event flag (admin confirmSeasonSchedule can't know this from
     * the calendar alone — partner rows only show up in the scoreboard
     * payload). Updates only when something actually changes.
     */
    context(ctx: TransactionContext)
    private fun syncTournamentFromEventIn(
        tournament: Tournament,
        event: EspnTournament,
    ) {
        val statusChange =
            if (event.completed && tournament.status != TournamentStatus.Completed) TournamentStatus.Completed else null
        val teamEventChange = event.isTeamEvent.takeIf { it != tournament.isTeamEvent }
        if (statusChange == null && teamEventChange == null) return
        tournamentRepository.update(
            tournament.id,
            UpdateTournamentRequest(status = statusChange, isTeamEvent = teamEventChange),
        )
    }

    private data class MatchingContext(
        val golfers: List<Golfer>,
        val rosterGolferIds: Set<GolferId>,
        val overrides: Map<String, GolferId>,
    )

    companion object {
        // Past PGA events folded into the active-player pool. Six events covers
        // ~5 weeks of touring (with one off week) — enough for a typical roster.
        private const val DEFAULT_RECENT_EVENT_COUNT: Int = 6

        // Length of the ISO date prefix `yyyy-MM-dd` extracted from ESPN's
        // startDate strings (which include a time/zone suffix).
        private const val ISO_DATE_LENGTH: Int = 10
    }
}

private fun matchesFullName(
    golfer: Golfer,
    fullName: String,
): Boolean =
    "${golfer.firstName} ${golfer.lastName}".equals(fullName, ignoreCase = true) ||
        golfer.lastName.equals(fullName, ignoreCase = true)

/**
 * Kept apart from [EspnError] so the overlay's `when` is exhaustive over what a preview can actually
 * answer: [NoMatchingEvent] is routine and skipped silently, [EspnUnavailable] is logged.
 */
internal sealed interface LivePreviewError {
    /** @see LivePreviewError */
    data object NoMatchingEvent : LivePreviewError

    /** @see LivePreviewError */
    data class EspnUnavailable(val cause: EspnError.UpstreamUnavailable) : LivePreviewError
}

/**
 * The lone-event fallback covers a tournament whose `pga_tournament_id` is unset or stale. It must stay
 * "exactly one": with several events on the date, taking any of them overlays the wrong tournament.
 */
private fun matchEvent(
    events: List<EspnTournament>,
    tournament: Tournament,
): EspnTournament? = events.firstOrNull { it.espnId == tournament.pgaTournamentId } ?: events.singleOrNull()

/**
 * Multiplier, team flag and overrides come from [tournament], never a lookup by [event]'s id: the lone-event
 * fallback matches without that id, so the lookup would miss and preview at single payout.
 */
internal fun buildLivePreviewForEvent(
    event: EspnTournament,
    tournament: Tournament,
    ctx: LivePreviewContext,
): EspnLivePreview {
    val multiplier = tournament.payoutMultiplier
    val isTeamEvent = event.isTeamEvent || tournament.isTeamEvent

    val matchedCompetitors: List<Pair<EspnCompetitor, Golfer?>> =
        event.competitors.map { competitor -> competitor to ctx.resolveGolfer(competitor, tournament.id) }
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
            val floor = ctx.rules.tieFloor.multiply(multiplier)
            val splits = PayoutTable.splitOwnership(basePayout, owners, floor)
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
 * these once per [EspnService.loadLivePreviewContext] avoids walking the
 * rosters list for every team and every competitor.
 *
 * Internal because [LiveOverlayService] holds one across its fold so a
 * multi-event overlay only pays for the season-scoped gather once
 * (CWF-18). Callers outside `com.cwfgw.espn` / `com.cwfgw.reports` must
 * not depend on the field layout.
 */
@Suppress("LongParameterList")
internal data class LivePreviewContext(
    val teams: List<Team>,
    val rosters: List<RosterEntry>,
    val rules: SeasonRules,
    val golferById: Map<GolferId, Golfer>,
    val golferByEspnId: Map<String, Golfer>,
    val golferByName: Map<Pair<String, String>, Golfer>,
    val golferByLastName: Map<String, List<Golfer>>,
    val rosteredGolferIds: Set<GolferId>,
    val rostersByTeam: Map<TeamId, List<RosterEntry>>,
    val golferOwners: Map<GolferId, List<Pair<TeamId, BigDecimal>>>,
    val numPlaces: Int,
    val overridesByTournament: Map<TournamentId, Map<String, GolferId>>,
) {
    fun resolveGolfer(
        competitor: EspnCompetitor,
        tournamentId: TournamentId,
    ): Golfer? {
        val override = overridesByTournament[tournamentId]?.get(competitor.espnId)
        if (override != null) golferById[override]?.let { return it }
        return resolveGolfer(competitor, golferByEspnId, golferByName, golferByLastName, rosteredGolferIds)
    }

    companion object {
        fun from(
            allGolfers: List<Golfer>,
            teams: List<Team>,
            rosters: List<RosterEntry>,
            rules: SeasonRules,
            overridesByTournament: Map<TournamentId, Map<String, GolferId>>,
        ): LivePreviewContext =
            LivePreviewContext(
                teams = teams,
                rosters = rosters,
                rules = rules,
                golferById = allGolfers.associateBy { it.id },
                golferByEspnId = allGolfers.mapNotNull { golfer -> golfer.pgaPlayerId?.let { it to golfer } }.toMap(),
                golferByName = allGolfers.associateBy { (it.firstName.lowercase() to it.lastName.lowercase()) },
                golferByLastName = allGolfers.groupBy { it.lastName.lowercase() },
                rosteredGolferIds = rosters.map { it.golferId }.toSet(),
                rostersByTeam = rosters.groupBy { it.teamId },
                golferOwners =
                    rosters.groupBy { it.golferId }
                        .mapValues { (_, entries) -> entries.map { it.teamId to it.ownershipPct } },
                numPlaces = rules.payouts.size,
                overridesByTournament = overridesByTournament,
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
    val nameByTeam = teams.associate { it.id to it.teamName }
    val teamNamesByGolfer =
        rosters
            .groupBy { it.golferId }
            .mapValues { (_, entries) -> entries.mapNotNull { nameByTeam[it.teamId] } }
    return matchedCompetitors
        .sortedBy { (competitor, _) -> competitor.position }
        .map { (competitor, golfer) ->
            PreviewLeaderboardEntry(
                name = competitor.name,
                position = competitor.position,
                scoreToPar = competitor.scoreToPar,
                thru = competitor.roundScores.takeIf { it.isNotEmpty() }?.let { "${it.size} rounds" },
                teamNames = golfer?.let { teamNamesByGolfer[it.id] }.orEmpty(),
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
