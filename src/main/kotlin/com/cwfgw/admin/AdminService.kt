package com.cwfgw.admin

import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnUpstreamException
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferService
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.TournamentService
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.LocalDate

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
        for (entry in calendar) {
            when (val outcome = importOne(entry, seasonId, startDate, endDate)) {
                is EntryOutcome.Created -> created += outcome.tournament
                is EntryOutcome.Skipped -> skipped += outcome.entry
                EntryOutcome.OutOfRange -> Unit
            }
        }
        return Result.Ok(SeasonImportResult(created = created, skipped = skipped))
    }

    /**
     * Read-only roster preview. Parses the TSV, matches each pick against
     * existing golfers by full name (case-insensitive), and returns per-pick
     * status plus headline counts. No DB writes — the operator confirms via
     * a follow-up call once the ambiguous/no-match rows are resolved.
     */
    suspend fun previewRoster(rosterText: String): Result<RosterPreviewResult, AdminError> {
        val parsed =
            when (val r = RosterParser.parse(rosterText)) {
                is Result.Ok -> r.value
                is Result.Err -> return Result.Err(AdminError.InvalidRoster(r.error))
            }
        val nameIndex = buildGolferNameIndex(golferService.list(activeOnly = false, search = null))

        val previewTeams = parsed.map { team -> previewTeam(team, nameIndex) }
        return Result.Ok(summarize(previewTeams))
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
        val candidates = nameIndex[playerName.lowercase()].orEmpty()
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
        golfers.groupBy { "${it.firstName} ${it.lastName}".lowercase() }

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

    private suspend fun importOne(
        entry: com.cwfgw.espn.EspnCalendarEntry,
        seasonId: SeasonId,
        startDate: LocalDate,
        endDate: LocalDate,
    ): EntryOutcome {
        val parsedDate =
            parseEspnDate(entry.startDate)
                ?: return EntryOutcome.Skipped(
                    SkippedEntry(entry.id, entry.label, "could not parse ESPN start date '${entry.startDate}'"),
                )
        if (parsedDate !in startDate..endDate) return EntryOutcome.OutOfRange
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
                    startDate = parsedDate,
                    endDate = parsedDate.plusDays(DEFAULT_TOURNAMENT_DAYS),
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
        } catch (e: java.time.format.DateTimeParseException) {
            log.warn(e) { "ESPN sent an unparseable startDate: '$raw'" }
            null
        }

    companion object {
        /** Default tournament length: Thu→Sun = 4 days inclusive (start + 3). Operator can PUT to fix exceptions. */
        private const val DEFAULT_TOURNAMENT_DAYS: Long = 3
        private const val ISO_DATE_LENGTH = 10
    }
}

private sealed interface EntryOutcome {
    data class Created(val tournament: com.cwfgw.tournaments.Tournament) : EntryOutcome

    data class Skipped(val entry: SkippedEntry) : EntryOutcome

    data object OutOfRange : EntryOutcome
}
