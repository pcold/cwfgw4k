package com.cwfgw.tournamentLinks

import com.cwfgw.db.TransactionContext
import com.cwfgw.golfers.GolferId
import com.cwfgw.tournaments.TournamentId
import java.util.concurrent.ConcurrentHashMap

class FakeTournamentLinkRepository(
    initial: List<TournamentPlayerOverride> = emptyList(),
) : TournamentLinkRepository {
    private val store = ConcurrentHashMap<Pair<TournamentId, String>, TournamentPlayerOverride>()

    init {
        initial.forEach { override -> store[override.tournamentId to override.espnCompetitorId] = override }
    }

    context(ctx: TransactionContext)
    override suspend fun listByTournament(tournamentId: TournamentId): List<TournamentPlayerOverride> =
        store.values
            .filter { it.tournamentId == tournamentId }
            .sortedBy { it.espnCompetitorId }

    context(ctx: TransactionContext)
    override suspend fun upsert(
        tournamentId: TournamentId,
        espnCompetitorId: String,
        golferId: GolferId,
    ): TournamentPlayerOverride {
        val override =
            TournamentPlayerOverride(
                tournamentId = tournamentId,
                espnCompetitorId = espnCompetitorId,
                golferId = golferId,
            )
        store[tournamentId to espnCompetitorId] = override
        return override
    }

    context(ctx: TransactionContext)
    override suspend fun delete(
        tournamentId: TournamentId,
        espnCompetitorId: String,
    ): Boolean = store.remove(tournamentId to espnCompetitorId) != null
}
