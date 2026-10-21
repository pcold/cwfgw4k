package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedSeason
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.NewTournament
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentFilter
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class AdminServiceScheduleImportSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    fun service(tournamentRepository: TournamentRepository = TournamentRepository()): AdminService =
        AdminService(
            tx = tx,
            seasonRepository = SeasonRepository(),
            tournamentRepository = tournamentRepository,
            espnService = testEspnService(client = FakeEspnClient(), tx = tx),
            golferRepository = GolferRepository(),
            teamRepository = TeamRepository(),
        )

    suspend fun scheduleOf(seasonId: SeasonId): List<Tournament> =
        tx.get { TournamentRepository().findAll(TournamentFilter(seasonId = seasonId)) }

    test("an import that fails on its second tournament persists neither") {
        val seasonId = tx.update { seedSeason() }
        val entries = listOf(entry("e-1", "2026-01-15"), entry("e-2", "2026-01-22"))

        shouldThrow<IllegalStateException> {
            service(SecondCreateRefused(TournamentRepository())).confirmSeasonSchedule(seasonId, entries)
        }

        scheduleOf(seasonId).shouldBeEmpty()
    }

    test("an ESPN event repeated in one import is created once and the repeat skipped as linked to it") {
        val seasonId = tx.update { seedSeason() }

        val result =
            service().confirmSeasonSchedule(seasonId, listOf(entry("e-1", "2026-01-15"), entry("e-1", "2026-01-22")))
                .orFail()

        val created = result.created.map(Tournament::pgaTournamentId)
        created shouldContainExactly listOf("e-1")
        result.skipped.map { it.reason } shouldContainExactly
            listOf("already linked to tournament ${result.created.single().id.value}")
        scheduleOf(seasonId).map(Tournament::pgaTournamentId) shouldContainExactly listOf("e-1")
    }
})

private fun entry(
    espnEventId: String,
    startDate: String,
): ConfirmedTournamentEntry =
    ConfirmedTournamentEntry(
        espnEventId = espnEventId,
        name = "Event $espnEventId",
        startDate = LocalDate.parse(startDate),
        endDate = LocalDate.parse(startDate).plusDays(3),
    )

/** Stands in for any failure after the first insert, which no refusal of the flow's own can stage. */
private class SecondCreateRefused(
    delegate: TournamentRepository,
) : TournamentRepository by delegate {
    private val inner = delegate
    private val creates = AtomicInteger()

    context(ctx: WriteTransactionContext)
    override fun create(request: NewTournament): Tournament =
        if (creates.incrementAndGet() == 2) error("second insert refused") else inner.create(request)
}
