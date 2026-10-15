package com.cwfgw.datahealth

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.Golfer
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedGolfer
import com.cwfgw.testing.seedLeague
import com.cwfgw.testing.seedLeagueSeason
import com.cwfgw.testing.seedRosteredTeam
import com.cwfgw.testing.seedTombstone
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private val NOTHING_FOUND: DataHealthReport =
    DataHealthReport(DataHealthCheck(emptyList()), DataHealthCheck(emptyList()), DataHealthCheck(emptyList()))

private fun Golfer.asReported(): DataHealthGolfer = DataHealthGolfer(id, firstName, lastName, pgaPlayerId)

class DataHealthRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val service = DataHealthService(DataHealthRepository(), tx)

    test("a twin pair rostered only in an earlier season of the same league and year is no current problem") {
        tx.update {
            val league = seedLeague("Castlewood")
            val earlier = seedLeagueSeason(league, seasonYear = 2026, seasonNumber = 1)
            val current = seedLeagueSeason(league, seasonYear = 2026, seasonNumber = 2)
            seedRosteredTeam(earlier, listOf(seedGolfer("Si Woo", "Kim").id))
            seedRosteredTeam(current, listOf(seedGolfer("Scottie", "Scheffler", pgaPlayerId = "9478").id))
            seedGolfer("Si Woo", "Kim", pgaPlayerId = "7081")
        }

        service.report() shouldBe NOTHING_FOUND
    }

    test("a current-season twin pair reports its rostered member and the twin holding the ESPN id") {
        val (rostered, twin) =
            tx.update {
                val season = seedLeagueSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val rostered = seedGolfer("Si Woo", "Kim")
                seedRosteredTeam(season, listOf(rostered.id))
                seedGolfer(" si woo ", "KIM")
                rostered.asReported() to seedGolfer("SI WOO", " Kim ", pgaPlayerId = "7081").asReported()
            }

        service.report() shouldBe
            DataHealthReport(
                rosteredTwins =
                    DataHealthCheck(listOf(RosteredTwinGroup("si woo kim", listOf(rostered), listOf(twin)))),
                rosteredWithoutEspnId = DataHealthCheck(listOf(rostered)),
                blankFirstNames = DataHealthCheck(emptyList()),
            )
    }

    test("a twin pair rostered in a second league's current season is reported beside the first league's") {
        val (rostered, twin) =
            tx.update {
                val first = seedLeagueSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val second = seedLeagueSeason(seedLeague("Shoreline"), seasonYear = 2025, seasonNumber = 1)
                seedRosteredTeam(first, listOf(seedGolfer("Scottie", "Scheffler", pgaPlayerId = "9478").id))
                val rostered = seedGolfer("Thomas", "Detry")
                seedRosteredTeam(second, listOf(rostered.id))
                rostered.asReported() to seedGolfer("Thomas", "Detry", pgaPlayerId = "4837").asReported()
            }

        service.report() shouldBe
            DataHealthReport(
                rosteredTwins =
                    DataHealthCheck(listOf(RosteredTwinGroup("thomas detry", listOf(rostered), listOf(twin)))),
                rosteredWithoutEspnId = DataHealthCheck(listOf(rostered)),
                blankFirstNames = DataHealthCheck(emptyList()),
            )
    }

    test("a tombstone holding the ESPN id is still the twin that takes the rostered golfer's results") {
        val (rostered, tombstone) =
            tx.update {
                val season = seedLeagueSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val rostered = seedGolfer("Ben", "Griffin")
                seedRosteredTeam(season, listOf(rostered.id))
                val tombstone = seedGolfer("Ben", "Griffin", pgaPlayerId = "4404992")
                seedTombstone(tombstone.id)
                rostered.asReported() to tombstone.asReported()
            }

        service.report().rosteredTwins shouldBe
            DataHealthCheck(listOf(RosteredTwinGroup("ben griffin", listOf(rostered), listOf(tombstone))))
    }

    test("a blank first name is counted on its own and never makes a twin, and a tombstoned one is left out") {
        val (rosteredBlank, unrosteredBlank) =
            tx.update {
                val season = seedLeagueSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val rosteredBlank = seedGolfer("", "Griffin")
                seedRosteredTeam(season, listOf(rosteredBlank.id))
                seedGolfer("Ben", "Griffin", pgaPlayerId = "4404992")
                seedTombstone(seedGolfer(" ", "Griffin", pgaPlayerId = "1").id)
                rosteredBlank.asReported() to seedGolfer("  ", "Hojgaard").asReported()
            }

        service.report() shouldBe
            DataHealthReport(
                rosteredTwins = DataHealthCheck(emptyList()),
                rosteredWithoutEspnId = DataHealthCheck(listOf(rosteredBlank)),
                blankFirstNames = DataHealthCheck(listOf(rosteredBlank, unrosteredBlank)),
            )
    }

    test("a rostered golfer with no ESPN id and no twin is reported by the id check alone") {
        val (aberg, rai) =
            tx.update {
                val season = seedLeagueSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val aberg = seedGolfer("Ludvig", "Aberg")
                val rai = seedGolfer("Aaron", "Rai")
                val scheffler = seedGolfer("Scottie", "Scheffler", pgaPlayerId = "9478")
                seedRosteredTeam(season, listOf(rai.id, aberg.id, scheffler.id))
                aberg.asReported() to rai.asReported()
            }

        service.report() shouldBe
            DataHealthReport(
                rosteredTwins = DataHealthCheck(emptyList()),
                rosteredWithoutEspnId = DataHealthCheck(listOf(aberg, rai)),
                blankFirstNames = DataHealthCheck(emptyList()),
            )
    }
})
