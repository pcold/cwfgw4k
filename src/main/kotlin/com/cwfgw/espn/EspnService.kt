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
import com.cwfgw.golfers.PgaPlayerIdClaim
import com.cwfgw.golfers.findOrCreateAll
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
import com.cwfgw.tournaments.NewTournamentResult
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentFilter
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentStatus
import com.cwfgw.tournaments.UpdateTournamentRequest
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException

private val log = KotlinLogging.logger {}

/** Finalize's own gate: a sibling an unlinked finalize would skip as completed is never settled with. */
private val UNFINALIZED: Set<TournamentStatus> = setOf(TournamentStatus.Upcoming, TournamentStatus.InProgress)

/**
 * A named competitor [locateExistingGolferIn] credits to no row becomes a new golfer, unless names can't tell which
 * existing row it is. A team-partner row gets no `pga_player_id`, because its ESPN id is synthetic (`team:X:N`).
 */
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
     * [candidates] must hold every completed tournament later handed to [previewForTournament]: one left
     * out previews without its link overrides. The season's unfinalized ones are loaded as siblings anyway.
     */
    internal suspend fun loadLivePreviewContext(
        seasonId: SeasonId,
        candidates: List<Tournament>,
    ): LivePreviewContext =
        tx.read {
            val unfinalized = findUnfinalizedIn(seasonId)
            val previewable = (candidates + unfinalized).distinctBy { it.id }
            val overrides =
                linkRepository.listByTournaments(previewable.map { it.id })
                    .groupBy({ it.tournamentId }, { it.espnCompetitorId to it.golferId })
            LivePreviewContext.from(
                allGolfers = golferRepository.findAll(search = null),
                teams = teamRepository.findBySeason(seasonId),
                rosters = teamRepository.findRostersBySeason(seasonId),
                rules = seasonRepository.getRules(seasonId) ?: SeasonRules.defaults(),
                overridesByTournament = previewable.associate { it.id to overrides[it.id].orEmpty().toMap() },
                unfinalizedTournaments = unfinalized,
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
        val siblings = findSiblingImports(tournament, event, events, ctx.unfinalizedTournaments)
        return buildLivePreviewForEvent(event, tournament, ctx, siblings)
    }

    context(ctx: TransactionContext)
    private fun findUnfinalizedIn(
        seasonId: SeasonId,
        startsOn: LocalDate? = null,
    ): List<Tournament> =
        tournamentRepository.findAll(TournamentFilter(seasonId = seasonId, statuses = UNFINALIZED, startsOn = startsOn))

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
                val located = event.competitors.map { it to locateExistingGolferIn(it, context) }
                val sameDay = findUnfinalizedIn(tournament.seasonId, startsOn = tournament.startDate)
                val siblings =
                    findSiblingImports(tournament, event, events, sameDay)
                        .map { sibling -> locateEventIn(sibling).matches }
                located.settledBeside(siblings).map { (competitor, match) ->
                    TournamentCompetitorView(
                        espnCompetitorId = competitor.espnId,
                        name = competitor.name,
                        position = competitor.position,
                        isTeamPartner = competitor.isTeamPartner,
                        linkedGolfer = match.creditedGolfer,
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

    /** @see persistImportsIn */
    context(ctx: WriteTransactionContext)
    internal fun persistImportIn(
        tournament: Tournament,
        event: EspnTournament,
    ): List<EspnImportWarnings> = persistImportsIn(listOf(EventImport(tournament, event)))

    /**
     * Writes into the caller's transaction so finalize persists the import and the scores atomically. Every event is
     * matched before any golfer is written, so all their writes share [findOrCreateAll]'s one order; if written event
     * by event, a later event's athlete could take its lock before an earlier one's, against a confirm taking both.
     * Answers one [EspnImportWarnings] per import, in [imports]' order.
     */
    context(ctx: WriteTransactionContext)
    internal fun persistImportsIn(imports: List<EventImport>): List<EspnImportWarnings> {
        val located = imports.map { locateEventIn(it) }
        val matched =
            located.zip(located.map(MatchedEvent::matches).settled()) { event, settled ->
                event.copy(matches = settled)
            }
        val credited = matched.flatMap { it.matches }.mapNotNull { (_, match) -> match.creditedRow?.id }.toSet()
        val written = golferRepository.findOrCreateAll(matched.flatMap(MatchedEvent::claims), referenced = credited)
        return matched.map { matchedEvent ->
            val (import, matches) = matchedEvent
            val placements =
                matches.map { (competitor, match) ->
                    Placement(competitor = competitor, match = match, golfer = bindGolferIn(competitor, match, written))
                }
            val matchedPairs =
                placements.mapNotNull { placement -> placement.golfer?.let { placement.competitor to it.id } }
            persistResultsIn(import.tournament.id, matchedPairs)
            syncTournamentFromEventIn(import.tournament, import.event)
            matchedEvent.warningsFor(placements)
        }
    }

    context(ctx: TransactionContext)
    private fun locateEventIn(import: EventImport): MatchedEvent {
        val context = buildMatchingContextIn(import.tournament.seasonId, import.tournament.id)
        val located = import.event.competitors.map { it to locateExistingGolferIn(it, context) }
        return MatchedEvent(import = import, matches = located, context = context)
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

    /** A competitor whose id another event's claim wrote is credited that row, as a later event would find it by id. */
    context(ctx: WriteTransactionContext)
    private fun bindGolferIn(
        competitor: EspnCompetitor,
        match: CompetitorMatch,
        written: Map<String, Golfer>,
    ): Golfer? {
        val writtenId = written[competitor.espnId]
        return when (match) {
            is CompetitorMatch.Credited -> match.golfer
            is CompetitorMatch.SurnameGuess -> match.golfer
            is CompetitorMatch.Idless -> writtenId
            CompetitorMatch.Unmatched -> if (competitor.isTeamPartner) createPartnerIn(competitor) else writtenId
            is CompetitorMatch.IdMismatch -> {
                val espnId = LoggedEspnId(competitor.espnId)
                log.warn {
                    "cwfgw4k.espn event=namesake-id-mismatch espn_id=$espnId golfer_id=${match.namesake.id.value}"
                }
                writtenId
            }
            is CompetitorMatch.Withheld -> {
                if (writtenId == null) {
                    val espnId = LoggedEspnId(competitor.espnId)
                    val golferIds = match.candidates.map { it.id.value }
                    log.warn { "cwfgw4k.espn event=namesake-withheld espn_id=$espnId golfer_ids=$golferIds" }
                }
                writtenId
            }
        }
    }

    /** Outside [findOrCreateAll]: a partner's id is synthetic and never written, so its insert takes no key lock. */
    context(ctx: WriteTransactionContext)
    private fun createPartnerIn(competitor: EspnCompetitor): Golfer? {
        val (firstName, lastName) = GolferName.splitName(competitor.name) ?: return null
        return golferRepository.findOrCreate(CreateGolferRequest(firstName = firstName, lastName = lastName))
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
                NewTournamentResult(
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

    /** Credited unless [settlingSurnameGuesses] finds another claim on [golfer]; never backfilled. */
    data class SurnameGuess(val golfer: Golfer) : CompetitorMatch

    /** @see CompetitorMatch */
    data object Unmatched : CompetitorMatch

    /** [namesake] holds, or this import claims it for, another ESPN id, so it is another golfer however it reads. */
    data class IdMismatch(val namesake: Golfer) : CompetitorMatch

    /** Names can't tell which of [candidates] this competitor is, and a new golfer would twin the one it is. */
    data class Withheld(val candidates: List<Golfer>) : CompetitorMatch
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
            is CompetitorMatch.SurnameGuess -> golfer
            CompetitorMatch.Unmatched, is CompetitorMatch.IdMismatch, is CompetitorMatch.Withheld -> null
        }

/** One event's competitors, in ESPN's order, each with the row it matched. */
private typealias LocatedField = List<Pair<EspnCompetitor, CompetitorMatch>>

/**
 * Finalize's rule over every event one import writes; the listing and the preview settle through it too, so each
 * credits what that import would. A contest inside one event is withheld before the events are compared.
 */
private fun List<LocatedField>.settled(): List<LocatedField> =
    settlingSurnameGuesses()
        .flatMap { field -> listOf(field).withholdingContestedRows() }
        .withholdingContestedRows()

/** @see settled */
@Suppress("ForbiddenMethodCall") // settled answers one field per field, in order, so the head is this one's.
private fun LocatedField.settledBeside(siblings: List<LocatedField>): LocatedField =
    (listOf(this) + siblings).settled().first()

/**
 * A surname credit is a guess: it yields a row any other match credits, backfills or contests under another ESPN id,
 * and the guesser is that row's namesake. Two ids guessing one row are withheld, as two full names are.
 */
private fun List<LocatedField>.settlingSurnameGuesses(): List<LocatedField> {
    val settlement = SurnameGuessSettlement(flatten())
    return map { field -> field.map { (competitor, match) -> competitor to settlement.settle(competitor, match) } }
}

/** Reads every claim before any is settled, so a guess withheld here never counts as a claim on its row. */
private class SurnameGuessSettlement(located: List<Pair<EspnCompetitor, CompetitorMatch>>) {
    private val claimantIds = espnIdsPerRow(located, CompetitorMatch::claimedRows)
    private val guesserIds = espnIdsPerRow(located) { match -> listOfNotNull(match.guessedRow) }

    fun settle(
        competitor: EspnCompetitor,
        match: CompetitorMatch,
    ): CompetitorMatch {
        if (match !is CompetitorMatch.SurnameGuess) return match
        val row = match.golfer
        return when {
            claimantIds[row.id].orEmpty().any { it != competitor.espnId } -> CompetitorMatch.IdMismatch(row)
            guesserIds.getValue(row.id).size > 1 -> CompetitorMatch.Withheld(listOf(row))
            else -> match
        }
    }

    private fun espnIdsPerRow(
        located: List<Pair<EspnCompetitor, CompetitorMatch>>,
        rowsOf: (CompetitorMatch) -> List<Golfer>,
    ): Map<GolferId, Set<String>> =
        located.flatMap { (competitor, match) -> rowsOf(match).map { row -> row.id to competitor.espnId } }
            .groupBy(keySelector = { (rowId, _) -> rowId }, valueTransform = { (_, espnId) -> espnId })
            .mapValues { (_, espnIds) -> espnIds.toSet() }
}

/** An existing row the result is written for as matched; an [CompetitorMatch.Idless] row is a claim's instead. */
private val CompetitorMatch.creditedRow: Golfer?
    get() =
        when (this) {
            is CompetitorMatch.Credited -> golfer
            is CompetitorMatch.SurnameGuess -> golfer
            is CompetitorMatch.Idless, CompetitorMatch.Unmatched, is CompetitorMatch.IdMismatch,
            is CompetitorMatch.Withheld,
            -> null
        }

private val CompetitorMatch.guessedRow: Golfer?
    get() = (this as? CompetitorMatch.SurnameGuess)?.golfer

/** A partner's surname credit counts: its name is a surname alone, so that is its full-name match. */
private val CompetitorMatch.claimedRows: List<Golfer>
    get() =
        when (this) {
            is CompetitorMatch.Credited -> listOf(golfer)
            is CompetitorMatch.Idless -> listOf(golfer)
            is CompetitorMatch.Withheld -> candidates
            is CompetitorMatch.SurnameGuess, CompetitorMatch.Unmatched, is CompetitorMatch.IdMismatch -> emptyList()
        }

/** One completed ESPN event and the tournament its results are written to. */
internal data class EventImport(
    val tournament: Tournament,
    val event: EspnTournament,
)

/**
 * [names] holds every golfer the matcher read, so a bound golfer missing from it is reported as created. A row another
 * transaction commits between that read and the write is reported too: nothing here records which rows were inserted.
 */
private data class MatchingContext(
    val names: GolferNameIndex,
    val rosterGolferIds: Set<GolferId>,
    val overrides: Map<String, GolferId>,
)

private data class MatchedEvent(
    val import: EventImport,
    val matches: List<Pair<EspnCompetitor, CompetitorMatch>>,
    val context: MatchingContext,
)

/** [golfer] is the row the competitor's result was written for, or null when it got none. */
private data class Placement(
    val competitor: EspnCompetitor,
    val match: CompetitorMatch,
    val golfer: Golfer?,
)

/**
 * A golfer created for another event's claim in the same import is reported under each event it played. A withheld
 * competitor credited through another event's claim got a result, so it is not contested.
 */
private fun MatchedEvent.warningsFor(placements: List<Placement>): EspnImportWarnings {
    val readIds = context.names.golfers.map(Golfer::id).toSet()
    val created = placements.mapNotNull(Placement::golfer).distinctBy(Golfer::id).filter { it.id !in readIds }
    val placedIds = placements.mapNotNull { it.golfer?.id }.toSet()
    val rostered = GolferNameIndex(context.names.golfers.filter { it.id in context.rosterGolferIds })
    val twins = created.mapNotNull { golfer -> rosteredSurnameTwin(golfer, rostered, placedIds) }
    val twinIds = twins.map { it.created.id }.toSet()
    val unplaced = placements.filter { it.golfer == null }
    return EspnImportWarnings(
        tournamentId = import.tournament.id,
        tournamentName = import.tournament.name,
        rosteredSurnameTwins = twins,
        createdGolfers = created.filter { it.id !in twinIds },
        unmatchedCompetitors =
            unplaced.filter { it.match !is CompetitorMatch.Withheld }
                .map { UnmatchedCompetitor(espnCompetitorId = it.competitor.espnId, name = it.competitor.name) },
        contestedCompetitors =
            unplaced.mapNotNull { placement ->
                (placement.match as? CompetitorMatch.Withheld)?.let { withheld ->
                    ContestedCompetitor(
                        espnCompetitorId = placement.competitor.espnId,
                        name = placement.competitor.name,
                        candidates = withheld.candidates,
                    )
                }
            },
    )
}

/**
 * Keyed as the matcher keys a surname guess, so the twin it would have guessed at is the one reported. A namesake
 * placed in the same event played beside [created], so it is provably someone else and is left out.
 */
private fun rosteredSurnameTwin(
    created: Golfer,
    rostered: GolferNameIndex,
    placedIds: Set<GolferId>,
): RosteredSurnameTwin? {
    val namesakes =
        when (val bySurname = rostered.matchSurname("${created.firstName} ${created.lastName}")) {
            is GolferNameMatch.Matched -> listOf(bySurname.golfer)
            is GolferNameMatch.Ambiguous -> bySurname.candidates
            GolferNameMatch.NoMatch -> emptyList()
        }
    val unplaced = namesakes.filter { it.id !in placedIds }
    return if (unplaced.isEmpty()) null else RosteredSurnameTwin(created = created, rostered = unplaced)
}

private val CompetitorMatch.idlessRow: Golfer?
    get() = (this as? CompetitorMatch.Idless)?.golfer

/**
 * Confirm refuses the same contest: neither name says whose the row is, and the loser's new golfer would twin it. An
 * id ESPN lists twice, in one event or across two, is one athlete and no contest, as it is on an id-bearing row.
 */
private fun List<LocatedField>.withholdingContestedRows(): List<LocatedField> {
    val idsPerRow =
        flatten()
            .mapNotNull { (competitor, match) -> match.idlessRow?.let { row -> row.id to competitor.espnId } }
            .groupBy(keySelector = { (rowId, _) -> rowId }, valueTransform = { (_, espnId) -> espnId })
    val contested = idsPerRow.filterValues { espnIds -> espnIds.distinct().size > 1 }.keys
    return map { field ->
        field.map { (competitor, match) ->
            val row = match.idlessRow
            competitor to if (row != null && row.id in contested) CompetitorMatch.Withheld(listOf(row)) else match
        }
    }
}

private fun MatchedEvent.claims(): List<PgaPlayerIdClaim> =
    matches.mapNotNull { (competitor, match) -> claimFor(competitor, match) }

/** A partner's synthetic id is never written, and a blank name has nothing to create. */
private fun claimFor(
    competitor: EspnCompetitor,
    match: CompetitorMatch,
): PgaPlayerIdClaim? {
    if (competitor.isTeamPartner) return null
    val name = GolferName.splitName(competitor.name) ?: return null
    return when (match) {
        is CompetitorMatch.Idless -> PgaPlayerIdClaim.Backfill(match.golfer.id, competitor.espnId, name)
        CompetitorMatch.Unmatched, is CompetitorMatch.IdMismatch -> PgaPlayerIdClaim.Create(competitor.espnId, name)
        is CompetitorMatch.Credited, is CompetitorMatch.SurnameGuess, is CompetitorMatch.Withheld -> null
    }
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
        is GolferNameMatch.Matched -> claim(byFullName.golfer)
        is GolferNameMatch.Ambiguous ->
            preferRostered(byFullName.candidates, rosteredGolferIds) ?: creditUniqueSurname(competitor, names)
        GolferNameMatch.NoMatch -> creditUniqueSurname(competitor, names)
    }

/** Full-name namesakes only: the roster may never break a surname tie for a full-named competitor. */
private fun preferRostered(
    namesakes: List<Golfer>,
    rosteredGolferIds: Set<GolferId>,
): CompetitorMatch? {
    val rostered = namesakes.filter { it.pgaPlayerId == null && it.id in rosteredGolferIds }
    if (rostered.isEmpty()) return null
    return rostered.singleOrNull()?.let(CompetitorMatch::Idless) ?: CompetitorMatch.Withheld(rostered)
}

/** Never backfilled: a surname match is a guess, and a written id would keep the guess after the real golfer plays. */
private fun creditUniqueSurname(
    competitor: EspnCompetitor,
    names: GolferNameIndex,
): CompetitorMatch =
    when (val bySurname = names.matchSurname(competitor.name)) {
        is GolferNameMatch.Matched ->
            bySurname.golfer.takeIf { it.pgaPlayerId == null }?.let(CompetitorMatch::SurnameGuess)
                ?: CompetitorMatch.IdMismatch(bySurname.golfer)
        is GolferNameMatch.Ambiguous, GolferNameMatch.NoMatch -> CompetitorMatch.Unmatched
    }

/**
 * A row holding the competitor's own id was found by id before any name match, so any id here is another's. A blank
 * id is not id-less: the backfill only ever replaces NULL.
 */
private fun claim(namesake: Golfer): CompetitorMatch =
    if (namesake.pgaPlayerId == null) CompetitorMatch.Idless(namesake) else CompetitorMatch.IdMismatch(namesake)

/**
 * Kept apart from [EspnError] so the overlay's `when` is exhaustive over what a preview can actually
 * answer: [NoMatchingEvent] is routine and skipped silently, [NotStarted] pays nothing but shows its [field],
 * [EspnUnavailable] is logged.
 */
internal sealed interface LivePreviewError {
    /** @see LivePreviewError */
    data object NoMatchingEvent : LivePreviewError

    /** Nobody has teed off, so every entry of [field] is unplaced. */
    data class NotStarted(val field: List<PreviewLeaderboardEntry>) : LivePreviewError

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
 * What an unlinked finalize of [tournament]'s day imports beside [event], so a preview or listing settles them as one
 * import; none once [tournament] is finalized, since that finalize skips it, or with no unlinked tournament that day,
 * since each linked one then finalizes alone. ESPN's completion flag is ignored: both predict the finished import.
 */
private fun findSiblingImports(
    tournament: Tournament,
    event: EspnTournament,
    events: List<EspnTournament>,
    unfinalized: List<Tournament>,
): List<EventImport> {
    val sameDay = unfinalized.filter { it.seasonId == tournament.seasonId && it.startDate == tournament.startDate }
    if (tournament.status !in UNFINALIZED || sameDay.none { it.pgaTournamentId == null }) return emptyList()
    return sameDay
        .filter { it.id != tournament.id }
        .mapNotNull { sibling ->
            findLinkedEvent(events, sibling)?.takeIf { it.espnId != event.espnId }?.let { EventImport(sibling, it) }
        }
}

/**
 * The team flag is [event]'s, not [tournament]'s: finalize's import writes ESPN's flag over the row before it
 * scores, and preview must pay what finalize will.
 */
internal fun buildLivePreviewForEvent(
    event: EspnTournament,
    tournament: Tournament,
    ctx: LivePreviewContext,
    siblings: List<EventImport> = emptyList(),
): Result<EspnLivePreview, LivePreviewError.NotStarted> {
    val multiplier = tournament.payoutMultiplier
    val isTeamEvent = event.isTeamEvent

    val located = event.competitors.map { it to ctx.match(it, tournament.id) }
    val siblingFields =
        siblings.map { sibling -> sibling.event.competitors.map { it to ctx.match(it, sibling.tournament.id) } }
    // Settled whole before the split, as finalize settles it, so an unstarted claimant still withholds a guess.
    val (teedOff, unstarted) =
        located.settledBeside(siblingFields)
            .map { (competitor, match) -> competitor to match.creditedGolfer }
            .partition { (competitor, _) -> competitor.hasTeedOff }
    if (teedOff.isEmpty()) {
        return Result.Err(
            LivePreviewError.NotStarted(buildLivePreviewLeaderboard(emptyList(), unstarted, ctx.teams, ctx.rosters)),
        )
    }
    val matchedCompetitors = teedOff.ranked(completed = event.completed)
    val tiedCounts = matchedCompetitors.groupingBy { (competitor, _) -> competitor.position }.eachCount()

    val teamPreviews =
        ctx.teams.map { team ->
            buildTeamPreview(team, matchedCompetitors, tiedCounts, ctx, multiplier, isTeamEvent)
        }

    return Result.Ok(
        EspnLivePreview(
            espnName = event.name,
            payoutMultiplier = multiplier,
            teams = teamPreviews,
            isTeamEvent = isTeamEvent,
            leaderboard = buildLivePreviewLeaderboard(matchedCompetitors, unstarted, ctx.teams, ctx.rosters),
        ),
    )
}

/** Re-placed among those who have teed off alone: no strokes means no finish, so no place and no share of a tie. */
private fun List<Pair<EspnCompetitor, Golfer?>>.ranked(completed: Boolean): List<Pair<EspnCompetitor, Golfer?>> {
    val golferByEspnId = associate { (competitor, golfer) -> competitor.espnId to golfer }
    return assignPositions(map { (competitor, _) -> competitor }, completed = completed)
        .map { competitor -> competitor to golferByEspnId[competitor.espnId] }
}

private val EspnCompetitor.hasTeedOff: Boolean
    get() = roundScores.isNotEmpty()

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
    val unfinalizedTournaments: List<Tournament> = emptyList(),
) {
    companion object {
        fun from(
            allGolfers: List<Golfer>,
            teams: List<Team>,
            rosters: List<RosterEntry>,
            rules: SeasonRules,
            overridesByTournament: Map<TournamentId, Map<String, GolferId>>,
            unfinalizedTournaments: List<Tournament> = emptyList(),
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
                unfinalizedTournaments = unfinalizedTournaments,
            )
    }
}

private fun LivePreviewContext.match(
    competitor: EspnCompetitor,
    tournamentId: TournamentId,
): CompetitorMatch {
    val override = overridesByTournament[tournamentId]?.get(competitor.espnId)
    if (override != null) golferById[override]?.let { return CompetitorMatch.Credited(it) }
    if (!competitor.isTeamPartner) golferByEspnId[competitor.espnId]?.let { return CompetitorMatch.Credited(it) }
    return resolveByName(competitor, golferNames, rosteredGolferIds)
}

/** The whole field: [placed] by position, then [unstarted] unplaced, in ESPN's order. */
private fun buildLivePreviewLeaderboard(
    placed: List<Pair<EspnCompetitor, Golfer?>>,
    unstarted: List<Pair<EspnCompetitor, Golfer?>>,
    teams: List<Team>,
    rosters: List<RosterEntry>,
): List<PreviewLeaderboardEntry> {
    val nameByTeam = teams.associate { it.id to it.teamName }
    val teamNamesByGolfer =
        rosters
            .groupBy { it.golferId }
            .mapValues { (_, entries) -> entries.mapNotNull { nameByTeam[it.teamId] } }

    fun entry(competitor: EspnCompetitor, golfer: Golfer?, position: Int?, scoreToPar: Int?) =
        PreviewLeaderboardEntry(
            name = competitor.name,
            position = position,
            scoreToPar = scoreToPar,
            teamNames = golfer?.let { teamNamesByGolfer[it.id] }.orEmpty(),
            pairKey = competitor.pairKey,
            roundScores = competitor.roundScores,
            totalStrokes = competitor.totalStrokes,
        )
    return placed.sortedBy { (competitor, _) -> competitor.position }
        .map { (competitor, golfer) -> entry(competitor, golfer, competitor.position, competitor.scoreToPar) } +
        unstarted.sortedBy { (competitor, _) -> competitor.order }
            .map { (competitor, golfer) -> entry(competitor, golfer, position = null, scoreToPar = null) }
}
