package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.scoring.SeasonStanding
import com.cwfgw.scoring.TeamSeasonTotals
import com.cwfgw.scoring.UpsertScore
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Real-DB coverage for [TournamentOpsService.finalizeTournament]'s
 * atomicity invariant (CWF-25). Forces a mid-flow exception inside
 * [ScoringService.refreshStandingsIn] via a wrapping [ScoringRepository]
 * that throws on `upsertStanding`. Verifies that the ESPN
 * `syncTournamentFromEvent` write performed earlier in the same
 * `tx.update` is rolled back — i.e., the tournament status stays
 * `Upcoming` even though `persistImportIn` already flipped it to
 * `Completed` before the throw.
 *
 * Fakes can't honestly model rollback (there's no real transaction);
 * the spec uses [postgresHarness] for the same reason
 * `AdminServiceConfirmRosterSpec` does.
 */
class TournamentOpsServiceFinalizeRollbackSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl)
    val leagueRepo = LeagueRepository()
    val seasonRepo = SeasonRepository()
    val tournamentRepo = TournamentRepository()
    val teamRepo = TeamRepository()
    val golferRepo = GolferRepository()
    val realScoringRepo = ScoringRepository()
    val linkRepo = TournamentLinkRepository()

    test("finalizeTournament rolls back ESPN status write when refreshStandings throws") {
        // Seed: league + season + one team + one tournament with a matching pga_tournament_id.
        // No roster, so calculateScoresIn never calls upsertScore — we want the throw
        // to land in refreshStandingsIn so it's clear it fires AFTER the ESPN write.
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
                    teamRepo.create(
                        seasonId = season.id,
                        request =
                            CreateTeamRequest(
                                ownerName = "Alice",
                                teamName = "Eagles",
                                teamNumber = 1,
                            ),
                    )
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
                repository = ThrowingOnUpsertStandingScoringRepository(realScoringRepo),
                seasonRepository = seasonRepo,
                tournamentRepository = tournamentRepo,
                teamRepository = teamRepo,
                tx = tx,
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
            runBlocking { opsService.finalizeTournament(seeded.id) }
        }

        // The whole tx.update rolled back: tournament status is still Upcoming despite
        // syncTournamentFromEvent having attempted to flip it to Completed before the
        // throw. If the persistence weren't sharing the outer tx, the status would
        // have committed independently and this assertion would fail.
        val afterRollback = runBlocking { tx.read { tournamentRepo.findById(seeded.id) } }
        afterRollback?.status shouldBe TournamentStatus.Upcoming
    }
})

/**
 * Forwards every [ScoringRepository] call to the real repository except
 * [upsertStanding], which throws unconditionally. Lets the rollback spec
 * land the failure precisely where it needs to fire — after
 * `syncTournamentFromEvent` has already attempted a write but before the
 * outer transaction commits.
 */
private class ThrowingOnUpsertStandingScoringRepository(
    private val delegate: ScoringRepository,
) : ScoringRepository {
    context(ctx: TransactionContext)
    override suspend fun getScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): List<FantasyScore> = delegate.getScores(seasonId, tournamentId)

    context(ctx: TransactionContext)
    override suspend fun getScoresBySeason(
        seasonId: SeasonId,
        tournamentIds: Collection<TournamentId>?,
    ): List<FantasyScore> = delegate.getScoresBySeason(seasonId, tournamentIds)

    context(ctx: TransactionContext)
    override suspend fun getStandings(seasonId: SeasonId): List<SeasonStanding> = delegate.getStandings(seasonId)

    context(ctx: TransactionContext)
    override suspend fun upsertScore(record: UpsertScore): FantasyScore = delegate.upsertScore(record)

    context(ctx: TransactionContext)
    override suspend fun golferPointTotal(
        seasonId: SeasonId,
        teamId: TeamId,
        golferId: GolferId,
    ): BigDecimal = delegate.golferPointTotal(seasonId, teamId, golferId)

    context(ctx: TransactionContext)
    override suspend fun teamSeasonTotals(
        seasonId: SeasonId,
        teamId: TeamId,
    ): TeamSeasonTotals = delegate.teamSeasonTotals(seasonId, teamId)

    context(ctx: TransactionContext)
    override suspend fun upsertStanding(
        seasonId: SeasonId,
        teamId: TeamId,
        totalPoints: BigDecimal,
        tournamentsPlayed: Int,
    ): Nothing = error("Forced rollback in test — refreshStandings should not have reached upsertStanding")

    context(ctx: TransactionContext)
    override suspend fun deleteByTournament(tournamentId: TournamentId): Int =
        delegate.deleteByTournament(tournamentId)

    context(ctx: TransactionContext)
    override suspend fun deleteBySeason(seasonId: SeasonId): Int =
        delegate.deleteBySeason(seasonId)

    context(ctx: TransactionContext)
    override suspend fun deleteStandingsBySeason(seasonId: SeasonId): Int =
        delegate.deleteStandingsBySeason(seasonId)
}
