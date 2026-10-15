package com.cwfgw.espn

import com.cwfgw.db.LOCK_TIMEOUT_MS
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.PostgresHarness
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.CreateTournamentResultRequest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jooq.impl.DSL
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** A holder never released lets go before its waiter's lock_timeout would fire. */
private const val HOLD_CEILING_MS: Long = LOCK_TIMEOUT_MS - 2_000

/** Under [HOLD_CEILING_MS], so the probe gives up before the paused import releases itself. */
private const val LOCK_WAIT_DEADLINE_MS: Long = HOLD_CEILING_MS - 1_000

/** A roster confirm inserts new athletes in espnId order, so an import inserting them in any other can deadlock it. */
class EspnServiceCreateOrderSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("an import creates its new golfers in espnId order and persists results in leaderboard order") {
        val rory = existingGolfer(pgaPlayerId = "250", firstName = "Rory", lastName = "McIlroy")
        val fixture = RecordingImport(initialGolfers = listOf(rory))

        fixture.persistImport(
            competitor(espnId = "300", name = "Ludvig Aberg", position = 1),
            competitor(espnId = "250", name = "Rory McIlroy", position = 2),
            competitor(espnId = "050", name = "  ", position = 3),
            competitor(espnId = "100", name = "Tommy Fleetwood", position = 4),
            competitor(espnId = "200", name = "Xander Schauffele", position = 5),
        )

        fixture.golfers.insertedPgaPlayerIds shouldContainExactly listOf("100", "200", "300")
        fixture.persistedRows() shouldContainExactly listOf("300" to 1, "250" to 2, "100" to 4, "200" to 5)
    }

    test("a competitor ESPN lists twice resolves both rows to the one golfer the import creates") {
        val fixture = RecordingImport()

        fixture.persistImport(
            competitor(espnId = "100", name = "Tommy Fleetwood", position = 1),
            competitor(espnId = "100", name = "Tommy Fleetwood", position = 2),
        )

        with(noopTransactionContext) { fixture.golfers.findAll(search = null) } shouldHaveSize 1
        fixture.persistedRows() shouldContainExactly listOf("100" to 1, "100" to 2)
    }

    test("an import and a confirm inserting the same new athletes both commit rather than deadlock") {
        val tournament = tx.update { checkNotNull(TournamentRepository().findById(seedTournament("evt-401"))) }
        val hold = InsertHold(postgres)
        val service =
            testEspnService(
                client = FakeEspnClient(),
                seasonRepository = SeasonRepository(),
                golferRepository = hold.pausing(),
                teamRepository = TeamRepository(),
                tournamentRepository = TournamentRepository(),
                linkRepository = TournamentLinkRepository(),
                tx = tx,
            )
        val leaderboard =
            listOf(
                competitor(espnId = "300", name = "Ludvig Aberg", position = 1),
                competitor(espnId = "100", name = "Tommy Fleetwood", position = 2),
            )

        coroutineScope {
            val import = async { tx.update { service.persistImportIn(tournament, mastersEvent(leaderboard)) } }
            val confirm = hold.whileHeld { async { tx.update { insertAsConfirmDoes(leaderboard) } } }
            import.await()
            confirm.await()
        }

        tx.get { GolferRepository().findAll(search = null) }.map { it.pgaPlayerId } shouldContainExactlyInAnyOrder
            listOf("100", "300")
    }
})

private val TOURNAMENT_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000209"))
private val START_DATE = LocalDate.parse("2026-04-09")

private class RecordingImport(initialGolfers: List<Golfer> = emptyList()) {
    val golfers = RecordingGolferRepository(FakeGolferRepository(initial = initialGolfers))
    val tournaments = RecordingTournamentRepository(FakeTournamentRepository())
    val service =
        testEspnService(client = FakeEspnClient(), golferRepository = golfers, tournamentRepository = tournaments)
}

private fun RecordingImport.persistImport(vararg competitors: EspnCompetitor) =
    with(noopTransactionContext) { service.persistImportIn(mastersTournament(), mastersEvent(competitors.toList())) }

private fun RecordingImport.persistedRows(): List<Pair<String?, Int?>> =
    with(noopTransactionContext) {
        tournaments.upserts.map { request -> golfers.findById(request.golferId)?.pgaPlayerId to request.position }
    }

