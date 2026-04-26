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
    val rosterTsv: String,
)

object SeedData {
    val summer2026: SeasonSeed by lazy {
        SeasonSeed(
            name = "Summer",
            seasonYear = 2026,
            seasonNumber = 2,
            startDate = LocalDate.parse("2026-04-16"),
            endDate = LocalDate.parse("2026-07-12"),
            rosterTsv = loadResource("/seed/2026-summer-roster.tsv"),
        )
    }

    private fun loadResource(path: String): String =
        SeedData::class.java.getResource(path)?.readText()
            ?: error("Seed resource not found on classpath: $path")
}
