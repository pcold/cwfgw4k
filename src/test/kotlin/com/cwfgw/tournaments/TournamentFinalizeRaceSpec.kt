package com.cwfgw.tournaments

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.result.Result
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import java.math.BigDecimal
import java.time.LocalDate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

private const val MASTERS_ESPN_ID = "401580999"
private const val ROSTERED_WINNER_ESPN_ID = "9478"
private const val UNSEEN_RUNNER_UP_ESPN_ID = "3470"

/** Below Transactor's 5s lock_timeout: a holder never released ends before its waiter would time out. */
private const val HOLD_CEILING_MS: Long = 3_000
private const val WAITER_PROBE_MS: Long = 300

/** Finalize reads, fetches from ESPN with no transaction open, then writes: each case lands work in that gap. */
class TournamentFinalizeRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("a season deleted during the ESPN fetch answers TournamentNotFound and imports nothing") {
        val masters = tx.update { seedRosteredMasters() }
        val ops = opsServiceFetchingThrough(tx, masters) { tx.update { SeasonRepository().delete(masters.seasonId) } }

        ops.finalizeTournament(masters.id) shouldBe Result.Err(TournamentOpsError.TournamentNotFound(masters.id))

        tx.get { GolferRepository().findByPgaPlayerId(UNSEEN_RUNNER_UP_ESPN_ID) }.shouldBeNull()
    }

    test("two finalizes past the fetch together both complete, the second re-importing over the first") {
        val masters = tx.update { seedRosteredMasters() }
        val fetched = AtomicInteger()
        val bothFetched = CompletableDeferred<Unit>()
        val ops =
            opsServiceFetchingThrough(tx, masters) {
                if (fetched.incrementAndGet() == 2) bothFetched.complete(Unit)
                bothFetched.await()
            }

        val outcomes = coroutineScope { List(2) { async { ops.finalizeTournament(masters.id) } }.awaitAll() }

        outcomes.map { it.orFail().status } shouldBe listOf(TournamentStatus.Completed, TournamentStatus.Completed)
        tx.get { TournamentRepository().getResults(masters.id) } shouldHaveSize 2
        tx.get { GolferRepository().findByPgaPlayerId(UNSEEN_RUNNER_UP_ESPN_ID) }?.lastName shouldBe "McIlroy"
        tx.get { ScoringRepository().getScores(masters.seasonId, masters.id) } shouldHaveSize 1
    }

    test("findByIdForUpdate keeps a second locker waiting until the first transaction ends") {
        val masters = tx.update { seedRosteredMasters() }
        val locked = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)

        coroutineScope {
            val holder = async { tx.update { lockAndHold(masters.id, locked, release) } }
            locked.await()
            val waiter = async { tx.update { TournamentRepository().findByIdForUpdate(masters.id) } }
            try {
                withTimeoutOrNull(WAITER_PROBE_MS) { waiter.await() }.shouldBeNull()
            } finally {
                release.countDown()
            }
            waiter.await() shouldBe holder.await()
        }
    }
})

context(ctx: WriteTransactionContext)
private fun lockAndHold(
    id: TournamentId,
    locked: CompletableDeferred<Unit>,
    release: CountDownLatch,
): Tournament? {
    val tournament = TournamentRepository().findByIdForUpdate(id)
    locked.complete(Unit)
    release.await(HOLD_CEILING_MS, TimeUnit.MILLISECONDS)
    return tournament
}

/** [duringFetch] runs where finalize has read the tournament and holds no transaction. */
private fun opsServiceFetchingThrough(
    tx: Transactor,
    masters: Tournament,
    duringFetch: suspend () -> Unit,
): TournamentOpsService {
    val espnService =
        EspnService(
            client = PausingEspnClient(FakeEspnClient(mapOf(masters.startDate to listOf(mastersFinal()))), duringFetch),
            seasonRepository = SeasonRepository(),
            golferRepository = GolferRepository(),
            teamRepository = TeamRepository(),
            tournamentRepository = TournamentRepository(),
            linkRepository = TournamentLinkRepository(),
            tx = tx,
        )
    val scoringService =
        ScoringService(
            repository = ScoringRepository(),
            seasonRepository = SeasonRepository(),
            tournamentRepository = TournamentRepository(),
            teamRepository = TeamRepository(),
        )
    return TournamentOpsService(
        tournamentRepository = TournamentRepository(),
        scoringService = scoringService,
        scoringRepository = ScoringRepository(),
        espnService = espnService,
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
private fun seedRosteredMasters(): Tournament {
    val league = LeagueRepository().create(CreateLeagueRequest(name = "Castlewood"))
    val season =
        SeasonRepository().create(CreateSeasonRequest(leagueId = league.id, name = "2026 Spring", seasonYear = 2026))
    val team =
        TeamRepository().create(
            seasonId = season.id,
            request = CreateTeamRequest(ownerName = "Alice", teamName = "Eagles", teamNumber = 1),
        )
    val winner =
        GolferRepository().create(
            CreateGolferRequest(pgaPlayerId = ROSTERED_WINNER_ESPN_ID, firstName = "Scottie", lastName = "Scheffler"),
        )
    TeamRepository().addToRoster(team.id, AddToRosterRequest(golferId = winner.id))
    return TournamentRepository().create(
        CreateTournamentRequest(
            name = "The Masters",
            seasonId = season.id,
            startDate = LocalDate.parse("2026-04-09"),
            endDate = LocalDate.parse("2026-04-12"),
            pgaTournamentId = MASTERS_ESPN_ID,
            payoutMultiplier = BigDecimal.ONE,
        ),
    )
}

private fun mastersFinal(): EspnTournament =
    EspnTournament(
        espnId = MASTERS_ESPN_ID,
        name = "The Masters",
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
