package com.cwfgw.seasons

import com.cwfgw.testing.migrateSchemaTo
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import org.jooq.DSLContext
import java.util.UUID

private const val SCHEMA = "side_bet_backfill"

private fun DSLContext.insertSeason(
    leagueId: UUID,
    seasonNumber: Int,
): UUID {
    val seasonId = UUID.randomUUID()
    execute(
        "INSERT INTO $SCHEMA.seasons (id, league_id, name, season_year, season_number) " +
            "VALUES (?, ?, 'Season', 2025, ?)",
        seasonId,
        leagueId,
        seasonNumber,
    )
    return seasonId
}

private fun DSLContext.sideBetRounds(seasonId: UUID): List<Int> =
    fetch("SELECT round FROM $SCHEMA.season_rule_side_bet_rounds WHERE season_id = ? ORDER BY round", seasonId)
        .getValues(0, Int::class.javaObjectType)

class SideBetRoundsBackfillMigrationSpec : FunSpec({

    val postgres = postgresHarness()

    test("V022 gives a season with no side-bet rounds 5-8, and leaves one with its own rounds as it was") {
        postgres.migrateSchemaTo(SCHEMA, "21")
        val leagueId = UUID.randomUUID()
        postgres.dsl.execute("INSERT INTO $SCHEMA.leagues (id, name) VALUES (?, 'Castlewood')", leagueId)
        val readingTheFallback = postgres.dsl.insertSeason(leagueId, seasonNumber = 1)
        val ownRounds = postgres.dsl.insertSeason(leagueId, seasonNumber = 2)
        listOf(2, 3).forEach { round ->
            postgres.dsl.execute(
                "INSERT INTO $SCHEMA.season_rule_side_bet_rounds (season_id, round) VALUES (?, ?)",
                ownRounds,
                round,
            )
        }

        postgres.migrateSchemaTo(SCHEMA, "22")

        postgres.dsl.sideBetRounds(readingTheFallback) shouldContainExactly listOf(5, 6, 7, 8)
        postgres.dsl.sideBetRounds(ownRounds) shouldContainExactly listOf(2, 3)
    }
})
