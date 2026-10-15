package com.cwfgw.scoring

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.POSTGRES_UUID_ORDER
import com.cwfgw.testing.fakeTableOf
import com.cwfgw.tournaments.TournamentId
import java.time.Instant
import java.util.UUID

class FakeScoringRepository(
    initialScores: List<FantasyScore> = emptyList(),
    private val scoreIdFactory: () -> FantasyScoreId = { FantasyScoreId(UUID.randomUUID()) },
) : ScoringRepository {
    private val scores = fakeTableOf<FantasyScoreId, FantasyScore>()
    val scoreUpserts = mutableListOf<FantasyScore>()

    init {
        initialScores.forEach { scores[it.id] = it }
    }

    context(ctx: TransactionContext)
    override fun getScores(
        seasonId: SeasonId,
        tournamentId: TournamentId,
    ): List<FantasyScore> =
        scores.values
            .filter { it.seasonId == seasonId && it.tournamentId == tournamentId }
            .sortedByDescending { it.points }

    context(ctx: TransactionContext)
    override fun getScoresBySeason(
        seasonId: SeasonId,
        tournamentIds: Collection<TournamentId>?,
    ): List<FantasyScore> {
        if (tournamentIds != null && tournamentIds.isEmpty()) return emptyList()
        val tournamentFilter = tournamentIds?.toSet()
        return scores.values
            .filter {
                it.seasonId == seasonId &&
                    (tournamentFilter == null || it.tournamentId in tournamentFilter)
            }
            .sortedWith(
                compareBy(POSTGRES_UUID_ORDER) { score: FantasyScore -> score.tournamentId.value }
                    .thenByDescending { it.points },
            )
    }

    context(ctx: WriteTransactionContext)
    override fun upsertScore(record: UpsertScore): FantasyScore {
        val existing =
            scores.values.firstOrNull { existing ->
                existing.seasonId == record.seasonId &&
                    existing.teamId == record.teamId &&
                    existing.tournamentId == record.tournamentId &&
                    existing.golferId == record.golferId
            }
        val score =
            FantasyScore(
                id = existing?.id ?: scoreIdFactory(),
                seasonId = record.seasonId,
                teamId = record.teamId,
                tournamentId = record.tournamentId,
                golferId = record.golferId,
                points = record.breakdown.payout,
                position = record.breakdown.position,
                numTied = record.breakdown.numTied,
                basePayout = record.breakdown.basePayout,
                ownershipPct = record.breakdown.ownershipPct,
                payout = record.breakdown.payout,
                multiplier = record.breakdown.multiplier,
                calculatedAt = Instant.now(),
            )
        scores[score.id] = score
        scoreUpserts += score
        return score
    }

    context(ctx: WriteTransactionContext)
    override fun deleteByTournament(tournamentId: TournamentId): Int {
        val matching = scores.values.filter { it.tournamentId == tournamentId }
        matching.forEach { scores.remove(it.id) }
        return matching.size
    }

    context(ctx: WriteTransactionContext)
    override fun deleteBySeason(seasonId: SeasonId): Int {
        val matching = scores.values.filter { it.seasonId == seasonId }
        matching.forEach { scores.remove(it.id) }
        return matching.size
    }
}
