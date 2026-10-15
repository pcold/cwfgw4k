package com.cwfgw.tournamentLinks

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.GolferId
import com.cwfgw.testing.fakeTableOf
import com.cwfgw.tournaments.TournamentId

class FakeTournamentLinkRepository(
    initial: List<TournamentPlayerOverride> = emptyList(),
) : TournamentLinkRepository {
    private val store = fakeTableOf<Pair<TournamentId, String>, TournamentPlayerOverride>()

    init {
        initial.forEach { override -> store[override.tournamentId to override.espnCompetitorId] = override }
    }

    context(ctx: TransactionContext)
    override fun listByTournament(tournamentId: TournamentId): List<TournamentPlayerOverride> =
        listByTournaments(listOf(tournamentId))

    context(ctx: TransactionContext)
    override fun listByTournaments(ids: Collection<TournamentId>): List<TournamentPlayerOverride> {
        val requested = ids.toSet()
        return store.values
            .filter { it.tournamentId in requested }
            .sortedBy { it.espnCompetitorId }
    }

    context(ctx: WriteTransactionContext)
    override fun upsert(
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

    context(ctx: WriteTransactionContext)
    override fun delete(
        tournamentId: TournamentId,
        espnCompetitorId: String,
    ): Boolean = store.remove(tournamentId to espnCompetitorId) != null
}
