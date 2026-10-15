package com.cwfgw.espn

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferName
import com.cwfgw.golfers.GolferNameIndex
import com.cwfgw.golfers.GolferNameMatch
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
     * The athletes a roster may name before our `golfers` table has them, drawn from the last
     * [recentEventCount] scoreboards; [com.cwfgw.admin.AdminService.confirmRoster] inserts the ones picked.
     * A failed calendar parse or scoreboard fetch is logged and contributes no athletes, so one upstream
     * hiccup doesn't black out the pool.
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
            val overrides =
                linkRepository.listByTournaments(candidates.map { it.id })
                    .groupBy({ it.tournamentId }, { it.espnCompetitorId to it.golferId })
            LivePreviewContext.from(
                allGolfers = golferRepository.findAll(search = null),
                teams = teamRepository.findBySeason(seasonId),
                rosters = teamRepository.findRostersBySeason(seasonId),
                rules = seasonRepository.getRules(seasonId) ?: SeasonRules.defaults(),
                overridesByTournament = candidates.associate { it.id to overrides[it.id].orEmpty().toMap() },
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
        val event = findLinkedEvent(events, tournament) ?: return Result.Err(LivePreviewError.NoMatchingEvent)
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
        val event = findLinkedEvent(events, tournament) ?: return Result.Err(EspnError.EventNotInScoreboard(espnId))

        val views =
            tx.read {
                val context = buildMatchingContextIn(tournament.seasonId, tournamentId)
                event.competitors.map { competitor ->
                    TournamentCompetitorView(
                        espnCompetitorId = competitor.espnId,
                        name = competitor.name,
                        position = competitor.position,
                        isTeamPartner = competitor.isTeamPartner,
                        linkedGolfer = locateExistingGolferIn(competitor, context).creditedGolfer,
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
    context(ctx: WriteTransactionContext)
    internal fun persistImportIn(
        tournament: Tournament,
        event: EspnTournament,
    ) {
        val matchingContext = buildMatchingContextIn(tournament.seasonId, tournament.id)
        val matchedPairs = matchOrCreateIn(event.competitors, matchingContext)
        persistResultsIn(tournament.id, matchedPairs)
        syncTournamentFromEventIn(tournament, event)
    }

    context(ctx: TransactionContext)
    private fun buildMatchingContextIn(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): MatchingContext {
        val names = GolferNameIndex(golferRepository.findAll(search = null))
        val rosterIds =
            teamRepository.getRosterView(seasonId)
                .flatMap { it.picks }
                .map { it.golferId }
                .toSet()
        val overrides =
            linkRepository.listByTournament(tournamentId)
                .associate { it.espnCompetitorId to it.golferId }
        return MatchingContext(names = names, rosterGolferIds = rosterIds, overrides = overrides)
    }

    context(ctx: WriteTransactionContext)
    private fun matchOrCreateIn(
        competitors: List<EspnCompetitor>,
        context: MatchingContext,
    ): List<Pair<EspnCompetitor, GolferId>> {
        val leaderboard =
            competitors.withIndex().associateWith { (_, competitor) -> locateExistingGolferIn(competitor, context) }
        // Confirm inserts and backfills in this order too: a conflicting write waits on the other transaction's
        // row, so leaderboard order would let an import and a confirm that share athletes deadlock.
        val bound =
            leaderboard.entries
                .sortedBy { (entry, _) -> entry.value.espnId }
                .associate { (entry, match) -> entry to bindGolferIn(entry.value, match) }
        return leaderboard.keys.mapNotNull { entry -> bound[entry]?.let { golferId -> entry.value to golferId } }
    }

    context(ctx: WriteTransactionContext)
    private fun bindGolferIn(
        competitor: EspnCompetitor,
        match: CompetitorMatch,
    ): GolferId? =
        when (match) {
            is CompetitorMatch.Credited -> match.golfer.id
            is CompetitorMatch.Idless -> backfillIn(competitor, match.golfer)
            CompetitorMatch.Unmatched -> createGolferIn(competitor)
            is CompetitorMatch.IdMismatch -> {
                val espnId = LoggedEspnId(competitor.espnId)
                log.warn {
                    "cwfgw4k.espn event=namesake-id-mismatch espn_id=$espnId golfer_id=${match.namesake.id.value}"
                }
                createGolferIn(competitor)
            }
            is CompetitorMatch.Withheld -> {
                val espnId = LoggedEspnId(competitor.espnId)
                val golferIds = match.drafted.map { it.id.value }
                log.warn { "cwfgw4k.espn event=namesake-withheld espn_id=$espnId golfer_ids=$golferIds" }
                null
            }
        }

    // Null means another writer, concurrent or earlier in this import, gave the row an id or this competitor's id to
    // another row; findOrCreate then returns that holder, or inserts beside a row that turned out to be a namesake.
    context(ctx: WriteTransactionContext)
    private fun backfillIn(
        competitor: EspnCompetitor,
        row: Golfer,
    ): GolferId? = golferRepository.backfillPgaPlayerId(row.id, competitor.espnId)?.id ?: createGolferIn(competitor)

    context(ctx: WriteTransactionContext)
    private fun createGolferIn(competitor: EspnCompetitor): GolferId? {
        val (firstName, lastName) = GolferName.splitName(competitor.name) ?: return null
        val created =
            golferRepository.findOrCreate(
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
    ): CompetitorMatch {
        // Manual override wins. Lets an admin disambiguate ESPN partner rows
        // (last-name only) when multiple rostered golfers share the surname.
        val overridden = context.overrides[competitor.espnId]?.let { golferId -> golferRepository.findById(golferId) }
        if (overridden != null) return CompetitorMatch.Credited(overridden)
        val byEspnId = if (competitor.isTeamPartner) null else golferRepository.findByPgaPlayerId(competitor.espnId)
        if (byEspnId != null) return CompetitorMatch.Credited(byEspnId)
        return resolveByName(competitor, context.names, context.rosterGolferIds)
    }

    context(ctx: WriteTransactionContext)
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
    context(ctx: WriteTransactionContext)
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
        val names: GolferNameIndex,
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

/**
 * Found without writing, so the read-only paths name the row the import credits. Only the import writes: it gives
 * an [Idless] row the competitor's ESPN id, and creates a golfer for [Unmatched] and [IdMismatch].
 */
private sealed interface CompetitorMatch {
    /** @see CompetitorMatch */
    data class Credited(val golfer: Golfer) : CompetitorMatch

    /** @see CompetitorMatch */
    data class Idless(val golfer: Golfer) : CompetitorMatch

    /** @see CompetitorMatch */
    data object Unmatched : CompetitorMatch

    /** [namesake] holds another ESPN id, so it is another golfer however its name reads. */
    data class IdMismatch(val namesake: Golfer) : CompetitorMatch

    /** A new golfer would twin one of the [drafted] namesakes, and none of them can be told apart by name. */
    data class Withheld(val drafted: List<Golfer>) : CompetitorMatch
}

/** An ESPN athlete id is a public token, never member data, so a log line may carry it. */
@JvmInline
private value class LoggedEspnId(private val value: String) {
    override fun toString(): String = value
}

private val CompetitorMatch.creditedGolfer: Golfer?
    get() =
        when (this) {
            is CompetitorMatch.Credited -> golfer
            is CompetitorMatch.Idless -> golfer
            CompetitorMatch.Unmatched, is CompetitorMatch.IdMismatch, is CompetitorMatch.Withheld -> null
        }

/**
 * A partner row skips the full-name step: its name is a surname alone, which would fully match only a
 * name-only phantom row and let it outrank the rostered golfer. Only partners may break a surname tie by
 * roster; a full-named competitor guessed that way overwrote a rostered namesake's result (CWF-35).
 */
private fun resolveByName(
    competitor: EspnCompetitor,
    names: GolferNameIndex,
    rosteredGolferIds: Set<GolferId>,
): CompetitorMatch =
    if (competitor.isTeamPartner) {
        resolvePartnerByName(competitor, names, rosteredGolferIds)
    } else {
        resolveAthleteByName(competitor, names, rosteredGolferIds)
    }

/** A partner's ESPN id is synthetic, so a row's own id neither refuses the match nor is written. */
private fun resolvePartnerByName(
    competitor: EspnCompetitor,
    names: GolferNameIndex,
    rosteredGolferIds: Set<GolferId>,
): CompetitorMatch {
    val golfer =
        when (val bySurname = names.matchSurname(competitor.name)) {
            is GolferNameMatch.Matched -> bySurname.golfer
            is GolferNameMatch.Ambiguous -> bySurname.candidates.singleOrNull { it.id in rosteredGolferIds }
            GolferNameMatch.NoMatch -> null
        }
    return golfer?.let(CompetitorMatch::Credited) ?: CompetitorMatch.Unmatched
}

private fun resolveAthleteByName(
    competitor: EspnCompetitor,
    names: GolferNameIndex,
    rosteredGolferIds: Set<GolferId>,
): CompetitorMatch =
    when (val byFullName = names.matchFullName(competitor.name)) {
        is GolferNameMatch.Matched -> claim(competitor, byFullName.golfer)
        is GolferNameMatch.Ambiguous ->
            preferDrafted(byFullName.candidates, rosteredGolferIds) ?: claimUniqueSurname(competitor, names)
        GolferNameMatch.NoMatch -> claimUniqueSurname(competitor, names)
    }

/** Full-name namesakes only: the roster may never break a surname tie for a full-named competitor. */
private fun preferDrafted(
    namesakes: List<Golfer>,
    rosteredGolferIds: Set<GolferId>,
): CompetitorMatch? {
    val drafted = namesakes.filter { it.pgaPlayerId == null && it.id in rosteredGolferIds }
    if (drafted.isEmpty()) return null
    return drafted.singleOrNull()?.let(CompetitorMatch::Idless) ?: CompetitorMatch.Withheld(drafted)
}

private fun claimUniqueSurname(
    competitor: EspnCompetitor,
    names: GolferNameIndex,
): CompetitorMatch =
    when (val bySurname = names.matchSurname(competitor.name)) {
        is GolferNameMatch.Matched -> claim(competitor, bySurname.golfer)
        is GolferNameMatch.Ambiguous, GolferNameMatch.NoMatch -> CompetitorMatch.Unmatched
    }

/** A blank id is not id-less: the backfill only ever replaces NULL. */
private fun claim(
    competitor: EspnCompetitor,
    namesake: Golfer,
): CompetitorMatch =
    when (namesake.pgaPlayerId) {
        null -> CompetitorMatch.Idless(namesake)
        competitor.espnId -> CompetitorMatch.Credited(namesake)
        else -> CompetitorMatch.IdMismatch(namesake)
    }

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
 * Exact id only, never the lone event on the date: an unset or stale id would then preview money finalize
 * never imports. Preview and finalize must pick by the same rule.
 */
internal fun findLinkedEvent(
    events: List<EspnTournament>,
    tournament: Tournament,
): EspnTournament? = tournament.pgaTournamentId?.let { espnId -> events.firstOrNull { it.espnId == espnId } }

/**
 * The team flag is [event]'s, not [tournament]'s: finalize's import writes ESPN's flag over the row before it
 * scores, and preview must pay what finalize will.
 */
internal fun buildLivePreviewForEvent(
    event: EspnTournament,
    tournament: Tournament,
    ctx: LivePreviewContext,
): EspnLivePreview {
    val multiplier = tournament.payoutMultiplier
    val isTeamEvent = event.isTeamEvent

    val matchedCompetitors: List<Pair<EspnCompetitor, Golfer?>> =
        event.competitors.map { competitor -> competitor to ctx.resolveGolfer(competitor, tournament.id) }
    val tiedCounts = event.competitors.groupingBy { it.position }.eachCount()

    val teamPreviews =
        ctx.teams.map { team ->
            buildTeamPreview(team, matchedCompetitors, tiedCounts, ctx, multiplier, isTeamEvent)
        }

    return EspnLivePreview(
        espnName = event.name,
        payoutMultiplier = multiplier,
        teams = teamPreviews,
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
    val teamGolferIds = ctx.rostersByTeam[team.id].orEmpty().map { it.golferId }.toSet()
    val golferEarnings =
        matchedCompetitors.mapNotNull { (competitor, golfer) ->
            val resolvedGolfer = golfer ?: return@mapNotNull null
            if (resolvedGolfer.id !in teamGolferIds) return@mapNotNull null
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
                golferId = resolvedGolfer.id,
                position = competitor.position,
                numTied = numTied,
                payout = ownerPayout,
            )
        }
    val topTens = golferEarnings.fold(BigDecimal.ZERO) { acc, score -> acc.add(score.payout) }
    return PreviewTeamScore(
        teamId = team.id,
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
    val golferNames: GolferNameIndex,
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
        if (!competitor.isTeamPartner) golferByEspnId[competitor.espnId]?.let { return it }
        return resolveByName(competitor, golferNames, rosteredGolferIds).creditedGolfer
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
                golferNames = GolferNameIndex(allGolfers),
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
                teamNames = golfer?.let { teamNamesByGolfer[it.id] }.orEmpty(),
                pairKey = competitor.pairKey,
                roundScores = competitor.roundScores,
                totalStrokes = competitor.totalStrokes,
            )
        }
}
