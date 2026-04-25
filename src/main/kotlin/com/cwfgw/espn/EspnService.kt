package com.cwfgw.espn

import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.TeamService
import com.cwfgw.tournaments.CreateTournamentResultRequest
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.UpdateTournamentRequest
import io.github.oshai.kotlinlogging.KotlinLogging
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
) {
    /**
     * Fetch ESPN's season calendar. Pass-through to the client; surfaced on
     * the service so routes stay service-only and don't pull [EspnClient]
     * into their wiring.
     */
    suspend fun fetchCalendar(): List<EspnCalendarEntry> = client.fetchCalendar()

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
        markCompletedIfNeeded(tournament, event)

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
                )
            }
        if (requests.isNotEmpty()) tournamentService.importResults(tournamentId, requests)
    }

    private suspend fun markCompletedIfNeeded(
        tournament: Tournament,
        event: EspnTournament,
    ) {
        if (event.completed && tournament.status != STATUS_COMPLETED) {
            tournamentService.update(tournament.id, UpdateTournamentRequest(status = STATUS_COMPLETED))
        }
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

    companion object {
        private const val STATUS_COMPLETED = "completed"
    }
}

private fun matchesFullName(
    golfer: Golfer,
    fullName: String,
): Boolean =
    "${golfer.firstName} ${golfer.lastName}".equals(fullName, ignoreCase = true) ||
        golfer.lastName.equals(fullName, ignoreCase = true)

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
