package com.cwfgw.admin

import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnUpstreamException
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamService
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.TournamentService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.text.Normalizer
import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.time.temporal.WeekFields

private val log = KotlinLogging.logger {}

/**
 * Operator tooling for bulk season + roster setup.
 *
 * `uploadSeason` is the season-creation flow: given a date range, fetch
 * ESPN's calendar, filter to events whose start date falls in the range,
 * and create one tournament per event with `pgaTournamentId` already
 * linked. Tournaments come back at multiplier 1.0; the UI then lets the
 * operator edit multipliers via the existing `PUT /api/v1/tournaments/{id}`
 * route, so this service deliberately doesn't accept a per-tournament
 * multiplier override on import — it would just duplicate that path.
 *
 * Re-runs are safe: any ESPN entry already linked to a tournament lands in
 * the `skipped` list with a clear reason, never overwrites or duplicates.
 */
class AdminService(
    private val seasonService: SeasonService,
    private val tournamentService: TournamentService,
    private val espnService: EspnService,
    private val golferService: GolferService,
    private val teamService: TeamService,
) {
    suspend fun uploadSeason(
        seasonId: SeasonId,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Result<SeasonImportResult, AdminError> {
        seasonService.get(seasonId) ?: return Result.Err(AdminError.SeasonNotFound(seasonId))

        val calendar =
            try {
                espnService.fetchCalendar()
            } catch (e: EspnUpstreamException) {
                log.warn(e) { "ESPN calendar fetch failed during admin season import (status ${e.status})" }
                return Result.Err(AdminError.UpstreamUnavailable(e.status))
            }

        val created = mutableListOf<com.cwfgw.tournaments.Tournament>()
        val skipped = mutableListOf<SkippedEntry>()
        // ESPN's calendar response doesn't carry a "week N" concept, so we
        // synthesize one from chronologically-sorted in-range entries. Two
        // tournaments in the same ISO calendar week get suffixed "Na" / "Nb"
        // (the 'b' is conventionally the alternate-field event, but we can't
        // tell which is which from ESPN — operators correct via the UI).
        val datedEntries = parseAndFilterByRange(calendar, startDate, endDate, skipped)
        for ((entry, date, week) in assignWeeks(datedEntries)) {
            when (val outcome = importOne(entry, date, week, seasonId)) {
                is EntryOutcome.Created -> created += outcome.tournament
                is EntryOutcome.Skipped -> skipped += outcome.entry
            }
        }
        return Result.Ok(SeasonImportResult(created = created, skipped = skipped))
    }

    private fun parseAndFilterByRange(
        calendar: List<com.cwfgw.espn.EspnCalendarEntry>,
        startDate: LocalDate,
        endDate: LocalDate,
        skipped: MutableList<SkippedEntry>,
    ): List<Pair<com.cwfgw.espn.EspnCalendarEntry, LocalDate>> =
        calendar.mapNotNull { entry ->
            val parsed = parseEspnDate(entry.startDate)
            if (parsed == null) {
                skipped += SkippedEntry(entry.id, entry.label, "could not parse ESPN start date '${entry.startDate}'")
                null
            } else if (parsed !in startDate..endDate) {
                null
            } else {
                entry to parsed
            }
        }

    private fun assignWeeks(
        entries: List<Pair<com.cwfgw.espn.EspnCalendarEntry, LocalDate>>,
    ): List<Triple<com.cwfgw.espn.EspnCalendarEntry, LocalDate, String>> {
        val grouped =
            entries.sortedBy { it.second }
                .groupBy { (_, date) -> date.weekKey() }
                .values
                .toList()
        return grouped.flatMapIndexed { weekIndex, group ->
            val weekNumber = weekIndex + 1
            group.mapIndexed { positionInWeek, (entry, date) ->
                val label = if (group.size == 1) "$weekNumber" else "$weekNumber${'a' + positionInWeek}"
                Triple(entry, date, label)
            }
        }
    }

    /** Year-aware ISO week key so the year boundary doesn't collide week 52 with week 1. */
    private fun LocalDate.weekKey(): Pair<Int, Int> =
        get(WeekFields.ISO.weekBasedYear()) to get(WeekFields.ISO.weekOfWeekBasedYear())

    /**
     * Roster preview with persisted-pool fallback. Parses the TSV, matches
     * each pick against existing golfers by normalized full name, and — if
     * any picks fail to match — pulls ESPN's recent-active player pool and
     * persists any newly-discovered golfers (with their `pga_player_id`)
     * before re-matching. The DB grows opportunistically so subsequent
     * previews start from a richer base and only need to fish ESPN for
     * truly new players.
     *
     * The route still returns a read-shaped [RosterPreviewResult]; callers
     * confirm via [confirmRoster] once ambiguous / no-match rows are
     * resolved. The persisted golfer rows stay valid even if the operator
     * later abandons the preview.
     */
    suspend fun previewRoster(rosterText: String): Result<RosterPreviewResult, AdminError> {
        val parsed =
            when (val r = RosterParser.parse(rosterText)) {
                is Result.Ok -> r.value
                is Result.Err -> return Result.Err(AdminError.InvalidRoster(r.error))
            }

        val initialIndex = buildGolferNameIndex(golferService.list(activeOnly = false, search = null))
        val initialMatches = parsed.flatMap { it.picks }.map { matchPick(it.playerName, initialIndex) }
        val anyUnmatched = initialMatches.any { it is PickMatch.NoMatch }

        val finalIndex =
            if (anyUnmatched) {
                fishEspnPoolInto(initialIndex)
            } else {
                initialIndex
            }

        val previewTeams = parsed.map { team -> previewTeam(team, finalIndex) }
        return Result.Ok(summarize(previewTeams))
    }

    /**
     * Pull ESPN's recent-active player pool and persist any athlete whose
     * `pga_player_id` isn't already in the DB. Returns a fresh name index
     * over the now-larger golfer set. Best-effort: if ESPN is unavailable
     * (the service itself absorbs upstream failures into an empty list),
     * we just return the original index unchanged so the matcher falls
     * back to "no match" rather than failing the whole preview.
     */
    private suspend fun fishEspnPoolInto(currentIndex: Map<String, List<Golfer>>): Map<String, List<Golfer>> {
        val athletes = espnService.fetchActivePlayers()
        if (athletes.isEmpty()) return currentIndex
        val existingIds = currentIndex.values.flatten().mapNotNull { it.pgaPlayerId }.toSet()
        val newAthletes = athletes.filter { it.espnId !in existingIds }
        if (newAthletes.isEmpty()) return currentIndex
        log.info { "Persisting ${newAthletes.size} new ESPN athletes into golfers table" }
        for (athlete in newAthletes) {
            val (firstName, lastName) = splitName(athlete.name)
            golferService.create(
                CreateGolferRequest(
                    pgaPlayerId = athlete.espnId,
                    firstName = firstName,
                    lastName = lastName,
                ),
            )
        }
        return buildGolferNameIndex(golferService.list(activeOnly = false, search = null))
    }

    private fun splitName(fullName: String): Pair<String, String> {
        val trimmed = fullName.trim()
        val lastSpace = trimmed.lastIndexOf(' ')
        if (lastSpace == -1) return trimmed to ""
        return trimmed.substring(0, lastSpace) to trimmed.substring(lastSpace + 1)
    }

    private fun previewTeam(
        team: ParsedTeam,
        nameIndex: Map<String, List<Golfer>>,
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
                        match = matchPick(pick.playerName, nameIndex),
                    )
                },
        )

    private fun matchPick(
        playerName: String,
        nameIndex: Map<String, List<Golfer>>,
    ): PickMatch {
        val candidates = nameIndex[normalizeName(playerName)].orEmpty()
        return when (candidates.size) {
            0 -> PickMatch.NoMatch
            1 -> {
                val golfer = candidates.single()
                PickMatch.Matched(golferId = golfer.id, golferName = golfer.fullName())
            }
            else ->
                PickMatch.Ambiguous(
                    candidates =
                        candidates.map { golfer ->
                            GolferCandidate(golferId = golfer.id, name = golfer.fullName())
                        },
                )
        }
    }

    private fun Golfer.fullName(): String = "$firstName $lastName"

    private fun buildGolferNameIndex(golfers: List<Golfer>): Map<String, List<Golfer>> =
        golfers.groupBy { normalizeName("${it.firstName} ${it.lastName}") }

    /**
     * Lowercase + accent-fold a name so "Niklas Nørgaard-Petersen",
     * "Niklas Norgaard-Petersen", and "niklas  nørgaard-PETERSEN" all
     * land in the same bucket.
     *
     * Two-step folding: explicit substitution of letters that have no NFD
     * canonical decomposition (Scandinavian ø/å/æ, Polish ł, German ß,
     * Icelandic ð, Croatian đ — the ones that bit us in production), then
     * standard NFD-decompose-and-strip-combining-marks for the regular
     * accented Latin range (é, ñ, ü, etc.).
     *
     * Predictable substitution rather than edit-distance fuzzy matching:
     * "Tom Kim" must not silently match "Tim Kim".
     */
    private fun normalizeName(name: String): String {
        val folded =
            NON_DECOMPOSABLE_LETTERS.entries.fold(name.trim()) { acc, (from, to) ->
                acc.replace(from, to)
            }
        return Normalizer.normalize(folded, Normalizer.Form.NFD)
            .replace(DIACRITIC_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .lowercase()
    }

    private fun summarize(teams: List<PreviewTeam>): RosterPreviewResult {
        val allPicks = teams.flatMap { it.picks }
        var matched = 0
        var ambiguous = 0
        var unmatched = 0
        for (pick in allPicks) {
            when (pick.match) {
                is PickMatch.Matched -> matched++
                is PickMatch.Ambiguous -> ambiguous++
                PickMatch.NoMatch -> unmatched++
            }
        }
        return RosterPreviewResult(
            teams = teams,
            totalPicks = allPicks.size,
            matchedCount = matched,
            ambiguousCount = ambiguous,
            unmatchedCount = unmatched,
        )
    }

    /**
     * Persist an operator-confirmed roster: create one Team per
     * `ConfirmedTeam`, create new golfers for any `New` assignments, and
     * add a roster entry per pick. Validates all `Existing` golfer ids
     * up front so the operator sees every bad reference at once instead
     * of fixing one and retrying. Writes are not transactional across
     * teams — a downstream failure mid-write leaves partial state, which
     * is acceptable because the validation pass catches the realistic
     * errors and anything later would be unexpected (DB-level).
     */
    suspend fun confirmRoster(request: ConfirmRosterRequest): Result<RosterUploadResult, AdminError> {
        seasonService.get(request.seasonId) ?: return Result.Err(AdminError.SeasonNotFound(request.seasonId))

        val missingGolferIds = findMissingGolferIds(request.teams)
        if (missingGolferIds.isNotEmpty()) return Result.Err(AdminError.GolferIdsNotFound(missingGolferIds))

        var golfersCreated = 0
        val createdTeams = mutableListOf<Team>()
        for (team in request.teams) {
            val (createdTeam, newGolfers) = persistConfirmedTeam(request.seasonId, team)
            createdTeams += createdTeam
            golfersCreated += newGolfers
        }
        return Result.Ok(
            RosterUploadResult(
                teamsCreated = createdTeams.size,
                golfersCreated = golfersCreated,
                teams = createdTeams,
            ),
        )
    }

    private suspend fun persistConfirmedTeam(
        seasonId: SeasonId,
        team: ConfirmedTeam,
    ): Pair<Team, Int> {
        val createdTeam =
            teamService.create(
                seasonId = seasonId,
                request =
                    CreateTeamRequest(
                        ownerName = team.teamName,
                        teamName = team.teamName,
                        teamNumber = team.teamNumber,
                    ),
            )
        var newGolfers = 0
        for (pick in team.picks) {
            val golferId =
                when (val assignment = pick.assignment) {
                    is GolferAssignment.Existing -> assignment.golferId
                    is GolferAssignment.New -> {
                        newGolfers++
                        golferService.create(
                            CreateGolferRequest(firstName = assignment.firstName, lastName = assignment.lastName),
                        ).id
                    }
                }
            teamService.addToRoster(
                teamId = createdTeam.id,
                request =
                    AddToRosterRequest(
                        golferId = golferId,
                        acquiredVia = ROSTER_ACQUIRED_VIA_DRAFT,
                        draftRound = pick.round,
                        ownershipPct = BigDecimal(pick.ownershipPct),
                    ),
            )
        }
        return createdTeam to newGolfers
    }

    private suspend fun findMissingGolferIds(teams: List<ConfirmedTeam>): List<GolferId> {
        val referencedIds =
            teams.asSequence()
                .flatMap { it.picks.asSequence() }
                .mapNotNull { (it.assignment as? GolferAssignment.Existing)?.golferId }
                .toSet()
        return referencedIds.filter { golferService.get(it) == null }
    }

    private suspend fun importOne(
        entry: com.cwfgw.espn.EspnCalendarEntry,
        startDate: LocalDate,
        week: String,
        seasonId: SeasonId,
    ): EntryOutcome {
        val existing = tournamentService.findByPgaTournamentId(entry.id)
        if (existing != null) {
            return EntryOutcome.Skipped(
                SkippedEntry(entry.id, entry.label, "already linked to tournament ${existing.id.value}"),
            )
        }
        val created =
            tournamentService.create(
                CreateTournamentRequest(
                    pgaTournamentId = entry.id,
                    name = entry.label,
                    seasonId = seasonId,
                    startDate = startDate,
                    endDate = startDate.plusDays(DEFAULT_TOURNAMENT_DAYS),
                    week = week,
                ),
            )
        return EntryOutcome.Created(created)
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

        private val DIACRITIC_REGEX: Regex = Regex("\\p{InCombiningDiacriticalMarks}+")
        private val WHITESPACE_REGEX: Regex = Regex("\\s+")

        // Letters with no NFD canonical decomposition that we still want
        // to fold for name matching. Order doesn't matter — replace passes
        // are independent.
        private val NON_DECOMPOSABLE_LETTERS: Map<String, String> =
            mapOf(
                "ø" to "o", "Ø" to "O",
                "å" to "a", "Å" to "A",
                "æ" to "ae", "Æ" to "AE",
                "œ" to "oe", "Œ" to "OE",
                "ß" to "ss",
                "ł" to "l", "Ł" to "L",
                "đ" to "d", "Đ" to "D",
                "ð" to "d", "Ð" to "D",
                "þ" to "th", "Þ" to "Th",
            )
    }
}

private sealed interface EntryOutcome {
    data class Created(val tournament: com.cwfgw.tournaments.Tournament) : EntryOutcome

    data class Skipped(val entry: SkippedEntry) : EntryOutcome
}
