package com.cwfgw.tournaments

import com.cwfgw.db.LOCK_TIMEOUT_MS
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTeams
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val EVENT_ESPN_ID = "401580999"
private const val ROSTERED_WINNER_ESPN_ID = "9478"
private const val UNSEEN_RUNNER_UP_ESPN_ID = "3470"

/** A holder never released lets go before its waiter's lock_timeout would fire. */
private const val HOLD_CEILING_MS: Long = LOCK_TIMEOUT_MS - 2_000
private const val WAITER_PROBE_MS: Long = 300

/** Finalize reads, fetches from ESPN with no transaction open, then writes under the tournament's lock. */
class TournamentFinalizeRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("a season deleted during the ESPN fetch answers TournamentNotFound and imports nothing") {
        val event = tx.update { seedRosteredEvent() }
        val ops = opsService(tx, event, duringFetch = { tx.update { SeasonRepository().delete(event.seasonId) } })

        ops.finalizeAndReport(event.id) shouldBe Result.Err(TournamentOpsError.TournamentNotFound(event.id))

        tx.get { GolferRepository().findByPgaPlayerId(UNSEEN_RUNNER_UP_ESPN_ID) }.shouldBeNull()
    }

    test("a second finalize waits for the first's transaction, then refuses the tournament it completed") {
        val event = tx.update { seedRosteredEvent() }
        val hold = LockHold()

        val (first, second) =
            coroutineScope {
                val first = async { opsService(tx, event, hold.pausing()).finalizeAndReport(event.id) }
                val second = hold.whileHeld { async { opsService(tx, event).finalizeAndReport(event.id) } }
                first.await() to second.await()
            }

        first.orFail().tournament.status shouldBe TournamentStatus.Completed
        second shouldBe Result.Err(TournamentOpsError.TournamentFinalized(event.id))
        tx.get { TournamentRepository().getResults(event.id) } shouldHaveSize 2
        tx.get { GolferRepository().findByPgaPlayerId(UNSEEN_RUNNER_UP_ESPN_ID) }?.lastName shouldBe "McIlroy"
        tx.get { ScoringRepository().getScores(event.seasonId, event.id) } shouldHaveSize 1
    }

    test("a season delete waits for a finalize holding its tournament, and neither deadlocks") {
        val event = tx.update { seedRosteredEvent() }
        val hold = LockHold()

        coroutineScope {
            val finalize = async { opsService(tx, event, hold.pausing()).finalizeAndReport(event.id) }
            val delete = hold.whileHeld { async { tx.update { SeasonRepository().delete(event.seasonId) } } }

            finalize.await().orFail().tournament.status shouldBe TournamentStatus.Completed
            delete.await() shouldBe true
        }
        tx.get { TournamentRepository().findById(event.id) }.shouldBeNull()
    }

    test("a finalize waits for a reset holding its tournament") {
        val event = tx.update { seedRosteredEvent() }
        val hold = LockHold()

        coroutineScope {
            val reset = async { opsService(tx, event, hold.pausing()).resetTournament(event.id) }
            val finalize = hold.whileHeld { async { opsService(tx, event).finalizeAndReport(event.id) } }

            reset.await().orFail().status shouldBe TournamentStatus.Upcoming
            finalize.await().orFail().tournament.status shouldBe TournamentStatus.Completed
        }
    }

    test("findByIdForUpdate keeps a second locker waiting until the first transaction ends") {
        val event = tx.update { seedRosteredEvent() }
        val hold = LockHold()

        coroutineScope {
            val holder = async { tx.update { hold.pausing().findByIdForUpdate(event.id) } }
            val waiter = hold.whileHeld { async { tx.update { TournamentRepository().findByIdForUpdate(event.id) } } }
            waiter.await() shouldBe holder.await()
        }
    }
})

/** One transaction stops just after its findByIdForUpdate until [whileHeld] has probed the contender. */
private class LockHold {
    private val locked = CompletableDeferred<Unit>()
    private val release = CountDownLatch(1)