private class RecordingGolferRepository(
    private val delegate: GolferRepository,
) : GolferRepository by delegate {
    val insertedPgaPlayerIds = mutableListOf<String?>()

    context(ctx: WriteTransactionContext)
    override fun findOrCreate(request: CreateGolferRequest): Golfer {
        insertedPgaPlayerIds += request.pgaPlayerId
        return delegate.findOrCreate(request)
    }
}

private class RecordingTournamentRepository(
    private val delegate: TournamentRepository,
) : TournamentRepository by delegate {
    val upserts = mutableListOf<CreateTournamentResultRequest>()

    context(ctx: WriteTransactionContext)
    override fun upsertResult(
        tournamentId: TournamentId,
        request: CreateTournamentResultRequest,
    ): TournamentResult {
        upserts += request
        return delegate.upsertResult(tournamentId, request)
    }
}

/** The import stops just after its first golfer insert until [whileHeld] has seen the contender wait on a lock. */
private class InsertHold(private val postgres: PostgresHarness) {
    private val inserted = CompletableDeferred<Unit>()
    private val release = CountDownLatch(1)

    fun pausing(): GolferRepository = PausingGolferRepository(GolferRepository(), inserted, release)

    /** Fails unless [contender] blocks on a lock: one that is merely slow never staged the race. */
    suspend fun <T> whileHeld(contender: () -> Deferred<T>): Deferred<T> {
        withTimeout(HOLD_CEILING_MS) { inserted.await() }
        val started = contender()
        try {
            postgres.awaitLockWait(started) shouldBe true
        } finally {
            release.countDown()
        }
        return started
    }
}

private class PausingGolferRepository(
    private val delegate: GolferRepository,
    private val inserted: CompletableDeferred<Unit>,
    private val release: CountDownLatch,
) : GolferRepository by delegate {
    context(ctx: WriteTransactionContext)
    override fun findOrCreate(request: CreateGolferRequest): Golfer {
        val golfer = delegate.findOrCreate(request)
        if (inserted.complete(Unit)) release.await(HOLD_CEILING_MS, TimeUnit.MILLISECONDS)
        return golfer
    }
}

/** The waiter runs through [Transactor], whose connection the spec can't name, so the database scopes the search. */
private suspend fun PostgresHarness.awaitLockWait(pending: Job): Boolean {
    val deadline = Instant.now().plusMillis(LOCK_WAIT_DEADLINE_MS)
    while (!pending.isCompleted && Instant.now().isBefore(deadline)) {
        val waiting =
            dsl.fetchExists(
                DSL.selectOne().from("pg_stat_activity")
                    .where("datname = current_database() and wait_event_type = 'Lock'"),
            )
        if (waiting) return true
        delay(10)
    }
    return false
}

/** Stands in for `AdminService.resolveEspnGolfers`, which inserts a confirm's new athletes this way. */
context(ctx: WriteTransactionContext)
private fun insertAsConfirmDoes(athletes: List<EspnCompetitor>) {
    athletes.sortedBy(EspnCompetitor::espnId).forEach { athlete ->
        GolferRepository().findOrCreate(
            CreateGolferRequest(pgaPlayerId = athlete.espnId, firstName = "Confirmed", lastName = athlete.name),
        )
    }
}

private fun existingGolfer(
    pgaPlayerId: String,
    firstName: String,
    lastName: String,
): Golfer =
    Golfer(
        id = GolferId(UUID.randomUUID()),
        pgaPlayerId = pgaPlayerId,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        updatedAt = Instant.EPOCH,
    )

private fun competitor(
    espnId: String,
    name: String,
    position: Int,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = "-10",
        scoreToPar = -10,
        totalStrokes = 278,
        roundScores = emptyList(),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

private fun mastersEvent(competitors: List<EspnCompetitor>): EspnTournament =
    EspnTournament(
        espnId = "evt-401",
        name = "Masters",
        completed = true,
        competitors = competitors,
        isTeamEvent = false,
    )

private fun mastersTournament(): Tournament =
    Tournament(
        id = TOURNAMENT_ID,
        pgaTournamentId = "evt-401",
        name = "Masters",
        seasonId = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa")),
        startDate = START_DATE,
        endDate = START_DATE.plusDays(3),
        courseName = null,
        status = TournamentStatus.Upcoming,
        purseAmount = null,
        payoutMultiplier = BigDecimal("1.0000"),
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )
