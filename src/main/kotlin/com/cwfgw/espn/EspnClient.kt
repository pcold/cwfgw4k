package com.cwfgw.espn

import com.cwfgw.config.EspnConfig
import com.github.benmanes.caffeine.cache.AsyncCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.Ticker
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.io.IOException
import java.nio.channels.UnresolvedAddressException
import java.time.Duration
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val log = KotlinLogging.logger {}

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
     * Throws [EspnUpstreamException] on a non-200 response, on a request
     * that got none, and on a 200 whose body does not parse; callers decide
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

/**
 * ESPN's scoreboard endpoint, served from the `site.web.api` host rather than
 * `site.api`.
 *
 * Both hosts serve byte-identical payloads for this path, but as of 2026-08-04
 * `site.api.espn.com` sits behind an Akamai rule that denies JVM TLS clients at
 * the edge — every request from JSSE gets a 403 `AkamaiGHost` page regardless of
 * User-Agent, headers, or HTTP version, while curl from the same host succeeds.
 * The block is host-wide (all sports), not specific to golf or to our traffic.
 *
 * Warning: this is a client-fingerprint workaround, not a supported contract. If
 * `site.web.api` picks up the same rule, changing the URL again won't help — the
 * fix then is to change the TLS fingerprint itself (e.g. a Conscrypt provider).
 */
const val ESPN_DEFAULT_BASE_URL: String =
    "https://site.web.api.espn.com/apis/site/v2/sports/golf/pga/scoreboard"

fun EspnClient(
    httpClient: HttpClient,
    config: EspnConfig,
    baseUrl: String = ESPN_DEFAULT_BASE_URL,
): EspnClient =
    CachingEspnClient(
        delegate = HttpEspnClient(httpClient, baseUrl),
        scoreboardTtl = Duration.ofSeconds(config.scoreboardCacheTtlSeconds),
        scoreboardMaxSize = config.scoreboardCacheMaxSize,
    )

/**
 * Wraps an [EspnClient] with an in-process Caffeine [AsyncCache] over
 * both [fetchScoreboard] and [fetchCalendar]. One cache per JVM (Cloud
 * Run instance) — parallel live report rebuilds, which bypass the Postgres
 * response cache, still need the same handful of ESPN dates, and this
 * layer dedupes them.
 *
 * Single-flight: when N coroutines call `fetchScoreboard(date)` for the
 * same date concurrently, only the first triggers the underlying ESPN
 * call; the others await the same `CompletableFuture`. That's exactly
 * the thundering-herd fix the report path needs — no more N parallel
 * ESPN calls for the same date.
 *
 * Cache-aside semantics on errors: if the underlying call throws, the
 * future fails, Caffeine evicts the entry, and the next call retries
 * fresh. Errors are not cached.
 *
 * Each event emits a structured log line shaped like the request cache
 * logs:
 *   `cwfgw4k.espn event=hit|miss|error endpoint=scoreboard date=YYYY-MM-DD [duration_ms=N]`
 *
 * Calendar entries use `endpoint=calendar` and `date=-` so the log shape
 * stays uniform.
 */
internal class CachingEspnClient(
    private val delegate: EspnClient,
    scoreboardTtl: Duration,
    scoreboardMaxSize: Long,
    ticker: Ticker = Ticker.systemTicker(),
    loadDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : EspnClient {
    // Long-lived scope tied to the JVM lifetime. SupervisorJob so one
    // failed ESPN load doesn't poison sibling loads.
    private val scope = CoroutineScope(SupervisorJob() + loadDispatcher)

    private val scoreboardCache: AsyncCache<LocalDate, List<EspnTournament>> =
        Caffeine.newBuilder()
            .expireAfterWrite(scoreboardTtl)
            .maximumSize(scoreboardMaxSize)
            .ticker(ticker)
            .buildAsync()
    private val calendarCache: AsyncCache<Unit, List<EspnCalendarEntry>> =
        Caffeine.newBuilder()
            .expireAfterWrite(scoreboardTtl)
            .maximumSize(1)
            .ticker(ticker)
            .buildAsync()

    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
        val precheck = scoreboardCache.getIfPresent(date)
        if (precheck != null && precheck.isDone && !precheck.isCompletedExceptionally) {
            log.info { "cwfgw4k.espn event=hit endpoint=scoreboard date=$date" }
            return precheck.await()
        }
        try {
            return scoreboardCache
                .get(date) { _, _ ->
                    scope.future { loadScoreboard(date) }
                }
                .await()
        } catch (e: EspnUpstreamException) {
            // Caffeine evicts failed futures asynchronously; force eviction
            // synchronously so the next caller retries instead of awaiting
            // the same failed future.
            scoreboardCache.synchronous().invalidate(date)
            throw e
        }
    }

    override suspend fun fetchCalendar(): List<EspnCalendarEntry> {
        val precheck = calendarCache.getIfPresent(Unit)
        if (precheck != null && precheck.isDone && !precheck.isCompletedExceptionally) {
            log.info { "cwfgw4k.espn event=hit endpoint=calendar date=-" }
            return precheck.await()
        }
        try {
            return calendarCache
                .get(Unit) { _, _ ->
                    scope.future { loadCalendar() }
                }
                .await()
        } catch (e: EspnUpstreamException) {
            calendarCache.synchronous().invalidate(Unit)
            throw e
        }
    }

    private suspend fun loadScoreboard(date: LocalDate): List<EspnTournament> {
        val started = System.currentTimeMillis()
        try {
            val fresh = delegate.fetchScoreboard(date)
            log.info {
                "cwfgw4k.espn event=miss endpoint=scoreboard date=$date " +
                    "duration_ms=${System.currentTimeMillis() - started}"
            }
            return fresh
        } catch (e: EspnUpstreamException) {
            log.warn(e) {
                "cwfgw4k.espn event=error endpoint=scoreboard date=$date " +
                    "duration_ms=${System.currentTimeMillis() - started}"
            }
            throw e
        }
    }

    private suspend fun loadCalendar(): List<EspnCalendarEntry> {
        val started = System.currentTimeMillis()
        try {
            val fresh = delegate.fetchCalendar()
            log.info {
                "cwfgw4k.espn event=miss endpoint=calendar date=- " +
                    "duration_ms=${System.currentTimeMillis() - started}"
            }
            return fresh
        } catch (e: EspnUpstreamException) {
            log.warn(e) {
                "cwfgw4k.espn event=error endpoint=calendar date=- " +
                    "duration_ms=${System.currentTimeMillis() - started}"
            }
            throw e
        }
    }
}

