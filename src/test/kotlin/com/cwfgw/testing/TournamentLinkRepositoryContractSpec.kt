package com.cwfgw.testing

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentPlayerOverride
import com.cwfgw.tournaments.TournamentId
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.util.UUID

class TournamentLinkRepositoryContractSpec : FunSpec({
    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    runContract("Postgres", linkContract) { ContractSubject(tx, TournamentLinkRepository(), PostgresSeeds(tx)) }
    runContract("fake", linkContract) {
        ContractSubject(FakeTransactor(), FakeTournamentLinkRepository(), FakeSeeds)
    }
})

private val linkContract: List<ContractCase<TournamentLinkRepository>> =
    listOf(
        ContractCase("an upserted link reads back from its tournament, and an unknown tournament lists none") {
            val tournamentId = seeds.tournament()
            val golferId = seeds.golfer()

            val saved = tx.update { repository.upsert(tournamentId, "abc-123", golferId) }

            saved shouldBe TournamentPlayerOverride(tournamentId, "abc-123", golferId)
            tx.get { repository.listByTournament(tournamentId) } shouldContainExactly listOf(saved)
            tx.get { repository.listByTournament(TournamentId(UUID.randomUUID())) }.shouldBeEmpty()
        },
        ContractCase("an upsert on a linked competitor replaces the golfer rather than adding a row") {
            val tournamentId = seeds.tournament()
            val (first, second) = seeds.golfer() to seeds.golfer()
            tx.update { repository.upsert(tournamentId, "abc-123", first) }

            val replaced = tx.update { repository.upsert(tournamentId, "abc-123", second) }

            replaced.golferId shouldBe second
            tx.get { repository.listByTournament(tournamentId) } shouldContainExactly listOf(replaced)
        },
        ContractCase("listByTournaments answers the requested tournaments' links in competitor order") {
            val (withTwo, withOne) = seeds.tournament() to seeds.tournament()
            val (withNone, unrequested) = seeds.tournament() to seeds.tournament()
            val golferId = seeds.golfer()
            tx.update {
                repository.upsert(withTwo, "def-456", golferId)
                repository.upsert(withOne, "xyz-789", golferId)
                repository.upsert(withTwo, "abc-123", golferId)
                repository.upsert(unrequested, "uvw-000", golferId)
            }

            val listed = tx.get { repository.listByTournaments(listOf(withTwo, withOne, withNone)) }

            listed.map { it.tournamentId to it.espnCompetitorId } shouldContainExactly
                listOf(withTwo to "abc-123", withTwo to "def-456", withOne to "xyz-789")
        },
        ContractCase("listByTournaments answers nothing for no ids, without issuing a query") {
            val connectionless = TransactionContext(DSL.using(SQLDialect.POSTGRES))

            with(connectionless) { repository.listByTournaments(emptyList()) }.shouldBeEmpty()
        },
        ContractCase("delete answers true for the link and false once it is gone") {
            val tournamentId = seeds.tournament()
            val golferId = seeds.golfer()
            tx.update { repository.upsert(tournamentId, "abc-123", golferId) }

            tx.update { repository.delete(tournamentId, "abc-123") } shouldBe true
            tx.update { repository.delete(tournamentId, "abc-123") } shouldBe false

            tx.get { repository.listByTournament(tournamentId) }.shouldBeEmpty()
        },
    )
