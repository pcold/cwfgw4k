package com.cwfgw.seed

import com.cwfgw.AppServices
import com.cwfgw.admin.ConfirmRosterRequest
import com.cwfgw.admin.ConfirmedPick
import com.cwfgw.admin.ConfirmedTeam
import com.cwfgw.admin.GolferAssignment
import com.cwfgw.admin.PickMatch
import com.cwfgw.admin.PreviewTeam
import com.cwfgw.admin.RosterPreviewResult
import com.cwfgw.buildHttpClient
import com.cwfgw.buildServices
import com.cwfgw.config.AppConfig
import com.cwfgw.db.Database
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueId
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.tournaments.TournamentStatus
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.runBlocking

private val log = KotlinLogging.logger {}

/**
 * Development-only seed entrypoint wired into the `seed` Gradle task.
 *
 * Loads the same [AppConfig] as the running server, opens the DB
 * (which also runs every Flyway migration), then composes the real
 * service graph to populate one full 2026 season:
 *   - create league + season
 *   - call [com.cwfgw.admin.AdminService.uploadSeason] (pulls ESPN
 *     calendar for the date range)
 *   - call [com.cwfgw.admin.AdminService.previewRoster] +
 *     [com.cwfgw.admin.AdminService.confirmRoster] (auto-accept
 *     ambiguous, create new golfers for unmatched)
 *   - finalize every tournament ESPN reports as already completed,
 *     stopping at the first non-completed event so chronological
 *     ordering holds
 *
 * The resulting database state mirrors what an admin would produce by
 * clicking through the UI — same writes, no HTTP round-trip.
 */
fun main() {
    log.info { "SeedMain: loading config" }
    val config = AppConfig.load()

    log.info { "SeedMain: starting database (runs Flyway migrations against ${config.db.jdbcUrl})" }
    val database = Database.start(config.db)
    val httpClient = buildHttpClient()

    try {
        val services = buildServices(config, database, httpClient)
        runBlocking { seed(services) }
        log.info { "SeedMain: done" }
    } finally {
        httpClient.close()
        (database.dataSource as? java.io.Closeable)?.close()
    }
}

private suspend fun seed(services: AppServices) {
    val league = services.leagueService.create(CreateLeagueRequest(name = "Cottonwood Fairway Golf Wagering"))
    log.info { "Created league '${league.name}' (${league.id.value})" }
    // Spring uses CSV, summer uses TSV — running both lets ./gradlew seed exercise
    // both parser paths on every run.
    seedSeason(SeedData.spring2026, league.id, services)
    seedSeason(SeedData.summer2026, league.id, services)
}

private suspend fun seedSeason(
    seed: SeasonSeed,
    leagueId: LeagueId,
    services: AppServices,
) {
    log.info { "=== Seeding ${seed.seasonYear} ${seed.name} (season #${seed.seasonNumber}) ===" }
    val season =
        services.seasonService.create(
            CreateSeasonRequest(
                leagueId = leagueId,
                name = seed.name,
                seasonYear = seed.seasonYear,
                seasonNumber = seed.seasonNumber,
            ),
        )
    log.info { "Created season '${season.name}' (${season.id.value})" }

    val upload =
        services.adminService.uploadSeason(season.id, seed.startDate, seed.endDate).orThrow("uploadSeason")
    log.info {
        "Uploaded ${upload.created.size} tournaments from ESPN calendar; ${upload.skipped.size} skipped"
    }

    applyMajorMultipliers(upload.created, seed.majorNamePatterns, services)

    val preview = services.adminService.previewRoster(seed.rosterText).orThrow("previewRoster")
    log.info {
        "Roster preview: ${preview.totalPicks} picks — ${preview.matchedCount} matched, " +
            "${preview.ambiguousCount} ambiguous, ${preview.unmatchedCount} unmatched"
    }

    val confirm =
        services.adminService.confirmRoster(toConfirmRequest(season.id, preview)).orThrow("confirmRoster")
    log.info {
        "Confirmed roster: ${confirm.teamsCreated} teams, ${confirm.golfersCreated} new golfers created"
    }

    finalizeCompleted(season.id, services)
}

/**
 * Convert a preview into a confirm request, auto-accepting whatever the
 * matcher resolved. Ambiguous picks take the first candidate; unmatched
 * picks become `New` golfers (split on first space — operators can clean
 * up via the real UI later).
 */
private fun toConfirmRequest(
    seasonId: SeasonId,
    preview: RosterPreviewResult,
): ConfirmRosterRequest =
    ConfirmRosterRequest(
        seasonId = seasonId,
        teams = preview.teams.map { team -> toConfirmedTeam(team) },
    )

private fun toConfirmedTeam(team: PreviewTeam): ConfirmedTeam =
    ConfirmedTeam(
        teamNumber = team.teamNumber,
        teamName = team.teamName,
        picks =
            team.picks.map { pick ->
                ConfirmedPick(
                    round = pick.round,
                    ownershipPct = pick.ownershipPct,
                    assignment = pickAssignment(pick.match, pick.playerName),
                )
            },
    )

private fun pickAssignment(
    match: PickMatch,
    playerName: String,
): GolferAssignment =
    when (match) {
        is PickMatch.Matched -> GolferAssignment.Existing(match.golferId)
        is PickMatch.Ambiguous -> GolferAssignment.Existing(match.candidates.first().golferId)
        PickMatch.NoMatch -> {
            val parts = playerName.split(' ', limit = 2)
            val (first, last) = if (parts.size >= 2) parts[0] to parts[1] else "" to parts[0]
            GolferAssignment.New(firstName = first, lastName = last)
        }
    }

/**
 * Bump payoutMultiplier to 2 on every imported tournament whose name
 * contains any of the configured patterns (case-insensitive substring
 * match). Skips silently when no patterns are configured for the season.
 */
private suspend fun applyMajorMultipliers(
    created: List<com.cwfgw.tournaments.Tournament>,
    patterns: List<String>,
    services: AppServices,
) {
    if (patterns.isEmpty()) return
    val matchers = patterns.map { it.lowercase() }
    var applied = 0
    for (tournament in created) {
        val nameLower = tournament.name.lowercase()
        if (matchers.any { it in nameLower }) {
            services.tournamentService.update(
                tournament.id,
                com.cwfgw.tournaments.UpdateTournamentRequest(payoutMultiplier = java.math.BigDecimal("2")),
            )
            applied++
            log.info { "Marked '${tournament.name}' as 2x major" }
        }
    }
    log.info { "Applied 2x multiplier to $applied tournament(s) matching $patterns" }
}

/**
 * Walk tournaments in chronological order and finalize each one that's
 * already completed on ESPN. Stops on the first non-completed event so
 * the chronological-order check inside finalizeTournament stays happy.
 */
private suspend fun finalizeCompleted(
    seasonId: SeasonId,
    services: AppServices,
) {
    val tournaments =
        services.tournamentService.list(seasonId, status = null)
            .filter { it.status != TournamentStatus.Completed }
            .sortedBy { it.startDate }
    if (tournaments.isEmpty()) {
        log.info { "No upcoming tournaments to finalize" }
        return
    }
    for (tournament in tournaments) {
        when (val result = services.tournamentOpsService.finalizeTournament(tournament.id)) {
            is Result.Ok -> log.info { "Finalized '${tournament.name}'" }
            is Result.Err -> {
                log.info { "Stopping finalize loop at '${tournament.name}': ${result.error}" }
                return
            }
        }
    }
}

private fun <T, E> Result<T, E>.orThrow(label: String): T =
    when (this) {
        is Result.Ok -> value
        is Result.Err -> error("Seed step '$label' failed: $error")
    }
