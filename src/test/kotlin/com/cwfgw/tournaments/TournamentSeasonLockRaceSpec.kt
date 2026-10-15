package com.cwfgw.tournaments

import com.cwfgw.db.LOCK_TIMEOUT_MS
import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonOpsService
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.PostgresHarness
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTeams
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.TournamentOpsError.Action
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
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
private val EVENT_WEEK: LocalDate = LocalDate.parse("2026-04-23")
private val EARLIER_WEEK: LocalDate = LocalDate.parse("2026-04-16")

/** A holder never released lets go before its waiter's lock_timeout would fire. */
private const val HOLD_CEILING_MS: Long = LOCK_TIMEOUT_MS - 2_000
private val LOCK_WAIT_DEADLINE: Duration = Duration.ofMillis(HOLD_CEILING_MS - 1_000)

/** Writers beside finalize and reset that change what their sibling-order checks read, paused mid-transaction. */
class TournamentSeasonLockRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("a season clean-results waits for a finalize in its season, then clears what that finalize wrote") {
        val event = tx.update { seedRosteredEvent() }
        val pause = WriterPause()
        val pausedFinalize = opsService(tx, tournamentRepository = pause.afterFindAll())
        val seasonOps = SeasonOpsService(SeasonRepository(), TournamentRepository(), ScoringRepository(), tx)

        coroutineScope {
            val finalize = async { pausedFinalize.finalizeTournament(event.id) }
            val (clean, waited) = pause.contend(postgres) { async { seasonOps.cleanSeasonResults(event.seasonId) } }

            finalize.await().orFail().status shouldBe TournamentStatus.Completed
            clean.await()?.resultsDeleted shouldBe 1
            waited.shouldBeTrue()
        }
        tx.get { TournamentRepository().findById(event.id) }?.status shouldBe TournamentStatus.Upcoming
        tx.get { TournamentRepository().getResults(event.id) }.shouldBeEmpty()
        tx.get { ScoringRepository().getScores(event.seasonId, event.id) }.shouldBeEmpty()
    }

    test("a finalize waits for a PUT reopening the week before it, then refuses as out of order") {
        val (earlier, later) = tx.update { seedConsecutiveWeeks() }
        val pause = WriterPause()
        val reopen = UpdateTournamentRequest(status = TournamentStatus.Upcoming)

        coroutineScope {
            val put = async { TournamentService(pause.afterUpdate(), tx).update(earlier.id, reopen) }
            val (finalize, waited) = pause.contend(postgres) { async { opsService(tx).finalizeTournament(later.id) } }

            val reopened = checkNotNull(put.await())
            finalize.await() shouldBe Result.Err(TournamentOpsError.OutOfOrder(Action.Finalize, listOf(reopened)))
            waited.shouldBeTrue()
        }
    }

    test("an unlinked finalize leaves a match in another season alone, so that season's reset can't strand it") {
        val (unlinked, seeded) = tx.update { seedUnlinkedWithForeignMatch() }
        val pause = WriterPause()
        val pausedFinalize = opsService(tx, tournamentRepository = pause.afterMatchLock())

        coroutineScope {
            val finalize = async { pausedFinalize.finalizeTournament(unlinked.id) }
            val (reset, _) = pause.contend(postgres) { async { opsService(tx).resetTournament(seeded.earlier.id) } }

            finalize.await().orFail()
            reset.await().orFail().status shouldBe TournamentStatus.Upcoming
        }
        tx.get { TournamentRepository().findById(seeded.match.id) }?.status shouldBe TournamentStatus.Upcoming
        tx.get { TournamentRepository().getResults(seeded.match.id) }.shouldBeEmpty()
    }
})

/** Stops one transaction where its hook fires, until [contend] has seen a contender wait on a lock or finish. */
private class WriterPause {
    private val reached = CompletableDeferred<Unit>()
    private val release = CountDownLatch(1)

    fun afterFindAll(): TournamentRepository = OrderCheckPausingTournamentRepository(TournamentRepository(), this)

    fun afterUpdate(): TournamentRepository = UpdatePausingTournamentRepository(TournamentRepository(), this)

