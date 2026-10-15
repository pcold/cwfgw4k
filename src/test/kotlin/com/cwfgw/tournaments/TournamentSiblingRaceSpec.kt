package com.cwfgw.tournaments

import com.cwfgw.db.LOCK_TIMEOUT_MS
import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.PostgresHarness
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.TournamentOpsError.Action
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jooq.impl.DSL
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val EVENT_ESPN_ID = "401580999"
private const val FINISHER_ESPN_ID = "9478"
private val EARLIER_WEEK: LocalDate = LocalDate.parse("2026-04-16")

/** A holder never released lets go before its waiter's lock_timeout would fire. */
private const val HOLD_CEILING_MS: Long = LOCK_TIMEOUT_MS - 2_000
private val LOCK_WAIT_DEADLINE: Duration = Duration.ofMillis(HOLD_CEILING_MS - 1_000)

/** Each race pauses one transaction past its gate, then starts a contender that must wait for it, not slip by. */
class TournamentSiblingRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("a finalize waits for a reset of the week before it, then refuses as out of order") {
        val (earlier, later) = tx.update { seedConsecutiveWeeks() }
        val pause = Pause()
        val pausedReset = opsService(tx, tournamentRepository = pause.afterFindAll())

        coroutineScope {
            val reset = async { pausedReset.resetTournament(earlier.id) }
            val (finalize, waited) = pause.contend(postgres) { async { opsService(tx).finalizeTournament(later.id) } }

            reset.await().orFail().status shouldBe TournamentStatus.Upcoming
            val blocking = listOf(checkNotNull(tx.get { TournamentRepository().findById(earlier.id) }))
            finalize.await() shouldBe Result.Err(TournamentOpsError.OutOfOrder(Action.Finalize, blocking))
            waited.shouldBeTrue()
        }
    }

    test("a reset waits for a finalize of the week after it, then refuses as out of order") {
        val (earlier, later) = tx.update { seedConsecutiveWeeks() }
        val pause = Pause()
        val pausedFinalize = opsService(tx, tournamentRepository = pause.afterFindAll())

        coroutineScope {
            val finalize = async { pausedFinalize.finalizeTournament(later.id) }
            val (reset, waited) = pause.contend(postgres) { async { opsService(tx).resetTournament(earlier.id) } }

            val finalized = finalize.await().orFail()
            finalized.status shouldBe TournamentStatus.Completed
            reset.await() shouldBe Result.Err(TournamentOpsError.OutOfOrder(Action.Reset, listOf(finalized)))
            waited.shouldBeTrue()
        }
    }

    test("an unlinked finalize holds the sibling it imports into, so that sibling's reset clears the import after") {
        val (unlinked, sibling) = tx.update { seedUnlinkedWithSibling() }
        val pause = Pause()
        val pausedFinalize = opsService(tx, golferRepository = pause.afterGolferFindAll())

        coroutineScope {
            val finalize = async { pausedFinalize.finalizeTournament(unlinked.id) }
            val (reset, waited) = pause.contend(postgres) { async { opsService(tx).resetTournament(sibling.id) } }

            finalize.await().orFail()
            reset.await().orFail().status shouldBe TournamentStatus.Upcoming
            tx.get { TournamentRepository().getResults(sibling.id) }.shouldBeEmpty()
            waited.shouldBeTrue()
        }
    }

    test("a start date moved during the ESPN fetch refuses the finalize, which imports nothing") {
        val event = tx.update { seedLinkedEvent() }
        val moved = event.startDate.plusWeeks(1)
        val move = UpdateTournamentRequest(startDate = moved, endDate = moved.plusDays(3))
        val ops = opsService(tx, duringFetch = { tx.update { TournamentRepository().update(event.id, move) } })

        ops.finalizeTournament(event.id) shouldBe
            Result.Err(TournamentOpsError.StartDateChanged(fetchedFor = event.startDate, startDate = moved))
        tx.get { GolferRepository().findByPgaPlayerId(FINISHER_ESPN_ID) }.shouldBeNull()
        tx.get { TournamentRepository().findById(event.id) }?.status shouldBe TournamentStatus.Upcoming
    }
})

/** Stops one transaction where its hook fires, until [contend] has seen a contender wait on a lock or finish. */
private class Pause {
    private val reached = CompletableDeferred<Unit>()
    private val release = CountDownLatch(1)

    fun afterFindAll(): TournamentRepository = FindAllPausingTournamentRepository(TournamentRepository(), this)

    fun afterGolferFindAll(): GolferRepository = FindAllPausingGolferRepository(GolferRepository(), this)

    fun hold() {
        reached.complete(Unit)
        release.await(HOLD_CEILING_MS, TimeUnit.MILLISECONDS)
    }

    /** The contender, and whether it was seen waiting on a lock while the paused transaction held. */
    suspend fun <T> contend(
        postgres: PostgresHarness,
        contender: () -> Deferred<T>,
    ): Pair<Deferred<T>, Boolean> {
        withTimeout(HOLD_CEILING_MS) { reached.await() }
        val started = contender()
        val waited =
            try {
                postgres.awaitLockWait(started)
            } finally {
                release.countDown()
            }
        return started to waited
    }
}

/** Finalize and reset each call findAll once, for the ordering check against their siblings. */
private class FindAllPausingTournamentRepository(
    private val delegate: TournamentRepository,
    private val pause: Pause,
) : TournamentRepository by delegate {
    context(ctx: TransactionContext)
    override fun findAll(filter: TournamentFilter): List<Tournament> = delegate.findAll(filter).also { pause.hold() }
}

/** The import's first read, after finalize has found the sibling it writes and before it writes there. */
private class FindAllPausingGolferRepository(
    private val delegate: GolferRepository,
    private val pause: Pause,
) : GolferRepository by delegate {
    context(ctx: TransactionContext)
    override fun findAll(search: String?): List<Golfer> = delegate.findAll(search).also { pause.hold() }
}

/**
 * True once a session in this spec's database waits on a lock; false if [pending] finishes first or the deadline
 * passes. The waiter runs through [Transactor], whose connection the spec can't name, so the database scopes the
 * search.
 */
private suspend fun PostgresHarness.awaitLockWait(pending: Job): Boolean {
    val deadline = Instant.now().plus(LOCK_WAIT_DEADLINE)
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

/** Every service reads one scoreboard: [EVENT_ESPN_ID], completed, on the seeded tournaments' start date. */
private fun opsService(
    tx: Transactor,
    tournamentRepository: TournamentRepository = TournamentRepository(),
    golferRepository: GolferRepository = GolferRepository(),
    duringFetch: suspend () -> Unit = {},
): TournamentOpsService {
    val scoreboard = FakeEspnClient(mapOf(LocalDate.parse("2026-04-23") to listOf(finalScoreboard())))
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
                client = FetchHookedEspnClient(scoreboard, duringFetch),
                seasonRepository = SeasonRepository(),
                golferRepository = golferRepository,
                teamRepository = TeamRepository(),
                tournamentRepository = TournamentRepository(),
                linkRepository = TournamentLinkRepository(),
                tx = tx,
            ),
        tx = tx,
    )
}

private class FetchHookedEspnClient(
    private val delegate: EspnClient,
    private val duringFetch: suspend () -> Unit,
) : EspnClient by delegate {
    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
        duringFetch()
        return delegate.fetchScoreboard(date)
    }
}

context(ctx: WriteTransactionContext)
private fun seedLinkedEvent(): Tournament =
    checkNotNull(TournamentRepository().findById(seedTournament(pgaTournamentId = EVENT_ESPN_ID)))

/** The earlier week is completed and the later one, linked to the scoreboard's event, is not. */
context(ctx: WriteTransactionContext)
private fun seedConsecutiveWeeks(): Pair<Tournament, Tournament> {
    val later = seedLinkedEvent()
    val earlier =
        TournamentRepository().create(
            CreateTournamentRequest(
                name = "RBC Heritage",
                seasonId = later.seasonId,
                startDate = EARLIER_WEEK,
                endDate = EARLIER_WEEK.plusDays(3),
            ),
        )
    val completed = UpdateTournamentRequest(status = TournamentStatus.Completed)
    return checkNotNull(TournamentRepository().update(earlier.id, completed)) to later
}

/** Two seasons: a sibling in another league's season is held by no season lock the unlinked finalize takes. */
context(ctx: WriteTransactionContext)
private fun seedUnlinkedWithSibling(): Pair<Tournament, Tournament> {
    val unlinked = checkNotNull(TournamentRepository().findById(seedTournament()))
    val sibling = seedLinkedEvent()
    val completed = UpdateTournamentRequest(status = TournamentStatus.Completed)
    return unlinked to checkNotNull(TournamentRepository().update(sibling.id, completed))
}

private fun finalScoreboard(): EspnTournament =
    EspnTournament(
        espnId = EVENT_ESPN_ID,
        name = "Zurich Classic",
        completed = true,
        competitors =
            listOf(
                EspnCompetitor(
                    espnId = FINISHER_ESPN_ID,
                    name = "Scottie Scheffler",
                    order = 1,
                    scoreStr = "-12",
                    scoreToPar = -12,
                    totalStrokes = 276,
                    roundScores = listOf(69, 69, 69, 69),
                    position = 1,
                    status = EspnStatus.Active,
                    isTeamPartner = false,
                    pairKey = null,
                ),
            ),
        isTeamEvent = false,
    )
