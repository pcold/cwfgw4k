package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.db.updateOrOutraced
import com.cwfgw.espn.EspnCalendarEntry
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnUpstreamException
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferName
import com.cwfgw.golfers.GolferNameIndex
import com.cwfgw.golfers.GolferNameMatch
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.PgaPlayerIdClaim
import com.cwfgw.golfers.findOrCreateAll
import com.cwfgw.golfers.normalizeName
import com.cwfgw.result.Result
import com.cwfgw.result.map
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.NewRosterEntry
import com.cwfgw.teams.NewTeam
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamRepository
import com.cwfgw.teams.UNIQUE_TEAM_NAME_PER_SEASON
import com.cwfgw.tournaments.NewTournament
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.UNIQUE_PGA_TOURNAMENT_ID
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.WeekFields

private val log = KotlinLogging.logger {}

/**
 * Operator tooling for bulk season + roster setup.
 *
 * The season-creation flow is preview-then-confirm:
 * [previewSeasonSchedule] fetches ESPN's calendar, filters to events whose
 * start date falls in the given range, and returns them as candidates —
 * no DB writes. The operator reviews the list and drops anything that
 * isn't part of this league's season (e.g. the Presidents Cup, which ESPN's
 * calendar includes but this game never scores). [confirmSeasonSchedule]
 * then creates one tournament per entry the operator kept, with
 * `pgaTournamentId` already linked. Week labels are recomputed at confirm
 * time from just the kept entries, so dropping a candidate closes the
 * numbering gap rather than leaving one. Tournaments come back at
 * multiplier 1.0; the UI then lets the operator edit multipliers via the
 * existing `PUT /api/v1/tournaments/{id}` route, so this service
 * deliberately doesn't accept a per-tournament multiplier override — it
 * would just duplicate that path.
 *
 * Re-runs are safe: any ESPN entry already linked to a tournament lands in
 * the `skipped` list with a clear reason, never overwrites or duplicates.
 */
class AdminService(
    private val tx: Transactor,
    private val seasonRepository: SeasonRepository,
    private val tournamentRepository: TournamentRepository,
    private val espnService: EspnService,
    private val golferRepository: GolferRepository,
    private val teamRepository: TeamRepository,
) {
    suspend fun previewSeasonSchedule(
        seasonId: SeasonId,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<SeasonSchedulePreviewResult, AdminError> {
        tx.get { seasonRepository.findById(seasonId) } ?: return Result.Err(AdminError.SeasonNotFound(seasonId))

        val calendar =
            try {
                espnService.fetchCalendar()
            } catch (e: EspnUpstreamException) {
                log.warn(e) { "ESPN calendar fetch failed during admin season preview: ${e.message}" }
                return Result.Err(AdminError.UpstreamUnavailable(e.failure))
            }

        // ESPN's calendar response doesn't carry a "week N" concept, so we
        // synthesize one from chronologically-sorted in-range entries. Two
        // tournaments in the same ISO calendar week get suffixed "Na" / "Nb"
        // (the 'b' is conventionally the alternate-field event, but we can't
        // tell which is which from ESPN — operators correct via the UI).
        val (datedEntries, unparseable) = parseAndFilterByRange(calendar, startDate, endDate)
        val skipped = unparseable.toMutableList()
        val entries = mutableListOf<PreviewedTournament>()
        val linked =
            tx.get { tournamentRepository.findByPgaTournamentIds(datedEntries.map { (entry, _) -> entry.id }) }
                .associateBy(Tournament::pgaTournamentId)
        for ((pair, week) in assignSequentialWeekLabels(datedEntries) { it.second }) {
            val (entry, date) = pair
            val existing = linked[entry.id]
            if (existing != null) {
                skipped += SkippedEntry(entry.id, entry.label, "already linked to tournament ${existing.id.value}")
            } else {
                entries +=
                    PreviewedTournament(
                        espnEventId = entry.id,
                        name = entry.label,
                        startDate = date,
                        endDate = date.plusDays(DEFAULT_TOURNAMENT_DAYS),
                        week = week,
                    )
            }
        }
        return Result.Ok(SeasonSchedulePreviewResult(entries = entries, skipped = skipped))
    }

    /**
     * One transaction, so a failure partway leaves no tournament. The linked read takes no lock: a skip writes nothing,
     * and a link committed after it trips `pga_tournament_id`'s unique key, refused as [AdminError.EspnEventTaken].
     */
    suspend fun confirmSeasonSchedule(
        seasonId: SeasonId,
        entries: List<ConfirmedTournamentEntry>,
    ): Result<SeasonImportResult, AdminError> =
        tx.updateOrOutraced(UNIQUE_PGA_TOURNAMENT_ID, outraced = { AdminError.EspnEventTaken(seasonId) }) {
            tournamentRepository.lockSeason(seasonId)
            seasonRepository.findById(seasonId)
                ?: return@updateOrOutraced Result.Err(AdminError.SeasonNotFound(seasonId))
            Result.Ok(importSchedule(seasonId, entries))
        }

    context(ctx: WriteTransactionContext)
    private fun importSchedule(
        seasonId: SeasonId,
        entries: List<ConfirmedTournamentEntry>,
    ): SeasonImportResult {
        val labelled = assignSequentialWeekLabels(entries) { it.startDate }
        val linked =
            tournamentRepository.findByPgaTournamentIds(entries.map(ConfirmedTournamentEntry::espnEventId))
                .associateByTo(mutableMapOf(), Tournament::pgaTournamentId)
        val created = sortedMapOf<Int, Tournament>()
        val skipped = sortedMapOf<Int, SkippedEntry>()
        // Event-id order, not week order: two imports sharing events then take their unique-key entries in one order
        // rather than crossed, which would deadlock instead of answering EspnEventTaken.
        for (position in labelled.indices.sortedBy { labelled[it].first.espnEventId }) {
            val (entry, week) = labelled[position]
            val existing = linked[entry.espnEventId]
            if (existing != null) {
                skipped[position] =
                    SkippedEntry(entry.espnEventId, entry.name, "already linked to tournament ${existing.id.value}")
            } else {
                val tournament = tournamentRepository.create(entry.toNewTournament(seasonId, week))
                linked[entry.espnEventId] = tournament
                created[position] = tournament
            }
        }
        return SeasonImportResult(created = created.values.toList(), skipped = skipped.values.toList())
    }

    private fun parseAndFilterByRange(
        calendar: List<EspnCalendarEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): DatedCalendar {
        val inRange = mutableListOf<Pair<EspnCalendarEntry, LocalDate>>()
        val unparseable = mutableListOf<SkippedEntry>()
        for (entry in calendar) {
            val date = parseEspnDate(entry.startDate)
            when {
                date == null ->
                    unparseable +=
                        SkippedEntry(entry.id, entry.label, "could not parse ESPN start date '${entry.startDate}'")
                date !in startDate..endDate -> Unit
                else -> inRange += entry to date
            }
        }
        return DatedCalendar(inRange = inRange, unparseable = unparseable)
    }

    /**
     * Assign sequential week labels (1, 2, 3a, 3b, …) to [items] in
     * chronological order, grouping same-ISO-week items so multi-event
     * weeks share a number with an a/b suffix. Used both to label preview
     * candidates and to relabel the confirmed subset, so dropping a
     * candidate before confirm closes the numbering gap.
     */
    private fun <T> assignSequentialWeekLabels(
        items: List<T>,
        dateOf: (T) -> LocalDate,
    ): List<Pair<T, String>> {
        val grouped =
            items.sortedBy(dateOf)
                .groupBy { dateOf(it).weekKey() }
                .values
                .toList()
        return grouped.flatMapIndexed { weekIndex, group ->
            val weekNumber = weekIndex + 1
            group.mapIndexed { positionInWeek, item ->
                val label = if (group.size == 1) "$weekNumber" else "$weekNumber${'a' + positionInWeek}"
                item to label
            }
        }
    }

    /** Year-aware ISO week key so the year boundary doesn't collide week 52 with week 1. */
    private fun LocalDate.weekKey(): Pair<Int, Int> =
        get(WeekFields.ISO.weekBasedYear()) to get(WeekFields.ISO.weekOfWeekBasedYear())

    /**
     * Writes nothing: a pick only an unpersisted ESPN athlete names comes back [PickMatch.MatchedEspn] for
     * [confirmRoster] to insert. ESPN is asked only when some pick matches no row; an outage reads as no match.
     */
    suspend fun previewRoster(rosterText: String): Result<RosterPreviewResult, AdminError> {
        val parsed =
            when (val r = RosterParser.parse(rosterText)) {
                is Result.Ok -> r.value
                is Result.Err -> return Result.Err(AdminError.InvalidRoster(r.error))
            }

        val golfers = GolferNameIndex(tx.get { golferRepository.findAll(search = null) })
        val anyUnmatched =
            parsed.flatMap { it.picks }.any { pick ->
                golfers.matchFullName(pick.playerName) == GolferNameMatch.NoMatch
            }
        val athletes = if (anyUnmatched) espnService.fetchActivePlayers() else emptyList()
        val nameIndex = RosterNameIndex(golfers, athletes)

        val previewTeams = parsed.map { team -> previewTeam(team, nameIndex) }
        return Result.Ok(summarize(previewTeams))
    }

    private fun previewTeam(
        team: ParsedTeam,
        nameIndex: RosterNameIndex,
    ): PreviewTeam =
        PreviewTeam(
            teamNumber = team.teamNumber,
            teamName = team.teamName,
            picks =
                team.picks.map { pick ->
                    PreviewPick(
                        round = pick.round,
                        playerName = pick.playerName,
                        ownershipPct = pick.ownershipPct,
                        match = nameIndex.match(pick.playerName),
                    )
                },
        )

    private fun summarize(teams: List<PreviewTeam>): RosterPreviewResult {
        val allPicks = teams.flatMap { it.picks }
        return RosterPreviewResult(
            teams = teams,
            totalPicks = allPicks.size,
            matchedCount =
                allPicks.count { pick ->
                    pick.match is PickMatch.Matched || pick.match is PickMatch.MatchedEspn ||
                        pick.match is PickMatch.MatchedBackfill
                },
            ambiguousCount = allPicks.count { it.match is PickMatch.Ambiguous },
            unmatchedCount = allPicks.count { it.match == PickMatch.NoMatch },
        )
    }

    /**
     * One transaction, so a mid-loop failure leaves no half-built team and no golfer a `New` or `Espn` pick inserted.
     * The season lock comes before any golfer's, the order a finalize takes them. `Existing` ids are read unlocked, so
     * every bad one is refused at once; no path deletes a golfer, and [resolveEspnGolfers] locks the found ones.
     */
    suspend fun confirmRoster(request: ConfirmRosterRequest): Result<RosterUploadResult, AdminError> =
        tx.updateOrOutraced(
            UNIQUE_TEAM_NAME_PER_SEASON,
            outraced = { AdminError.TeamNameTaken(request.seasonId) },
        ) {
            tournamentRepository.lockSeason(request.seasonId)
            seasonRepository.findById(request.seasonId)
                ?: return@updateOrOutraced Result.Err(AdminError.SeasonNotFound(request.seasonId))
            val problems = findRosterProblems(request.teams)
            if (problems.isNotEmpty()) return@updateOrOutraced Result.Err(AdminError.InvalidConfirmedRoster(problems))
            val missingGolferIds = findMissingGolferIds(request.teams)
            if (missingGolferIds.isNotEmpty()) {
                return@updateOrOutraced Result.Err(AdminError.GolferIdsNotFound(missingGolferIds))
            }
            resolveEspnGolfers(request.teams).map { espnGolfers ->
                val builtTeams =
                    request.teams.map { team -> persistConfirmedTeam(request.seasonId, team, espnGolfers.golferIds) }
                RosterUploadResult(
                    teamsCreated = builtTeams.size,
                    golfersCreated = espnGolfers.insertedCount + builtTeams.sumOf { (_, newGolfers) -> newGolfers },
                    teams = builtTeams.map { (createdTeam, _) -> createdTeam },
                )
            }
        }

    /**
     * Before any team, so an athlete two teams share is one row, and every refusal is decided before the first write
     * since a returned error still commits. [EspnGolfers.insertedCount] can include a row a concurrent confirm
     * inserted first, since [GolferRepository.findOrCreate] returns either the same way. Every existing row a pick
     * names is locked with the claims, in their one order, so no roster insert after it waits out of order.
     */
    context(ctx: WriteTransactionContext)
    private fun resolveEspnGolfers(teams: List<ConfirmedTeam>): Result<EspnGolfers, AdminError> {
        val athletes =
            teams.flatMap { it.picks }
                .map(ConfirmedPick::assignment)
                .filterIsInstance<GolferAssignment.Espn>()
                .distinctBy(GolferAssignment.Espn::espnId)
        val holders =
            golferRepository.findByPgaPlayerIds(athletes.map(GolferAssignment.Espn::espnId))
                .associateBy(Golfer::pgaPlayerId)
        val persisted =
            athletes.mapNotNull { athlete ->
                holders[athlete.espnId]?.let { golfer -> athlete.espnId to golfer.id }
            }.toMap()
        val unpersisted = athletes.filterNot { it.espnId in persisted }
        val targets = targetRowsByName(unpersisted)
        val claims = unpersisted.associateWith { athlete -> athlete.toClaim(targets.getValue(athlete.espnId)) }
        val unresolvable = claims.filterValues { it == null }.keys
        if (unresolvable.isNotEmpty()) {
            val refusedIds = unresolvable.map(GolferAssignment.Espn::espnId).sorted()
            return Result.Err(AdminError.UnresolvableEspnGolfers(refusedIds))
        }
        val writes = claims.values.filterNotNull()
        val existing = teams.flatMap { it.picks }.mapNotNull { (it.assignment as? GolferAssignment.Existing)?.golferId }
        val written = golferRepository.findOrCreateAll(writes, referenced = existing.toSet() + persisted.values)
        return Result.Ok(
            EspnGolfers(
                golferIds = persisted + written.mapValues { (_, golfer) -> golfer.id },
                insertedCount = writes.count { claim -> claim.insertedInto(written) },
            ),
        )
    }

    context(ctx: WriteTransactionContext)
    private fun targetRowsByName(athletes: List<GolferAssignment.Espn>): Map<String, EspnTarget> {
        if (athletes.isEmpty()) return emptyMap()
        val names = GolferNameIndex(golferRepository.findAll(search = null))
        // Among several rows of a name the preview offers the athlete as a golfer of its own, so only a lone id-less
        // row is taken to be the athlete; an id-less row among namesakes is never guessed at.
        val loneIdlessRows =
            athletes.associate { athlete ->
                athlete.espnId to
                    names.matchFullName(athlete.name).golfers().singleOrNull()?.takeIf { it.pgaPlayerId == null }
            }
        val claimsPerRow = loneIdlessRows.values.filterNotNull().groupingBy(Golfer::id).eachCount()
        return loneIdlessRows.mapValues { (_, row) ->
            when {
                row == null -> EspnTarget.NewRow
                claimsPerRow.getValue(row.id) == 1 -> EspnTarget.IdlessRow(row.id)
                else -> EspnTarget.Unresolvable
            }
        }
    }

    context(ctx: WriteTransactionContext)
    private fun persistConfirmedTeam(
        seasonId: SeasonId,
        team: ConfirmedTeam,
        espnGolferIds: Map<String, GolferId>,
    ): Pair<Team, Int> {
        val createdTeam =
            teamRepository.create(
                seasonId = seasonId,
                request =
                    NewTeam(
                        ownerName = team.teamName,
                        teamName = team.teamName,
                        teamNumber = team.teamNumber,
                    ),
            )
        for (pick in team.picks) {
            val golferId =
                when (val assignment = pick.assignment) {
                    is GolferAssignment.Existing -> assignment.golferId
                    is GolferAssignment.New -> golferRepository.create(assignment.toCreateRequest()).id
                    is GolferAssignment.Espn -> espnGolferIds.getValue(assignment.espnId)
                }
            teamRepository.addToRoster(
                teamId = createdTeam.id,
                request =
                    NewRosterEntry(
                        golferId = golferId,
                        acquiredVia = ROSTER_ACQUIRED_VIA_DRAFT,
                        draftRound = pick.round,
                        ownershipPct = BigDecimal(pick.ownershipPct),
                    ),
            )
        }
        return createdTeam to team.picks.count { it.assignment is GolferAssignment.New }
    }

    context(ctx: WriteTransactionContext)
    private fun findMissingGolferIds(teams: List<ConfirmedTeam>): List<GolferId> {
        val referencedIds =
            teams.asSequence()
                .flatMap { it.picks.asSequence() }
                .mapNotNull { (it.assignment as? GolferAssignment.Existing)?.golferId }
                .toSet()
        val found = golferRepository.findByIds(referencedIds).map(Golfer::id).toSet()
        return referencedIds.filterNot { it in found }
    }

    /**
     * Parse the ISO-8601 prefix of an ESPN calendar entry's startDate
     * ("2026-04-09T00:00Z" → 2026-04-09). Returns null on anything we don't
     * recognize so the caller can skip-with-reason rather than blow up.
     */
    private fun parseEspnDate(raw: String): LocalDate? =
        try {
            LocalDate.parse(raw.take(ISO_DATE_LENGTH))
        } catch (e: DateTimeParseException) {
            log.warn(e) { "ESPN sent an unparseable startDate: '$raw'" }
            null
        }

    companion object {
        /** Default tournament length: Thu→Sun = 4 days inclusive (start + 3). Operator can PUT to fix exceptions. */
        private const val DEFAULT_TOURNAMENT_DAYS: Long = 3
        private const val ISO_DATE_LENGTH = 10

        /** Roster entries created by confirmRoster are draft picks — distinguishes from waiver/trade adds. */
        private const val ROSTER_ACQUIRED_VIA_DRAFT = "draft"
    }
}

/**
 * Every team needs exactly one pick in each: the weekly ledger counts one pick per round into topTens but every
 * pick into the pot, so any other shape stops it summing to zero. Must agree with `WeeklyReportService`'s copy and
 * sit within `team_rosters_draft_round_in_range`, which refuses the rest as a 500.
 */
internal val DRAFT_ROUNDS: IntRange = 1..8

/** Must sit within `team_rosters_ownership_pct_in_range`, which refuses the rest as a 500. */
internal val OWNERSHIP_PCTS: IntRange = 1..100

private fun findRosterProblems(teams: List<ConfirmedTeam>): List<ConfirmedRosterProblem> =
    repeatedTeamNames(teams) + teams.flatMap(::repeatedGolfers) + teams.flatMap(::blankEspnNames) +
        teams.flatMap(::blankNewNames) + teams.flatMap(::draftRoundProblems) + teams.flatMap(::ownershipProblems)

private fun repeatedTeamNames(teams: List<ConfirmedTeam>): List<ConfirmedRosterProblem> =
    teams.firstOfEachRepeat(ConfirmedTeam::teamName)
        .map { team -> ConfirmedRosterProblem.RepeatedTeamName(team.teamName) }

private fun repeatedGolfers(team: ConfirmedTeam): List<ConfirmedRosterProblem> {
    val assignments = team.picks.map(ConfirmedPick::assignment)
    val repeatedExisting =
        assignments.filterIsInstance<GolferAssignment.Existing>()
            .firstOfEachRepeat(GolferAssignment.Existing::golferId)
            .map { repeat -> ConfirmedRosterProblem.RepeatedExistingGolfer(team.teamName, repeat.golferId) }
    val repeatedNew =
        assignments.filterIsInstance<GolferAssignment.New>()
            .mapNotNull { newGolfer -> GolferName.splitName(newGolfer.name)?.storedName() }
            .firstOfEachRepeat(::normalizeName)
            .map { name -> ConfirmedRosterProblem.RepeatedNewGolfer(team.teamName, name) }
    val repeatedEspn =
        assignments.filterIsInstance<GolferAssignment.Espn>()
            .firstOfEachRepeat(GolferAssignment.Espn::espnId)
            .map { repeat -> ConfirmedRosterProblem.RepeatedEspnGolfer(team.teamName, repeat.espnId) }
    return repeatedExisting + repeatedNew + repeatedEspn
}

private fun blankEspnNames(team: ConfirmedTeam): List<ConfirmedRosterProblem> =
    team.picks.map(ConfirmedPick::assignment)
        .filterIsInstance<GolferAssignment.Espn>()
        .filter { athlete -> GolferName.splitName(athlete.name) == null }
        .map { athlete -> ConfirmedRosterProblem.BlankEspnGolferName(team.teamName, athlete.espnId) }

private fun blankNewNames(team: ConfirmedTeam): List<ConfirmedRosterProblem> =
    team.picks.filter { pick ->
        val assignment = pick.assignment
        assignment is GolferAssignment.New && GolferName.splitName(assignment.name) == null
    }.map { pick -> ConfirmedRosterProblem.BlankNewGolferName(team.teamName, pick.round) }

private fun GolferName.storedName(): String = "$firstName $lastName".trim()

private fun GolferAssignment.New.toCreateRequest(): CreateGolferRequest {
    val split = checkNotNull(GolferName.splitName(name)) { "findRosterProblems refuses a blank new-golfer name" }
    return CreateGolferRequest(firstName = split.firstName, lastName = split.lastName)
}

private fun GolferAssignment.Espn.toClaim(target: EspnTarget): PgaPlayerIdClaim? {
    val split = checkNotNull(GolferName.splitName(name)) { "findRosterProblems refuses a blank ESPN name" }
    return when (target) {
        is EspnTarget.IdlessRow -> PgaPlayerIdClaim.Backfill(target.golferId, espnId, split)
        EspnTarget.NewRow -> PgaPlayerIdClaim.Create(espnId, split)
        EspnTarget.Unresolvable -> null
    }
}

/** A lost backfill counts unless it found its own row: its fallback may have inserted the row it returns. */
private fun PgaPlayerIdClaim.insertedInto(written: Map<String, Golfer>): Boolean =
    this !is PgaPlayerIdClaim.Backfill || written.getValue(pgaPlayerId).id != golferId

private data class EspnGolfers(val golferIds: Map<String, GolferId>, val insertedCount: Int)

/** Where an ESPN pick no row holds by id lands, by the rows of its name. */
private sealed interface EspnTarget {
    /** The name's one row, without an id, and no other pick of the payload names it. */
    data class IdlessRow(val golferId: GolferId) : EspnTarget

    /** The name has no row, several, or one holding another id: the preview offered the athlete as its own golfer. */
    data object NewRow : EspnTarget

    /** @see AdminError.UnresolvableEspnGolfers */
    data object Unresolvable : EspnTarget
}

private fun draftRoundProblems(team: ConfirmedTeam): List<ConfirmedRosterProblem> {
    val rounds = team.picks.map(ConfirmedPick::round)
    val outOfRange =
        rounds.filterNot { it in DRAFT_ROUNDS }
            .distinct()
            .map { round -> ConfirmedRosterProblem.RoundOutOfRange(team.teamName, round) }
    val repeated =
        rounds.filter { it in DRAFT_ROUNDS }
            .firstOfEachRepeat { it }
            .map { round -> ConfirmedRosterProblem.RepeatedRound(team.teamName, round) }
    val missing =
        DRAFT_ROUNDS.filterNot { it in rounds }
            .takeIf { it.isNotEmpty() }
            ?.let { absent -> ConfirmedRosterProblem.MissingRounds(team.teamName, absent) }
    return outOfRange + repeated + listOfNotNull(missing)
}

private fun ownershipProblems(team: ConfirmedTeam): List<ConfirmedRosterProblem> =
    team.picks.filterNot { it.ownershipPct in OWNERSHIP_PCTS }
        .map { pick -> ConfirmedRosterProblem.OwnershipPctOutOfRange(team.teamName, pick.round, pick.ownershipPct) }

private fun <T, K> List<T>.firstOfEachRepeat(key: (T) -> K): List<T> {
    val occurrences = groupingBy(key).eachCount()
    return filter { item -> occurrences.getValue(key(item)) > 1 }.distinctBy(key)
}

private data class DatedCalendar(
    val inRange: List<Pair<EspnCalendarEntry, LocalDate>>,
    val unparseable: List<SkippedEntry>,
)

private fun ConfirmedTournamentEntry.toNewTournament(
    seasonId: SeasonId,
    week: String,
): NewTournament =
    NewTournament(
        pgaTournamentId = espnEventId,
        name = name,
        seasonId = seasonId,
        startDate = startDate,
        endDate = endDate,
        week = week,
    )
