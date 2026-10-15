package com.cwfgw.tournamentLinks

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.testing.Pause
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTombstone
import com.cwfgw.testing.seedTournament
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentStatus
import com.cwfgw.tournaments.UpdateTournamentRequest
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jooq.exception.DataAccessException

private const val CHECK_VIOLATION = "23514"

/** Each race holds a tombstone or a finalize uncommitted, then starts a link write that must wait for it. */
class TournamentLinkRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val links = TournamentLinkRepository()
    val tournaments = TournamentRepository()
    val golfers = GolferRepository()
    val service = TournamentLinkService(links, tournaments, golfers, tx)

    suspend fun seedDetry(): Golfer =
        tx.update { golfers.create(CreateGolferRequest(firstName = "Thomas", lastName = "Detry")) }

    test("the schema waits out an uncommitted tombstone, then refuses a link written past the service") {
        val tournamentId = tx.update { seedTournament() }
        val golfer = seedDetry()
        val pause = Pause()

        coroutineScope {
            val tombstone = async { tx.update { seedTombstone(golfer.id).also { pause.hold() } } }
            val write = suspend { tx.update { links.upsert(tournamentId, "espn-1", golfer.id) } }
            val (link, waited) = pause.contend(postgres) { async { shouldThrow<DataAccessException> { write() } } }

            tombstone.await()
            link.await().sqlState() shouldBe CHECK_VIOLATION
            waited.shouldBeTrue()
        }
        tx.read { links.listByTournament(tournamentId) }.shouldBeEmpty()
    }

    test("a link waits out an uncommitted tombstone, then refuses it as tombstoned rather than failing the write") {
        val tournamentId = tx.update { seedTournament() }
        val golfer = seedDetry()
        val pause = Pause()

        coroutineScope {
            val tombstone = async { tx.update { seedTombstone(golfer.id).also { pause.hold() } } }
            val request = UpsertTournamentPlayerOverrideRequest(espnCompetitorId = "espn-1", golferId = golfer.id)
            val (link, waited) = pause.contend(postgres) { async { service.upsert(tournamentId, request) } }

            tombstone.await()
            link.await() shouldBe Result.Err(TournamentLinkError.GolferTombstoned(golfer.id))
            waited.shouldBeTrue()
        }
    }

    test("a link waits out an uncommitted finalize, then refuses the completed tournament") {
        val tournamentId = tx.update { seedTournament() }
        val golfer = seedDetry()
        val pause = Pause()
        val complete = UpdateTournamentRequest(status = TournamentStatus.Completed)

        coroutineScope {
            val finalize =
                async {
                    tx.update {
                        tournaments.findByIdForUpdate(tournamentId)
                        tournaments.update(tournamentId, complete).also { pause.hold() }
                    }
                }
            val request = UpsertTournamentPlayerOverrideRequest(espnCompetitorId = "espn-1", golferId = golfer.id)
            val (link, waited) = pause.contend(postgres) { async { service.upsert(tournamentId, request) } }

            finalize.await()
            link.await() shouldBe Result.Err(TournamentLinkError.TournamentFinalized(tournamentId))
            waited.shouldBeTrue()
        }
        tx.read { links.listByTournament(tournamentId) }.shouldBeEmpty()
    }
})
