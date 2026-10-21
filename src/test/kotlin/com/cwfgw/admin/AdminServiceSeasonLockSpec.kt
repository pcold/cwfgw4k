package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.Pause
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedSeason
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.NewTournament
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

/**
 * Without the season lock each confirm still waits, but on a row lock taken after its gate read, so it ends in a
 * 409 or an FK error rather than the outcome pinned here.
 */
class AdminServiceSeasonLockSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    fun service(): AdminService =
        AdminService(
            tx = tx,
            seasonRepository = SeasonRepository(),
            tournamentRepository = TournamentRepository(),
            espnService = testEspnService(client = FakeEspnClient(), tx = tx),
            golferRepository = GolferRepository(),
            teamRepository = TeamRepository(),
        )

    test("a schedule confirm waits out an import holding its season and skips the event that import linked") {
        val seasonId = tx.update { seedSeason() }
        val masters =
            ConfirmedTournamentEntry(
                espnEventId = "e-1",
                name = "Masters",
                startDate = LocalDate.parse("2026-04-09"),
                endDate = LocalDate.parse("2026-04-12"),
            )
        val pause = Pause()

        val (linked, imported, waited) =
            coroutineScope {
                val holding =
                    async {
                        tx.update {
                            TournamentRepository().lockSeason(seasonId)
                            TournamentRepository().create(
                                NewTournament(
                                    pgaTournamentId = masters.espnEventId,
                                    name = masters.name,
                                    seasonId = seasonId,
                                    startDate = masters.startDate,
                                    endDate = masters.endDate,
                                ),
                            ).also { pause.hold() }
                        }
                    }
                val (confirming, waited) =
                    pause.contend(postgres) { async { service().confirmSeasonSchedule(seasonId, listOf(masters)) } }
                Triple(holding.await(), confirming.await(), waited)
            }

        val result = imported.orFail()
        result.created.shouldBeEmpty()
        result.skipped.map { it.reason } shouldContainExactly listOf("already linked to tournament ${linked.id.value}")
        waited.shouldBeTrue()
    }

    test("a roster confirm waits out a season delete and refuses the season as not found") {
        val seasonId = tx.update { seedSeason() }
        val request = ConfirmRosterRequest(seasonId = seasonId, teams = listOf(fullTeam(number = 1, name = "BROWN")))
        val pause = Pause()

        val (confirmed, waited) =
            coroutineScope {
                val deleting = async { tx.update { SeasonRepository().delete(seasonId).also { pause.hold() } } }
                val (confirming, waited) = pause.contend(postgres) { async { service().confirmRoster(request) } }
                deleting.await().shouldBeTrue()
                confirming.await() to waited
            }

        confirmed shouldBe Result.Err(AdminError.SeasonNotFound(seasonId))
        waited.shouldBeTrue()
    }
})
