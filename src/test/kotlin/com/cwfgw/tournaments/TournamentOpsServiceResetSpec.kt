package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.scoring.ScoreBreakdown
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.scoring.UpsertScore
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTeams
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import java.math.BigDecimal

/**
 * The status write is reset's last step, after both deletes, so failing it there shows the deletes share its
 * `tx.update`. Real Postgres because a fake has no transaction to roll back.
 */
class TournamentOpsServiceResetSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val tournamentRepo = TournamentRepository()
    val scoringRepo = ScoringRepository()

    test("resetTournament keeps scores and results when its status write throws after the deletes") {
        val tournamentId = tx.update { seedTournament() }
        val seasonId =
            tx.update {
                val seasonId = checkNotNull(tournamentRepo.findById(tournamentId)).seasonId
                val golfer =
                    GolferRepository().create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
                tournamentRepo.upsertResult(
                    tournamentId,
                    CreateTournamentResultRequest(golferId = golfer.id, position = 1),
                )
                seedTeams(seasonId).forEach { teamId ->
                    scoringRepo.upsertScore(
                        UpsertScore(
                            seasonId = seasonId,
                            teamId = teamId,
                            tournamentId = tournamentId,
                            golferId = golfer.id,
                            breakdown = SOLE_WINNER,
                        ),
                    )
                }
                seasonId
            }
        val opsService =
            TournamentOpsService(
                tournamentRepository = ThrowingOnUpdateTournamentRepository(tournamentRepo),
                scoringService =
                    ScoringService(
                        repository = scoringRepo,
                        seasonRepository = SeasonRepository(),
                        tournamentRepository = tournamentRepo,
                        teamRepository = TeamRepository(),
                    ),
                scoringRepository = scoringRepo,
                espnService = testEspnService(client = FakeEspnClient()),
                tx = tx,
            )

        shouldThrow<IllegalStateException> { opsService.resetTournament(tournamentId) }

        tx.read { tournamentRepo.getResults(tournamentId) } shouldHaveSize 1
        tx.read { scoringRepo.getScores(seasonId, tournamentId) } shouldHaveSize 3
    }
})

private val SOLE_WINNER =
    ScoreBreakdown(
        position = 1,
        numTied = 1,
        basePayout = BigDecimal(18),
        ownershipPct = BigDecimal(100),
        payout = BigDecimal(18),
        multiplier = BigDecimal.ONE,
    )

/** @see TournamentOpsServiceResetSpec */
private class ThrowingOnUpdateTournamentRepository(
    delegate: TournamentRepository,
) : TournamentRepository by delegate {
    context(ctx: TransactionContext)
    override fun update(
        id: TournamentId,
        request: UpdateTournamentRequest,
    ): Tournament? = error("Forced rollback in test: update always throws")
}
