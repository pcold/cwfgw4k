package com.cwfgw.tournamentLinks

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import org.jooq.exception.DataAccessException

/** On Postgres, because what is under test is that the golfer insert and the link share one transaction. */
class TournamentLinkCreateGolferSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val golfers = GolferRepository()

    fun service(links: TournamentLinkRepository): TournamentLinkService =
        TournamentLinkService(
            repository = links,
            tournamentRepository = TournamentRepository(),
            golferRepository = golfers,
            tx = tx,
        )

    test("a link write that fails leaves no golfer behind") {
        val tournamentId = tx.update { seedTournament() }
        val request = CreateLinkedGolferRequest(espnCompetitorId = "espn-9", name = "Alex Fitzpatrick")

        shouldThrow<DataAccessException> {
            service(FailingUpsertLinkRepository(TournamentLinkRepository())).createGolferAndLink(tournamentId, request)
        }

        tx.read { golfers.findAll(search = null) }.shouldBeEmpty()
    }

    test("a link that commits stores the golfer it names, split as an import would split it") {
        val tournamentId = tx.update { seedTournament() }
        val request = CreateLinkedGolferRequest(espnCompetitorId = "espn-9", name = "Min Woo Lee")

        val link = service(TournamentLinkRepository()).createGolferAndLink(tournamentId, request).orFail()

        val created = tx.read { golfers.findById(link.golferId) }
        (created?.firstName to created?.lastName) shouldBe ("Min Woo" to "Lee")
        created?.pgaPlayerId shouldBe null
        tx.read { TournamentLinkRepository().listByTournament(tournamentId) } shouldBe listOf(link)
    }
})

/** Fails after the golfer insert has run, as a constraint on the override row would. */
private class FailingUpsertLinkRepository(
    delegate: TournamentLinkRepository,
) : TournamentLinkRepository by delegate {
    context(ctx: WriteTransactionContext)
    override fun upsert(
        tournamentId: TournamentId,
        espnCompetitorId: String,
        golferId: GolferId,
    ): TournamentPlayerOverride = throw DataAccessException("override write refused")
}
