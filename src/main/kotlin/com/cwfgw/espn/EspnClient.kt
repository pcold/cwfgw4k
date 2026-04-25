package com.cwfgw.espn

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Fetches PGA leaderboards from ESPN's public scoreboard API.
 *
 * ESPN's JSON uses camelCase keys, so the implementation owns its own [Json]
 * instance rather than sharing the server's SnakeCase-configured one —
 * exposing the response types internally keeps that boundary clean.
 *
 * Scope: one responsibility — fetch, deserialize, and parse. HTTP retries
 * and error policy belong to the caller (EspnService).
 */
interface EspnClient {
    /**
     * Fetch tournaments (completed + in-progress) for a specific date.
     * Throws [EspnUpstreamException] on any non-200 response; callers decide
     * how to translate that at the HTTP boundary.
     */
    suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament>

    /**
     * Fetch ESPN's season calendar — the list of tournaments scheduled for
     * the season. Hits the base scoreboard URL with no date param; the
     * calendar is embedded under `leagues[0].calendar[]` in the response.
     */
    suspend fun fetchCalendar(): List<EspnCalendarEntry>
}

const val ESPN_DEFAULT_BASE_URL: String =
    "https://site.api.espn.com/apis/site/v2/sports/golf/pga/scoreboard"

fun EspnClient(
    httpClient: HttpClient,
    baseUrl: String = ESPN_DEFAULT_BASE_URL,
): EspnClient = HttpEspnClient(httpClient, baseUrl)

private class HttpEspnClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : EspnClient {
    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
        val dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val response = httpClient.get("$baseUrl?dates=$dateStr")
        if (response.status != HttpStatusCode.OK) {
            throw EspnUpstreamException(
                status = response.status.value,
                message = "ESPN returned ${response.status.value} for $dateStr",
            )
        }
        return parseScoreboard(response.bodyAsText())
    }

    override suspend fun fetchCalendar(): List<EspnCalendarEntry> {
        val response = httpClient.get(baseUrl)
        if (response.status != HttpStatusCode.OK) {
            throw EspnUpstreamException(
                status = response.status.value,
                message = "ESPN returned ${response.status.value} for the calendar request",
            )
        }
        return parseCalendar(response.bodyAsText())
    }
}

/**
 * Non-200 response from ESPN. Kept in the client layer so callers can
 * distinguish upstream failures from our own bugs without catching
 * `Throwable`.
 */
class EspnUpstreamException(
    val status: Int,
    message: String,
) : RuntimeException(message)

/**
 * Parse a raw ESPN scoreboard JSON payload into our domain tournaments.
 * Exposed [internal] so tests can exercise it directly without stubbing out
 * the HTTP client.
 */
internal fun parseScoreboard(body: String): List<EspnTournament> =
    espnJson.decodeFromString<EspnScoreboardResponse>(body)
        .events
        .map(::parseEvent)

/**
 * Extract the season calendar from a raw scoreboard response. ESPN embeds
 * the calendar under the first league's `calendar[]` array; entries that
 * are missing any of the three required fields are dropped silently
 * (defensive — ESPN occasionally ships empty placeholder entries).
 */
internal fun parseCalendar(body: String): List<EspnCalendarEntry> =
    espnJson.decodeFromString<EspnScoreboardResponse>(body)
        .leagues
        .firstOrNull()
        ?.calendar
        .orEmpty()
        .mapNotNull { entry ->
            EspnCalendarEntry(
                id = entry.id ?: return@mapNotNull null,
                label = entry.label ?: return@mapNotNull null,
                startDate = entry.startDate ?: return@mapNotNull null,
            )
        }

// ESPN uses camelCase, unlike our server's SnakeCase global Json.
private val espnJson: Json =
    Json {
        ignoreUnknownKeys = true
    }

@Serializable
internal data class EspnScoreboardResponse(
    val events: List<EspnEventJson> = emptyList(),
    val leagues: List<EspnLeagueJson> = emptyList(),
)

@Serializable
internal data class EspnLeagueJson(val calendar: List<EspnCalendarJson> = emptyList())

@Serializable
internal data class EspnCalendarJson(
    val id: String? = null,
    val label: String? = null,
    val startDate: String? = null,
)

@Serializable
internal data class EspnEventJson(
    val id: String,
    val name: String,
    val status: EspnStatusJson? = null,
    val competitions: List<EspnCompetitionJson> = emptyList(),
)

@Serializable
internal data class EspnStatusJson(val type: EspnStatusType? = null)

@Serializable
internal data class EspnStatusType(
    val id: String? = null,
    val completed: Boolean? = null,
)

@Serializable
internal data class EspnCompetitionJson(val competitors: List<EspnCompetitorJson> = emptyList())

@Serializable
internal data class EspnCompetitorJson(
    val id: String? = null,
    val type: String? = null,
    val order: Int? = null,
    val score: String? = null,
    val athlete: EspnNamedJson? = null,
    val team: EspnNamedJson? = null,
    val linescores: List<EspnLinescoreJson> = emptyList(),
    val status: EspnStatusJson? = null,
)

@Serializable
internal data class EspnNamedJson(
    @SerialName("displayName") val displayName: String? = null,
)

@Serializable
internal data class EspnLinescoreJson(val value: Double? = null)

private fun parseEvent(event: EspnEventJson): EspnTournament {
    val competitorRows =
        event.competitions.firstOrNull()?.competitors
            ?: error("ESPN event ${event.id} has no competitions")
    val parsed = competitorRows.flatMapIndexed(::parseCompetitor)
    val isTeamEvent = parsed.any { it.isTeamPartner }
    return EspnTournament(
        espnId = event.id,
        name = event.name,
        completed = event.status?.type?.completed ?: false,
        competitors = assignPositions(parsed),
        isTeamEvent = isTeamEvent,
    )
}

/**
 * Expand one ESPN competitor entry into one or more leaderboard rows. Team
 * entries (`type == "team"`, or names containing "/") become one row per
 * partner sharing the team's score and a synthetic id, so we can later
 * regroup them by [EspnCompetitor.pairKey].
 */
private fun parseCompetitor(
    index: Int,
    json: EspnCompetitorJson,
): List<EspnCompetitor> {
    val entryId = json.id ?: return emptyList()
    val displayName = json.team?.displayName ?: json.athlete?.displayName ?: return emptyList()
    val order = json.order ?: (index + 1)
    val score = json.score
    val scoreToPar = score?.let(::parseScoreToPar)
    val roundScores = json.linescores.mapNotNull { it.value?.toInt() }
    val totalStrokes = if (roundScores.isEmpty()) null else roundScores.sum()
    val status = json.status?.type?.id?.let(EspnStatus::fromCode) ?: EspnStatus.Active
    val isTeamEntry = json.type == "team" || displayName.contains("/")

    return if (isTeamEntry) {
        val pairKey = "team:$entryId"
        displayName
            .split("/")
            .map(String::trim)
            .filter { it.isNotEmpty() }
            .mapIndexed { partnerIdx, partnerName ->
                EspnCompetitor(
                    espnId = "$pairKey:$partnerIdx",
                    name = partnerName,
                    order = order,
                    scoreStr = score,
                    scoreToPar = scoreToPar,
                    totalStrokes = totalStrokes,
                    roundScores = roundScores,
                    position = order,
                    status = status,
                    isTeamPartner = true,
                    pairKey = pairKey,
                )
            }
    } else {
        listOf(
            EspnCompetitor(
                espnId = entryId,
                name = displayName,
                order = order,
                scoreStr = score,
                scoreToPar = scoreToPar,
                totalStrokes = totalStrokes,
                roundScores = roundScores,
                position = order,
                status = status,
                isTeamPartner = false,
                pairKey = null,
            ),
        )
    }
}

private fun parseScoreToPar(score: String): Int? =
    when {
        score == "E" -> 0
        else -> score.removePrefix("+").toIntOrNull()
    }

/**
 * Assign tournament positions from leaderboard ranking.
 *
 * Sorted by `(scoreToPar, order)` so ranking holds even when ESPN's `order`
 * field lags live state (e.g. mid-round Zurich where `order` is tee-time,
 * not live rank). Consecutive competitors sharing a score form a tie group.
 * Position advances by the number of distinct teams in each group — in team
 * events two partners share one slot so ties match ESPN's per-team numbering
 * (1, T2, T4, T8 …). Competitors without a score sink to the bottom.
 */
internal fun assignPositions(competitors: List<EspnCompetitor>): List<EspnCompetitor> {
    val sorted =
        competitors.sortedWith(
            compareBy({ it.scoreToPar ?: Int.MAX_VALUE }, { it.order }),
        )
    val groups = mutableListOf<MutableList<EspnCompetitor>>()
    for (competitor in sorted) {
        val current = groups.lastOrNull()
        val currentScore = current?.firstOrNull()?.scoreStr
        if (current != null && currentScore != null && currentScore == competitor.scoreStr) {
            current += competitor
        } else {
            groups += mutableListOf(competitor)
        }
    }
    val positioned = mutableListOf<EspnCompetitor>()
    var nextPosition = 1
    for (group in groups) {
        val teamCount = group.map { it.pairKey ?: it.espnId }.distinct().size
        positioned += group.map { it.copy(position = nextPosition) }
        nextPosition += teamCount
    }
    return positioned
}