    fun afterMatchLock(): TournamentRepository = MatchLockPausingTournamentRepository(TournamentRepository(), this)

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

/** Finalize calls findAll once, for its order check, after its locks and before any write. */
private class OrderCheckPausingTournamentRepository(
    private val delegate: TournamentRepository,
    private val pause: WriterPause,
) : TournamentRepository by delegate {
    context(ctx: TransactionContext)
    override fun findAll(filter: TournamentFilter): List<Tournament> = delegate.findAll(filter).also { pause.hold() }
}

/** The PUT's write, made and not yet committed. */
private class UpdatePausingTournamentRepository(
    private val delegate: TournamentRepository,
    private val pause: WriterPause,
) : TournamentRepository by delegate {
    context(ctx: WriteTransactionContext)
    override fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? = delegate.update(id, request).also { pause.hold() }
}

/** An unlinked finalize, holding its matches and about to import into them. */
private class MatchLockPausingTournamentRepository(
    private val delegate: TournamentRepository,
    private val pause: WriterPause,
) : TournamentRepository by delegate {
    context(ctx: WriteTransactionContext)
    override fun findByPgaTournamentIdsForUpdate(pgaTournamentIds: Collection<String>): List<Tournament> =
        delegate.findByPgaTournamentIdsForUpdate(pgaTournamentIds).also { pause.hold() }
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

/** Every service reads one scoreboard: [EVENT_ESPN_ID], completed, on [EVENT_WEEK]. */
private fun opsService(
    tx: Transactor,
    tournamentRepository: TournamentRepository = TournamentRepository(),
): TournamentOpsService =
    TournamentOpsService(
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
                client = FakeEspnClient(mapOf(EVENT_WEEK to listOf(finalScoreboard()))),
                seasonRepository = SeasonRepository(),
                golferRepository = GolferRepository(),
                teamRepository = TeamRepository(),
                tournamentRepository = TournamentRepository(),
                linkRepository = TournamentLinkRepository(),
                tx = tx,
            ),
        tx = tx,
    )

context(ctx: WriteTransactionContext)
private fun seedLinkedEvent(): Tournament =
    checkNotNull(TournamentRepository().findById(seedTournament(pgaTournamentId = EVENT_ESPN_ID)))

/** The finisher is rostered, so the finalize writes a fantasy score as well as a result. */
context(ctx: WriteTransactionContext)
private fun seedRosteredEvent(): Tournament {
    val event = seedLinkedEvent()
    val finisher =
        GolferRepository().create(
            CreateGolferRequest(pgaPlayerId = FINISHER_ESPN_ID, firstName = "Scottie", lastName = "Scheffler"),
        )
    TeamRepository().addToRoster(seedTeams(event.seasonId).first(), AddToRosterRequest(golferId = finisher.id))
    return event
}

/** The earlier week is completed and the later one, linked to the scoreboard's event, is not. */
context(ctx: WriteTransactionContext)
private fun seedConsecutiveWeeks(): Pair<Tournament, Tournament> {
    val later = seedLinkedEvent()
    val earlier = seedEarlierWeek(later)
    val completed = UpdateTournamentRequest(status = TournamentStatus.Completed)
    return checkNotNull(TournamentRepository().update(earlier.id, completed)) to later
}

context(ctx: WriteTransactionContext)
private fun seedEarlierWeek(later: Tournament): Tournament =
    TournamentRepository().create(
        CreateTournamentRequest(
            name = "RBC Heritage",
            seasonId = later.seasonId,
            startDate = EARLIER_WEEK,
            endDate = EARLIER_WEEK.plusDays(3),
        ),
    )

private data class ForeignSeason(val earlier: Tournament, val match: Tournament)

/** The match's season is not the unlinked tournament's, and its earlier week there is completed. */
context(ctx: WriteTransactionContext)
private fun seedUnlinkedWithForeignMatch(): Pair<Tournament, ForeignSeason> {
    val unlinked = checkNotNull(TournamentRepository().findById(seedTournament()))
    val match = seedLinkedEvent()
    val completed = UpdateTournamentRequest(status = TournamentStatus.Completed)
    val earlier = checkNotNull(TournamentRepository().update(seedEarlierWeek(match).id, completed))
    return unlinked to ForeignSeason(earlier = earlier, match = match)
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
