package com.cwfgw.tournaments

import com.cwfgw.admin.AdminService
import com.cwfgw.admin.ConfirmRosterRequest
import com.cwfgw.admin.ConfirmedPick
import com.cwfgw.admin.GolferAssignment
import com.cwfgw.admin.fullTeam
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.Pause
import com.cwfgw.testing.PostgresHarness
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

private const val EVENT_ESPN_ID = "evt-lock-order"
private val START_DATE: LocalDate = LocalDate.parse("2026-04-23")

/**
 * The finalize is paused after its first id claim, holding that row, while a confirm takes a lock the finalize needs
 * next; the confirm then waits on the finalize. Unordered, the finalize's next lock closes a cycle.
 */
class TournamentImportConfirmLockOrderSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("a confirm rostering a row the import backfills, and backfilling one the import scores, does not deadlock") {
        val seeded = tx.update { seedRace(listOf("Alpha Aardvark", "Bravo Bee")) }
        val (alpha, bravo) = seeded.golfers
        val scoreboard = listOf(competitor("e1", "Alpha Aardvark"), competitor("e2", "Bravo Bee"))
        val picks = listOf(GolferAssignment.Existing(alpha), GolferAssignment.Espn("e2", "Bravo Bee"))

        race(postgres, seeded, scoreboard, picks)

        val golfers = tx.get { GolferRepository().findAll(search = null) }.associateBy(Golfer::id)
        golfers.getValue(alpha).pgaPlayerId shouldBe "e1"
        golfers.getValue(bravo).pgaPlayerId shouldBe "e2"
        tx.get {
            TournamentRepository().getResults(seeded.tournament)
        }.map { it.golferId } shouldContainExactlyInAnyOrder
            listOf(alpha, bravo)
        rosteredIn(tx, seeded.confirmSeason) shouldContainAll listOf(alpha, bravo)
    }

    test("an import and a confirm backfilling one id-less row under different ids do not deadlock") {
        val seeded = tx.update { seedRace(listOf("Robin Row")) }
        val robin = seeded.golfers.single()
        val scoreboard = listOf(competitor("e2", "Robin Row"), competitor("e4", "Delta Dog"))
        val picks = listOf(GolferAssignment.Espn("e4", "Delta Dog"), GolferAssignment.Espn("e5", "Robin Row"))

        race(postgres, seeded, scoreboard, picks)

        val golfers = tx.get { GolferRepository().findAll(search = null) }
        golfers.single { it.id == robin }.pgaPlayerId shouldBe "e2"
        val confirmedRobin = golfers.single { it.pgaPlayerId == "e5" }
        confirmedRobin.id shouldNotBe robin
        val deltaDog = golfers.single { it.pgaPlayerId == "e4" }
        rosteredIn(tx, seeded.confirmSeason) shouldContainAll listOf(deltaDog.id, confirmedRobin.id)
    }
})

/** The confirm writes a season of its own, so the two meet only on golfer rows. */
private data class LockOrderRace(
    val tournament: TournamentId,
    val confirmSeason: SeasonId,
    val golfers: List<GolferId>,
)

context(ctx: WriteTransactionContext)
private fun seedRace(idlessNames: List<String>): LockOrderRace {
    val tournament = seedTournament(pgaTournamentId = EVENT_ESPN_ID)
    val confirmSeason = checkNotNull(TournamentRepository().findById(seedTournament())).seasonId
    val golfers =
        idlessNames.map { name ->
            val (first, last) = name.split(" ")
            GolferRepository().create(CreateGolferRequest(firstName = first, lastName = last)).id
        }
    return LockOrderRace(tournament = tournament, confirmSeason = confirmSeason, golfers = golfers)
}

/** Awaited before the lock wait is asserted, so a deadlock surfaces as the 40P01 it is. */
private suspend fun race(
    postgres: PostgresHarness,
    seeded: LockOrderRace,
    scoreboard: List<EspnCompetitor>,
    picks: List<GolferAssignment>,
) {
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val pause = Pause()
    val confirm = rosterConfirm(seeded.confirmSeason, picks)
    coroutineScope {
        val finalize =
            async { opsService(tx, scoreboard, pause.afterFirstWrite()).finalizeAndReport(seeded.tournament) }
        val (confirming, waited) = pause.contend(postgres) { async { adminService(tx).confirmRoster(confirm) } }
        finalize.await().orFail()
        confirming.await().orFail()
        waited.shouldBeTrue()
    }
}

private suspend fun rosteredIn(
    tx: Transactor,
    seasonId: SeasonId,
): List<GolferId> = tx.get { TeamRepository().findRostersBySeason(seasonId) }.map { it.golferId }

private fun rosterConfirm(
    seasonId: SeasonId,
    assignments: List<GolferAssignment>,
): ConfirmRosterRequest {
    val picks =
        assignments.mapIndexed { index, assignment ->
            ConfirmedPick(round = index + 1, ownershipPct = 100, assignment = assignment)
        }
    return ConfirmRosterRequest(seasonId = seasonId, teams = listOf(fullTeam(1, "BROWN", *picks.toTypedArray())))
}

private fun Pause.afterFirstWrite(): GolferRepository = ClaimPausingGolferRepository(GolferRepository(), this)

private class ClaimPausingGolferRepository(
    private val delegate: GolferRepository,
    private val pause: Pause,
) : GolferRepository by delegate {
    context(ctx: WriteTransactionContext)
    override fun findOrCreate(request: CreateGolferRequest): Golfer =
        delegate.findOrCreate(request).also { pause.hold() }

    context(ctx: WriteTransactionContext)
    override fun backfillPgaPlayerId(
        id: GolferId,
        pgaPlayerId: String,
    ): Golfer? = delegate.backfillPgaPlayerId(id, pgaPlayerId).also { pause.hold() }
}

private fun opsService(
    tx: Transactor,
    competitors: List<EspnCompetitor>,
    golferRepository: GolferRepository,
): TournamentOpsService {
    val event =
        EspnTournament(
            espnId = EVENT_ESPN_ID,
            name = "Event $EVENT_ESPN_ID",
            completed = true,
            competitors = competitors,
            isTeamEvent = false,
        )
    return TournamentOpsService(
        tournamentRepository = TournamentRepository(),
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
                client = FakeEspnClient(mapOf(START_DATE to listOf(event))),
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

private fun adminService(tx: Transactor): AdminService =
    AdminService(
        tx = tx,
        seasonRepository = SeasonRepository(),
        tournamentRepository = TournamentRepository(),
        espnService = testEspnService(client = FakeEspnClient(), tx = tx),
        golferRepository = GolferRepository(),
        teamRepository = TeamRepository(),
    )

private fun competitor(
    espnId: String,
    name: String,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = 1,
        scoreStr = "-12",
        scoreToPar = -12,
        totalStrokes = 276,
        roundScores = listOf(69, 69, 69, 69),
        position = 1,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )
