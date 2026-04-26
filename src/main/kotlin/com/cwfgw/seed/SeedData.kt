package com.cwfgw.seed

import java.time.LocalDate

/**
 * Canonical dev-seed payload. The roster TSV is loaded from a resource
 * file rather than embedded as a string literal so it stays diffable
 * and editable without touching source. Date range covers the actual
 * 2026 summer PGA window so [com.cwfgw.admin.AdminService.uploadSeason]
 * pulls real ESPN events when the seed runs.
 */
data class SeasonSeed(
    val name: String,
    val seasonYear: Int,
    val seasonNumber: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val rosterText: String,
    /**
     * Substrings (case-insensitive) that mark a tournament as a 2x major.
     * After uploadSeason creates the calendar, every imported tournament
     * whose name contains any of these substrings gets its
     * payoutMultiplier bumped to 2.
     */
    val majorNamePatterns: List<String> = emptyList(),
)

object SeedData {
    /**
     * Spring 2026 — covers the West Coast swing through the Masters.
     * Roster ships as CSV so the seed flow exercises the parser's CSV path
     * end-to-end (the tab-loss-on-paste class of bug that prompted the
     * dual-format support).
     */
    val spring2026: SeasonSeed by lazy {
        SeasonSeed(
            name = "Spring",
            seasonYear = 2026,
            seasonNumber = 1,
            startDate = LocalDate.parse("2026-01-15"),
            endDate = LocalDate.parse("2026-04-12"),
            rosterText = loadResource("/seed/2026-spring-roster.csv"),
            majorNamePatterns = listOf("Players", "Masters"),
        )
    }

    /**
     * Summer 2026 — post-Masters PGA stretch. Roster ships as TSV so the
     * other parser path is exercised on the same seed run.
     */
    val summer2026: SeasonSeed by lazy {
        SeasonSeed(
            name = "Summer",
            seasonYear = 2026,
            seasonNumber = 2,
            startDate = LocalDate.parse("2026-04-16"),
            endDate = LocalDate.parse("2026-07-12"),
            rosterText = loadResource("/seed/2026-summer-roster.tsv"),
            majorNamePatterns = listOf("U.S. Open", "Open Championship"),
        )
    }

    private fun loadResource(path: String): String =
        SeedData::class.java.getResource(path)?.readText()
            ?: error("Seed resource not found on classpath: $path")
}
