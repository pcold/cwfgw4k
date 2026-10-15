package com.cwfgw.tournaments

import com.cwfgw.admin.AdminService
import com.cwfgw.admin.ConfirmRosterRequest
import com.cwfgw.admin.ConfirmedPick
import com.cwfgw.admin.DRAFT_ROUNDS
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
import com.cwfgw.golfers.GolferService
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.Pause
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.time.LocalDate

private const val FIRST_EVENT_ESPN_ID = "evt-first"
private const val SECOND_EVENT_ESPN_ID = "evt-second"
private val START_DATE: LocalDate = LocalDate.parse("2026-04-23")

/**
 * The import's events list "300" before "100", and a confirm writes "100" first, so writing event by event would
 * deadlock the two. "100" is a backfill on both sides onto the one id-less Fleetwood row, "300" an insert.
 */
class TournamentImportConfirmRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("a two-event import and a roster confirm sharing new athletes both commit rather than deadlock") {
        val seeded = tx.update { seedRace() }
        val pause = Pause()
        val confirm = rosterConfirm(seeded.confirmSeason, ConfirmPicks.FLEETWOOD_AND_ABERG)

        val confirmed =
            coroutineScope {
                val finalize = async { opsService(tx, pause.afterFirstWrite()).finalizeAndReport(seeded.unlinked.id) }
                val (confirming, waited) = pause.contend(postgres) { async { adminService(tx).confirmRoster(confirm) } }
                finalize.await().orFail()
                confirming.await().orFail().also { waited.shouldBeTrue() }
            }

        // "100"'s backfill lost to the import's on its own row, so beside the fillers only "300"'s create counts.
        val fillers = DRAFT_ROUNDS.count() - ConfirmPicks.FLEETWOOD_AND_ABERG.size
        confirmed.golfersCreated shouldBe fillers + 1

        val espnRows = tx.get { GolferRepository().findAll(search = null) }.filter { it.pgaPlayerId != null }
        espnRows.map { it.pgaPlayerId } shouldContainExactlyInAnyOrder listOf("100", "300")
        espnRows.single { it.pgaPlayerId == "100" }.id shouldBe seeded.fleetwood
        tx.get { TournamentRepository().getResults(seeded.second.id) }.map { it.golferId } shouldContainExactly
            listOf(seeded.fleetwood)
    }
})

/** The confirm writes a season of its own, so the two meet only on golfer rows. */
private data class SeededRace(
    val unlinked: Tournament,
    val second: Tournament,
    val confirmSeason: SeasonId,
    val fleetwood: GolferId,
)

context(ctx: WriteTransactionContext)
private fun seedRace(): SeededRace {
    val unlinked = checkNotNull(TournamentRepository().findById(seedTournament()))
    seedMatch(unlinked, FIRST_EVENT_ESPN_ID)
    val second = seedMatch(unlinked, SECOND_EVENT_ESPN_ID)
    val confirmSeason = checkNotNull(TournamentRepository().findById(seedTournament())).seasonId
    val fleetwood = GolferRepository().create(CreateGolferRequest(firstName = "Tommy", lastName = "Fleetwood"))
    return SeededRace(unlinked = unlinked, second = second, confirmSeason = confirmSeason, fleetwood = fleetwood.id)
}

/** In [unlinked]'s season and week: an unlinked finalize imports into no other season. */
context(ctx: WriteTransactionContext)
private fun seedMatch(
    unlinked: Tournament,
    espnId: String,
): Tournament =
    TournamentRepository().create(
        CreateTournamentRequest(
            name = "Event $espnId",
            seasonId = unlinked.seasonId,
            startDate = START_DATE,
            endDate = START_DATE.plusDays(3),
            pgaTournamentId = espnId,
        ),
    )

private object ConfirmPicks {
    val FLEETWOOD_AND_ABERG: List<ConfirmedPick> =
        listOf(
            ConfirmedPick(round = 1, ownershipPct = 100, assignment = GolferAssignment.Espn("100", "Tommy Fleetwood")),
            ConfirmedPick(round = 2, ownershipPct = 100, assignment = GolferAssignment.Espn("300", "Ludvig Aberg")),
        )
}

private fun rosterConfirm(
    seasonId: SeasonId,
    picks: List<ConfirmedPick>,
): ConfirmRosterRequest =
    ConfirmRosterRequest(seasonId = seasonId, teams = listOf(fullTeam(1, "BROWN", *picks.toTypedArray())))

private fun Pause.afterFirstWrite(): GolferRepository = FirstWritePausingGolferRepository(GolferRepository(), this)

private class FirstWritePausingGolferRepository(
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
    golferRepository: GolferRepository,
): TournamentOpsService {
    val scoreboard =
        listOf(event(FIRST_EVENT_ESPN_ID, "300", "Ludvig Aberg"), event(SECOND_EVENT_ESPN_ID, "100", "Tommy Fleetwood"))
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
                client = FakeEspnClient(mapOf(START_DATE to scoreboard)),
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
        seasonService = SeasonService(SeasonRepository(), tx),
        tournamentService = TournamentService(TournamentRepository(), tx),
        espnService = testEspnService(client = FakeEspnClient(), tx = tx),
        golferService = GolferService(GolferRepository(), tx),
        golferRepository = GolferRepository(),
        teamRepository = TeamRepository(),
    )

private fun event(
    espnId: String,
    athleteId: String,
    athleteName: String,
): EspnTournament =
    EspnTournament(
        espnId = espnId,
        name = "Event $espnId",
        completed = true,
        competitors =
            listOf(
                EspnCompetitor(
                    espnId = athleteId,
                    name = athleteName,
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