    fun pausing(): TournamentRepository = PausingTournamentRepository(TournamentRepository(), locked, release)

    /** Fails unless [contender] is still waiting [WAITER_PROBE_MS] after the hold took its lock. */
    suspend fun <T> whileHeld(contender: () -> Deferred<T>): Deferred<T> {
        withTimeout(HOLD_CEILING_MS) { locked.await() }
        val started = contender()
        try {
            withTimeoutOrNull(WAITER_PROBE_MS) { started.await() }.shouldBeNull()
        } finally {
            release.countDown()
        }
        return started
    }
}

private class PausingTournamentRepository(
    private val delegate: TournamentRepository,
    private val locked: CompletableDeferred<Unit>,
    private val release: CountDownLatch,
) : TournamentRepository by delegate {
    context(ctx: WriteTransactionContext)
    override fun findByIdForUpdate(id: TournamentId): Tournament? {
        val tournament = delegate.findByIdForUpdate(id)
        locked.complete(Unit)
        release.await(HOLD_CEILING_MS, TimeUnit.MILLISECONDS)
        return tournament
    }
}

/** [duringFetch] runs where finalize has read the tournament and holds no transaction. */
private fun opsService(
    tx: Transactor,
    event: Tournament,
    tournamentRepository: TournamentRepository = TournamentRepository(),
    duringFetch: suspend () -> Unit = {},
): TournamentOpsService {
    val scoreboard = FakeEspnClient(mapOf(event.startDate to listOf(finalScoreboard())))
    return TournamentOpsService(
        tournamentRepository = tournamentRepository,
        scoringService =
            ScoringService(
                repository = ScoringRepository(),
                seasonRepository = SeasonRepository(),
                tournamentRepository = TournamentRepository(),
                teamRepository = TeamRepository(),
            ),
        scoringRepository = ScoringRepository(),
        espnService =
            testEspnService(
                client = PausingEspnClient(scoreboard, duringFetch),
                seasonRepository = SeasonRepository(),
                golferRepository = GolferRepository(),
                teamRepository = TeamRepository(),
                tournamentRepository = TournamentRepository(),
                linkRepository = TournamentLinkRepository(),
                tx = tx,
            ),
        tx = tx,
    )
}

private class PausingEspnClient(
    private val delegate: EspnClient,
    private val duringFetch: suspend () -> Unit,
) : EspnClient by delegate {
    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
        duringFetch()
        return delegate.fetchScoreboard(date)
    }
}

/** The runner-up has no golfer row, so the import creates one: a write a finalize that refuses must not make. */
context(ctx: WriteTransactionContext)
private fun seedRosteredEvent(): Tournament {
    val event = checkNotNull(TournamentRepository().findById(seedTournament(pgaTournamentId = EVENT_ESPN_ID)))
    val winner =
        GolferRepository().create(
            CreateGolferRequest(pgaPlayerId = ROSTERED_WINNER_ESPN_ID, firstName = "Scottie", lastName = "Scheffler"),
        )
    TeamRepository().addToRoster(seedTeams(event.seasonId).first(), AddToRosterRequest(golferId = winner.id))
    return event
}

private fun finalScoreboard(): EspnTournament =
    EspnTournament(
        espnId = EVENT_ESPN_ID,
        name = "Zurich Classic",
        completed = true,
        competitors =
            listOf(
                finisher(ROSTERED_WINNER_ESPN_ID, "Scottie Scheffler", position = 1),
                finisher(UNSEEN_RUNNER_UP_ESPN_ID, "Rory McIlroy", position = 2),
            ),
        isTeamEvent = false,
    )

private fun finisher(
    espnId: String,
    name: String,
    position: Int,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = "-12",
        scoreToPar = -12,
        totalStrokes = 276,
        roundScores = listOf(69, 69, 69, 69),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )
