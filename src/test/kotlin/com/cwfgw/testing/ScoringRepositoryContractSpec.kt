package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.ScoreBreakdown
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.UpsertScore
import com.cwfgw.seasons.SeasonId
import com.cwfgw.tournaments.TournamentId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

private val SCORE_ORDER: Comparator<FantasyScore> =
    compareBy(POSTGRES_UUID_ORDER) { score: FantasyScore -> score.tournamentId.value }.thenByDescending { it.points }

private fun breakdown(
    payout: BigDecimal,
    position: Int = 1,
): ScoreBreakdown =
    ScoreBreakdown(
        position = position,
        numTied = 1,
        basePayout = payout,
        ownershipPct = BigDecimal(100),
        payout = payout,
        multiplier = BigDecimal.ONE,
    )

/** A fresh team and golfer, so the score never shares an upsert key with another. */
private suspend fun ContractSubject<ScoringRepository>.upsertScore(
    seasonId: SeasonId,
    tournamentId: TournamentId,
    payout: Int,
): FantasyScore {
    val score = UpsertScore(seasonId, seeds.team(), tournamentId, seeds.golfer(), breakdown(BigDecimal(payout)))
    return tx.update { repository.upsertScore(score) }
}

class ScoringRepositoryContractSpec : FunSpec({
    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    runContract("Postgres", scoringContract) { ContractSubject(tx, ScoringRepository(), PostgresSeeds(tx)) }
    runContract("fake", scoringContract) { ContractSubject(FakeTransactor(), FakeScoringRepository(), FakeSeeds) }
})

private val scoringContract: List<ContractCase<ScoringRepository>> =
    listOf(
        ContractCase("an upserted score keeps each amount at its column's scale") {
            val (seasonId, tournamentId) = seeds.season() to seeds.tournament()
            val drafted =
                breakdown(BigDecimal(9)).copy(basePayout = BigDecimal(18), ownershipPct = BigDecimal("50.5"))
            val request = UpsertScore(seasonId, seeds.team(), tournamentId, seeds.golfer(), drafted)

            val score = tx.update { repository.upsertScore(request) }

            listOf(score.points, score.basePayout, score.ownershipPct, score.payout).map { it.toPlainString() } shouldBe
                listOf("9.0000", "18.0000", "50.5000", "9.0000")
            score.multiplier.toPlainString() shouldBe "1.00"
            tx.get { repository.getScores(seasonId, tournamentId) } shouldContainExactly listOf(score)
        },
        ContractCase("a second upsert for the same team, tournament and golfer updates that row in place") {
            val (seasonId, tournamentId) = seeds.season() to seeds.tournament()
            val first = UpsertScore(seasonId, seeds.team(), tournamentId, seeds.golfer(), breakdown(BigDecimal(18)))
            val created = tx.update { repository.upsertScore(first) }

            val updated = tx.update { repository.upsertScore(first.copy(breakdown = breakdown(BigDecimal(9), 3))) }

            updated.id shouldBe created.id
            updated.points.toPlainString() shouldBe "9.0000"
            updated.position shouldBe 3
            tx.get { repository.getScores(seasonId, tournamentId) } shouldContainExactly listOf(updated)
        },
        ContractCase("getScores answers one tournament's scores in the season, highest points first") {
            val (seasonId, tournamentId) = seeds.season() to seeds.tournament()
            val scored = listOf(7, 18, 12).map { payout -> upsertScore(seasonId, tournamentId, payout) }
            upsertScore(seasonId, seeds.tournament(), payout = 30)
            upsertScore(seeds.season(), tournamentId, payout = 40)

            tx.get { repository.getScores(seasonId, tournamentId) } shouldContainExactly
                scored.sortedByDescending { it.points }
        },
        ContractCase("getScoresBySeason orders by tournament id as Postgres orders uuid, then highest points first") {
            val seasonId = seeds.season()
            val tournaments = (1..4).map { seeds.tournament() }
            val scored =
                tournaments.flatMap { tournamentId ->
                    listOf(7, 18, 12).map { payout -> upsertScore(seasonId, tournamentId, payout) }
                }
            upsertScore(seeds.season(), tournaments.first(), payout = 40)

            tx.get { repository.getScoresBySeason(seasonId) } shouldContainExactly scored.sortedWith(SCORE_ORDER)
        },
        ContractCase("getScoresBySeason restricts to the named tournaments, and answers none for an empty set") {
            val (seasonId, named) = seeds.season() to seeds.tournament()
            val kept = upsertScore(seasonId, named, payout = 7)
            upsertScore(seasonId, seeds.tournament(), payout = 18)

            tx.get { repository.getScoresBySeason(seasonId, listOf(named)) } shouldContainExactly listOf(kept)
            tx.get { repository.getScoresBySeason(seasonId, emptyList()) }.shouldBeEmpty()
        },
        ContractCase("deleteByTournament clears the tournament in every season, deleteBySeason the whole season") {
            val (seasonId, otherSeason) = seeds.season() to seeds.season()
            val (tournamentId, kept) = seeds.tournament() to seeds.tournament()
            listOf(seasonId, seasonId, otherSeason).forEach { season -> upsertScore(season, tournamentId, payout = 7) }
            upsertScore(seasonId, kept, payout = 18)

            tx.update { repository.deleteByTournament(tournamentId) } shouldBe 3
            tx.get { repository.getScoresBySeason(otherSeason) }.shouldBeEmpty()
            tx.update { repository.deleteBySeason(seasonId) } shouldBe 1
            tx.get { repository.getScoresBySeason(seasonId) }.shouldBeEmpty()
        },
    )
