package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
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
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.scoring.UpsertScore
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.Season
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Real-DB coverage for [TournamentOpsService.finalizeAndReport]'s
 * atomicity invariant (CWF-25). Forces a mid-flow exception inside
 * [ScoringService.calculateScoresIn] via a wrapping [ScoringRepository]
 * that throws on `upsertScore`. Verifies that the ESPN
 * `syncTournamentFromEvent` write performed earlier in the same
 * `tx.update` is rolled back — i.e., the tournament status stays
 * `Upcoming` even though `persistImportIn` already flipped it to
 * `Completed` before the throw.
 *
 * The Err cases hide a row from [ScoringService] alone, so `calculateScoresIn` returns an Err rather than throwing.
 *
 * Uses [postgresHarness] for the same reason `AdminServiceConfirmRosterSpec` does.
 */
class TournamentOpsServiceFinalizeRollbackSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val leagueRepo = LeagueRepository()
    val seasonRepo = SeasonRepository()
    val tournamentRepo = TournamentRepository()
    val teamRepo = TeamRepository()
    val golferRepo = GolferRepository()
    val realScoringRepo = ScoringRepository()
    val linkRepo = TournamentLinkRepository()

    test("finalizeAndReport rolls back ESPN status write when calculateScoresIn throws") {
        // The rostered golfer's seeded win is what makes calculateScoresIn reach upsertScore: the ESPN
        // event below carries no competitors, so scoring finds no result unless one is seeded here.
        val seeded =
            runBlocking {
                tx.update {
                    val league = leagueRepo.create(CreateLeagueRequest(name = "Castlewood"))
                    val season =
                        seasonRepo.create(
                            CreateSeasonRequest(
                                leagueId = league.id,
                                name = "2026 Spring",
                                seasonYear = 2026,
                            ),
                        )
                    val team =
                        teamRepo.create(
                            seasonId = season.id,
                            request =
                                CreateTeamRequest(
                                    ownerName = "Alice",
                                    teamName = "Eagles",
                                    teamNumber = 1,
                                ),
                        )
                    val golfer = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
                    teamRepo.addToRoster(team.id, AddToRosterRequest(golferId = golfer.id))
                    val tournament =
                        tournamentRepo.create(
                            CreateTournamentRequest(
                                name = "The Masters",
                                seasonId = season.id,
                                startDate = LocalDate.parse("2026-04-09"),
                                endDate = LocalDate.parse("2026-04-12"),
                                pgaTournamentId = "401580999",
                                payoutMultiplier = BigDecimal.ONE,
                            ),
                        )
                    tournamentRepo.upsertResult(
                        tournament.id,
                        CreateTournamentResultRequest(golferId = golfer.id, position = 1),
                    )
                    tournament
                }
            }

        // ESPN returns an event that matches the tournament's pga_tournament_id and is
        // flagged completed=true so syncTournamentFromEvent attempts a status flip.
        // Zero competitors means the matcher and per-result upserts are no-ops; only
        // the tournament-level status write hits the DB before scoring runs.
        val espnEvent =
            EspnTournament(
                espnId = "401580999",
                name = "The Masters",
                completed = true,
                competitors = emptyList(),
                isTeamEvent = false,
            )
        val espnClient: EspnClient =
            FakeEspnClient(tournamentsByDate = mapOf(seeded.startDate to listOf(espnEvent)))

        val espnService =
            EspnService(
                client = espnClient,
                seasonRepository = seasonRepo,
                golferRepository = golferRepo,
                teamRepository = teamRepo,
                tournamentRepository = tournamentRepo,
                linkRepository = linkRepo,
                tx = tx,
            )
        val scoringService =
            ScoringService(
                repository = ThrowingOnUpsertScoreScoringRepository(realScoringRepo),
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepo,
            )
        val opsService =
            TournamentOpsService(
                tournamentRepository = tournamentRepo,
                scoringService = scoringService,
                scoringRepository = realScoringRepo,
                espnService = espnService,
                tx = tx,
            )

        shouldThrow<Throwable> {
            runBlocking { opsService.finalizeAndReport(seeded.id) }
        }

        // The whole tx.update rolled back: tournament status is still Upcoming despite
        // syncTournamentFromEvent having attempted to flip it to Completed before the
        // throw. If the persistence weren't sharing the outer tx, the status would
        // have committed independently and this assertion would fail.
        val afterRollback = runBlocking { tx.read { tournamentRepo.findById(seeded.id) } }
        afterRollback?.status shouldBe TournamentStatus.Upcoming
    }

    test("finalizeAndReport rolls back the ESPN import when scoring can't find the season") {
        val seeded = runBlocking { tx.update { seedUpcomingMasters() } }
        val opsService =
            opsServiceScoringThrough(
                tx,
                seeded,
                scoringSeasons = SeasonHidingSeasonRepository(SeasonRepository()),
            )

        val outcome = runBlocking { opsService.finalizeAndReport(seeded.id) }

        tx.shouldHoldNothingImportedFor(seeded)
        outcome shouldBe Result.Err(TournamentOpsError.SeasonNotFound(seeded.seasonId))
    }

    test("finalizeAndReport rolls back the ESPN import when scoring can't find the tournament") {
        val seeded = runBlocking { tx.update { seedUpcomingMasters() } }
        val opsService =
            opsServiceScoringThrough(
                tx,
                seeded,
                scoringTournaments = TournamentHidingTournamentRepository(TournamentRepository()),
            )

        val outcome = runBlocking { opsService.finalizeAndReport(seeded.id) }

        tx.shouldHoldNothingImportedFor(seeded)
        outcome shouldBe Result.Err(TournamentOpsError.TournamentNotFound(seeded.id))
    }
})

private const val MASTERS_ESPN_ID = "401580999"
private const val UNSEEN_WINNER_ESPN_ID = "3470"

/** Only scoring reads through [scoringSeasons] and [scoringTournaments]; finalize and the import see every row. */
private fun opsServiceScoringThrough(
    tx: Transactor,
    seeded: Tournament,
    scoringSeasons: SeasonRepository = SeasonRepository(),
    scoringTournaments: TournamentRepository = TournamentRepository(),
): TournamentOpsService {
    val espnEvent =
        EspnTournament(
            espnId = MASTERS_ESPN_ID,
            name = "The Masters",
            completed = true,
            competitors = listOf(unseenWinner()),
            isTeamEvent = false,
        )
    val espnService =
        EspnService(
            client = FakeEspnClient(tournamentsByDate = mapOf(seeded.startDate to listOf(espnEvent))),
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
            seasonRepository = scoringSeasons,
            tournamentRepository = scoringTournaments,
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

private suspend fun Transactor.shouldHoldNothingImportedFor(tournament: Tournament) {
    read { TournamentRepository().findById(tournament.id) }?.status shouldBe TournamentStatus.Upcoming
    read { TournamentRepository().getResults(tournament.id) }.shouldBeEmpty()
    read { GolferRepository().findByPgaPlayerId(UNSEEN_WINNER_ESPN_ID) } shouldBe null
}

context(ctx: WriteTransactionContext)
private fun seedUpcomingMasters(): Tournament {
    val league = LeagueRepository().create(CreateLeagueRequest(name = "Castlewood"))
    val season =
        SeasonRepository().create(
            CreateSeasonRequest(leagueId = league.id, name = "2026 Spring", seasonYear = 2026),
        )
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

/** No golfer carries this ESPN id, so the import creates one and writes its result: two rows a rollback must undo. */
private fun unseenWinner(): EspnCompetitor =
    EspnCompetitor(
        espnId = UNSEEN_WINNER_ESPN_ID,
        name = "Rory McIlroy",
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

/** `calculateScoresIn` returns Err on a null [findById]; finalize has already written by then. */
private class SeasonHidingSeasonRepository(
    delegate: SeasonRepository,
) : SeasonRepository by delegate {
    context(ctx: TransactionContext)
    override fun findById(id: SeasonId): Season? = null
}

/** @see SeasonHidingSeasonRepository */
private class TournamentHidingTournamentRepository(
    delegate: TournamentRepository,
) : TournamentRepository by delegate {
    context(ctx: TransactionContext)
    override fun findById(id: TournamentId): Tournament? = null
}

/**
 * Forwards every [ScoringRepository] call to the real repository except
 * [upsertScore], which throws unconditionally. Lets the rollback spec
 * land the failure precisely where it needs to fire — after
 * `syncTournamentFromEvent` has already attempted a write but before the
 * outer transaction commits.
 */
private class ThrowingOnUpsertScoreScoringRepository(
    private val delegate: ScoringRepository,
) : ScoringRepository {
    context(ctx: TransactionContext)
    override fun getScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): List<FantasyScore> = delegate.getScores(seasonId, tournamentId)

    context(ctx: TransactionContext)
    override fun getScoresBySeason(
        seasonId: SeasonId,
        tournamentIds: Collection<TournamentId>?,
    ): List<FantasyScore> = delegate.getScoresBySeason(seasonId, tournamentIds)

    context(ctx: WriteTransactionContext)
    override fun upsertScore(record: UpsertScore): Nothing =
        error("Forced rollback in test: upsertScore always throws")

    context(ctx: WriteTransactionContext)
    override fun deleteByTournament(tournamentId: TournamentId): Int =
        delegate.deleteByTournament(tournamentId)

    context(ctx: WriteTransactionContext)
    override fun deleteBySeason(seasonId: SeasonId): Int =
        delegate.deleteBySeason(seasonId)
}