private class HttpEspnClient(
    private val httpClient: HttpClient,
    private val baseUrl: String,
) : EspnClient {
    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
        val dateStr = date.format(DateTimeFormatter.BASIC_ISO_DATE)
        val body = fetchBody(url = "$baseUrl?dates=$dateStr", subject = dateStr)
        return parsedOrMalformed(subject = dateStr) { parseScoreboard(body) }
    }

    override suspend fun fetchCalendar(): List<EspnCalendarEntry> {
        val subject = "the calendar request"
        val body = fetchBody(url = baseUrl, subject = subject)
        return parsedOrMalformed(subject = subject) { parseCalendar(body) }
    }

    private suspend fun fetchBody(
        url: String,
        subject: String,
    ): String =
        try {
            httpClient.get(url).bodyIfOk(subject)
        } catch (e: IOException) {
            throw noResponse(subject, e)
        } catch (e: UnresolvedAddressException) {
            // A DNS failure is an IllegalArgumentException, not an IOException, so it needs its own arm.
            throw noResponse(subject, e)
        }
}

private suspend fun HttpResponse.bodyIfOk(subject: String): String {
    if (status != HttpStatusCode.OK) {
        throw EspnUpstreamException(status = status.value, message = "ESPN returned ${status.value} for $subject")
    }
    return bodyAsText()
}

private fun noResponse(
    subject: String,
    cause: Exception,
): EspnUpstreamException =
    EspnUpstreamException(
        failure = EspnUpstreamFailure.NoResponse,
        message = "ESPN did not respond for $subject: $cause",
        cause = cause,
    )

// Not IllegalArgumentException, SerializationException's supertype: that would report a bug of ours as ESPN's.
private inline fun <T> parsedOrMalformed(
    subject: String,
    parse: () -> T,
): T =
    try {
        parse()
    } catch (e: SerializationException) {
        throw EspnUpstreamException(
            failure = EspnUpstreamFailure.MalformedBody,
            message = "ESPN sent a malformed body for $subject",
            cause = e,
        )
    }

/**
 * Kept in the client layer so callers can distinguish upstream failures from our own bugs without
 * catching `Throwable`. A transport failure or an unparseable 200 has to arrive as this type too: it is the
 * only one the cache logs and evicts synchronously, and the only one a caller falls back on.
 */
class EspnUpstreamException(
    val failure: EspnUpstreamFailure,
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {
    constructor(status: Int, message: String) : this(EspnUpstreamFailure.BadStatus(status), message)
}

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
    // A SerializationException, not error(): the client refuses this body as malformed rather than failing.
    val competitorRows =
        event.competitions.firstOrNull()?.competitors
            ?: throw SerializationException("ESPN event ${event.id} has no competitions")
    val parsed = competitorRows.flatMapIndexed(::parseCompetitor)
    val isTeamEvent = parsed.any { it.isTeamPartner }
    val completed = event.status?.type?.completed ?: false
    return EspnTournament(
        espnId = event.id,
        name = event.name,
        completed = completed,
        competitors = assignPositions(parsed, completed = completed),
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
 *
 * Once [completed] is true a tie for the lead is resolved by playoff rather
 * than shared — see [breakLeadTie]. Ties below the lead remain genuine shared
 * finishes.
 */
internal fun assignPositions(
    competitors: List<EspnCompetitor>,
    completed: Boolean,
): List<EspnCompetitor> {
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
    val rankedGroups: List<List<EspnCompetitor>> = if (completed) breakLeadTie(groups) else groups
    val teamCounts = rankedGroups.map { group -> group.map { it.pairKey ?: it.espnId }.distinct().size }
    val groupPositions = teamCounts.runningFold(1, Int::plus)
    return rankedGroups
        .zip(groupPositions) { group, position -> group.map { it.copy(position = position) } }
        .flatten()
}

/**
 * Resolve a tie for the lead at a completed event.
 *
 * In stroke play a tie for the lead is decided by a sudden-death playoff, so a
 * completed event never has co-leaders — exactly one entry finishes first. ESPN
 * reflects this only in `order` (1 = playoff winner, 2 = runner-up); its `score`
 * field is unreliable here, sometimes folding the winner's extra-hole birdie in
 * (−13 vs −12) and sometimes leaving both level (−12 vs −12). So when the top
 * group holds more than one team we split it into one group per team ranked by
 * `order`, giving the winner sole 1st and the rest the following places. Groups
 * below the lead are untouched — those are genuine shared finishes (T2, T3, …).
 */
private fun breakLeadTie(
    groups: List<List<EspnCompetitor>>,
): List<List<EspnCompetitor>> {
    val lead = groups.firstOrNull() ?: return groups
    val leadTeams = lead.groupBy { it.pairKey ?: it.espnId }
    if (leadTeams.size <= 1) return groups
    val splitLead = leadTeams.values.sortedBy { team -> team.minOf { it.order } }
    return splitLead + groups.drop(1)
}
