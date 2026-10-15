package com.cwfgw.datahealth

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueId
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTombstone
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

private val NOTHING_FOUND: DataHealthReport =
    DataHealthReport(DataHealthCheck(emptyList()), DataHealthCheck(emptyList()), DataHealthCheck(emptyList()))

context(ctx: WriteTransactionContext)
private fun seedLeague(name: String): LeagueId = LeagueRepository().create(CreateLeagueRequest(name = name)).id

context(ctx: WriteTransactionContext)
private fun seedSeason(
    leagueId: LeagueId,
    seasonYear: Int,
    seasonNumber: Int,
): SeasonId =
    SeasonRepository().create(
        CreateSeasonRequest(
            leagueId = leagueId,
            name = "$seasonYear Season $seasonNumber",
            seasonYear = seasonYear,
            seasonNumber = seasonNumber,
        ),
    ).id

context(ctx: WriteTransactionContext)
private fun seedGolfer(
    firstName: String,
    lastName: String,
    pgaPlayerId: String? = null,
): DataHealthGolfer {
    val golfer =
        GolferRepository().create(
            CreateGolferRequest(pgaPlayerId = pgaPlayerId, firstName = firstName, lastName = lastName),
        )
    return DataHealthGolfer(golfer.id, golfer.firstName, golfer.lastName, golfer.pgaPlayerId)
}

context(ctx: WriteTransactionContext)
private fun seedTeamRostering(
    seasonId: SeasonId,
    vararg golfers: DataHealthGolfer,
) {
    val teams = TeamRepository()
    val team = teams.create(seasonId, CreateTeamRequest(ownerName = "Owner", teamName = "Team ${UUID.randomUUID()}"))
    golfers.forEach { golfer -> teams.addToRoster(team.id, AddToRosterRequest(golferId = golfer.id)) }
}

class DataHealthRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val service = DataHealthService(DataHealthRepository(), tx)

    test("a twin pair rostered only in an earlier season of the same league and year is no current problem") {
        tx.update {
            val league = seedLeague("Castlewood")
            val earlier = seedSeason(league, seasonYear = 2026, seasonNumber = 1)
            val current = seedSeason(league, seasonYear = 2026, seasonNumber = 2)
            seedTeamRostering(earlier, seedGolfer("Si Woo", "Kim"))
            seedTeamRostering(current, seedGolfer("Scottie", "Scheffler", pgaPlayerId = "9478"))
            seedGolfer("Si Woo", "Kim", pgaPlayerId = "7081")
        }

        service.report() shouldBe NOTHING_FOUND
    }

    test("a current-season twin pair reports its rostered member and the twin holding the ESPN id") {
        val (rostered, twin) =
            tx.update {
                val season = seedSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val rostered = seedGolfer("Si Woo", "Kim")
                seedTeamRostering(season, rostered)
                seedGolfer(" si woo ", "KIM")
                rostered to seedGolfer("SI WOO", " Kim ", pgaPlayerId = "7081")
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
                val first = seedSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val second = seedSeason(seedLeague("Shoreline"), seasonYear = 2025, seasonNumber = 1)
                seedTeamRostering(first, seedGolfer("Scottie", "Scheffler", pgaPlayerId = "9478"))
                val rostered = seedGolfer("Thomas", "Detry")
                seedTeamRostering(second, rostered)
                rostered to seedGolfer("Thomas", "Detry", pgaPlayerId = "4837")
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
                val season = seedSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val rostered = seedGolfer("Ben", "Griffin")
                seedTeamRostering(season, rostered)
                val tombstone = seedGolfer("Ben", "Griffin", pgaPlayerId = "4404992")
                seedTombstone(tombstone.id)
                rostered to tombstone
            }

        service.report().rosteredTwins shouldBe
            DataHealthCheck(listOf(RosteredTwinGroup("ben griffin", listOf(rostered), listOf(tombstone))))
    }

    test("a blank first name is counted on its own and never makes a twin, and a tombstoned one is left out") {
        val (rosteredBlank, unrosteredBlank) =
            tx.update {
                val season = seedSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val rosteredBlank = seedGolfer("", "Griffin")
                seedTeamRostering(season, rosteredBlank)
                seedGolfer("Ben", "Griffin", pgaPlayerId = "4404992")
                seedTombstone(seedGolfer(" ", "Griffin", pgaPlayerId = "1").id)
                rosteredBlank to seedGolfer("  ", "Hojgaard")
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
                val season = seedSeason(seedLeague("Castlewood"), seasonYear = 2026, seasonNumber = 1)
                val aberg = seedGolfer("Ludvig", "Aberg")
                val rai = seedGolfer("Aaron", "Rai")
                seedTeamRostering(season, rai, aberg, seedGolfer("Scottie", "Scheffler", pgaPlayerId = "9478"))
                aberg to rai
            }

        service.report() shouldBe
            DataHealthReport(
                rosteredTwins = DataHealthCheck(emptyList()),
                rosteredWithoutEspnId = DataHealthCheck(listOf(aberg, rai)),
                blankFirstNames = DataHealthCheck(emptyList()),
            )
    }
})
