package com.cwfgw.tournaments

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
import com.cwfgw.testing.Pause
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
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

private const val EVENT_ESPN_ID = "401580999"
private const val FINISHER_ESPN_ID = "9478"
private val EVENT_WEEK: LocalDate = LocalDate.parse("2026-04-23")
private val EARLIER_WEEK: LocalDate = LocalDate.parse("2026-04-16")
private val LATER_WEEK: LocalDate = LocalDate.parse("2026-04-30")

/** Writers beside finalize and reset that change what their sibling-order checks read, paused mid-transaction. */
class TournamentSeasonLockRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("a season clean-results waits for a finalize in its season, then clears what that finalize wrote") {
        val event = tx.update { seedRosteredEvent() }
        val pause = Pause()
        val pausedFinalize = opsService(tx, tournamentRepository = pause.afterFindAll())
        val seasonOps = SeasonOpsService(SeasonRepository(), TournamentRepository(), ScoringRepository(), tx)

        coroutineScope {
            val finalize = async { pausedFinalize.finalizeAndReport(event.id) }
            val (clean, waited) = pause.contend(postgres) { async { seasonOps.cleanSeasonResults(event.seasonId) } }

            finalize.await().orFail().tournament.status shouldBe TournamentStatus.Completed
            clean.await()?.resultsDeleted shouldBe 1
            waited.shouldBeTrue()
        }
        tx.get { TournamentRepository().findById(event.id) }?.status shouldBe TournamentStatus.Upcoming
        tx.get { TournamentRepository().getResults(event.id) }.shouldBeEmpty()
        tx.get { ScoringRepository().getScores(event.seasonId, event.id) }.shouldBeEmpty()
    }

    test("a finalize waits for a PUT reopening the week before it, then refuses as out of order") {
        val (earlier, later) = tx.update { seedConsecutiveWeeks() }
        val pause = Pause()
        val reopen = UpdateTournamentRequest(status = TournamentStatus.Upcoming)

        coroutineScope {
            val put = async { TournamentService(pause.afterUpdate(), tx).update(earlier.id, reopen) }
            val (finalize, waited) = pause.contend(postgres) { async { opsService(tx).finalizeAndReport(later.id) } }

            val reopened = checkNotNull(put.await())
            finalize.await() shouldBe Result.Err(TournamentOpsError.OutOfOrder(Action.Finalize, listOf(reopened)))
            waited.shouldBeTrue()
        }
    }

    test("a finalize waits for a PUT moving an upcoming week before it, then refuses as out of order") {
        val (moving, event) = tx.update { seedWeekAfter() }
        val pause = Pause()
        val moveEarlier = UpdateTournamentRequest(startDate = EARLIER_WEEK, endDate = EARLIER_WEEK.plusDays(3))

        coroutineScope {
            val put = async { TournamentService(pause.afterUpdate(), tx).update(moving.id, moveEarlier) }
            val (finalize, waited) = pause.contend(postgres) { async { opsService(tx).finalizeAndReport(event.id) } }

            val moved = checkNotNull(put.await())
            finalize.await() shouldBe Result.Err(TournamentOpsError.OutOfOrder(Action.Finalize, listOf(moved)))
            waited.shouldBeTrue()
        }
    }

    test("an unlinked finalize leaves a match in another season alone, so that season's reset can't strand it") {
        val (unlinked, seeded) = tx.update { seedUnlinkedWithForeignMatch() }
        val pause = Pause()
        val pausedFinalize = opsService(tx, scoringTournamentRepository = pause.afterImport())

        coroutineScope {
            val finalize = async { pausedFinalize.finalizeAndReport(unlinked.id) }
            val (reset, _) = pause.contend(postgres) { async { opsService(tx).resetTournament(seeded.earlier.id) } }

            finalize.await().orFail()
            reset.await().orFail().status shouldBe TournamentStatus.Upcoming
        }
        tx.get { TournamentRepository().findById(seeded.match.id) }?.status shouldBe TournamentStatus.Upcoming
        tx.get { TournamentRepository().getResults(seeded.match.id) }.shouldBeEmpty()
    }
})

private fun Pause.afterFindAll(): TournamentRepository =
    OrderCheckPausingTournamentRepository(TournamentRepository(), this)

private fun Pause.afterUpdate(): TournamentRepository = UpdatePausingTournamentRepository(TournamentRepository(), this)

private fun Pause.afterImport(): TournamentRepository = ResultsPausingTournamentRepository(TournamentRepository(), this)

/** Finalize calls findAll once, for its order check, after its locks and before any write. */
private class OrderCheckPausingTournamentRepository(
    private val delegate: TournamentRepository,
    private val pause: Pause,
) : TournamentRepository by delegate {
    context(ctx: TransactionContext)
    override fun findAll(filter: TournamentFilter): List<Tournament> = delegate.findAll(filter).also { pause.hold() }
}

/** The PUT's write, made and not yet committed. */
private class UpdatePausingTournamentRepository(
    private val delegate: TournamentRepository,
    private val pause: Pause,
) : TournamentRepository by delegate {
    context(ctx: WriteTransactionContext)
    override fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? = delegate.update(id, request).also { pause.hold() }
}

/** Scoring reads the results after every import is written, so a contender commits after the match filter ran. */
private class ResultsPausingTournamentRepository(
    private val delegate: TournamentRepository,
    private val pause: Pause,
) : TournamentRepository by delegate {
    context(ctx: TransactionContext)
    override fun getResults(tournamentId: TournamentId): List<TournamentResult> =
        delegate.getResults(tournamentId).also { pause.hold() }
}

/** Every service reads one scoreboard: [EVENT_ESPN_ID], completed, on [EVENT_WEEK]. */
private fun opsService(
    tx: Transactor,
    tournamentRepository: TournamentRepository = TournamentRepository(),
    scoringTournamentRepository: TournamentRepository = TournamentRepository(),
): TournamentOpsService =
    TournamentOpsService(
        tournamentRepository = tournamentRepository,
        scoringService =
            ScoringService(
                repository = ScoringRepository(),
                seasonRepository = SeasonRepository(),
                tournamentRepository = scoringTournamentRepository,
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

/** The scoreboard's event, and an upcoming week after it in its season. */
context(ctx: WriteTransactionContext)
private fun seedWeekAfter(): Pair<Tournament, Tournament> {
    val event = seedLinkedEvent()
    val after =
        TournamentRepository().create(
            CreateTournamentRequest(
                name = "Zurich Classic",
                seasonId = event.seasonId,
                startDate = LATER_WEEK,
                endDate = LATER_WEEK.plusDays(3),
            ),
        )
    return after to event
}

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
