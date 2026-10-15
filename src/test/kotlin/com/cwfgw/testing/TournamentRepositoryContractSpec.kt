package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.CreateTournamentResultRequest
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentResult
import com.cwfgw.tournaments.TournamentResultId
import com.cwfgw.tournaments.TournamentStatus
import com.cwfgw.tournaments.UNIQUE_PGA_TOURNAMENT_ID
import com.cwfgw.tournaments.UpdateTournamentRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

private fun masters(seasonId: SeasonId): CreateTournamentRequest =
    CreateTournamentRequest(
        pgaTournamentId = "evt-401",
        name = "Masters",
        seasonId = seasonId,
        startDate = LocalDate.parse("2026-04-09"),
        endDate = LocalDate.parse("2026-04-12"),
    )

private val COMPLETED = UpdateTournamentRequest(status = TournamentStatus.Completed)

private val RESULT_ORDER: Comparator<TournamentResult> =
    compareBy<TournamentResult, Int?>(nullsLast()) { it.position }.thenBy(POSTGRES_UUID_ORDER) { it.id.value }

class TournamentRepositoryContractSpec : FunSpec({
    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val cases = tournamentRowContract + tournamentResultContract

    runContract("Postgres", cases) { ContractSubject(tx, TournamentRepository(), PostgresSeeds(tx)) }
    runContract("fake", cases) {
        val ids = signStraddlingUuids()
        val fake =
            FakeTournamentRepository(
                idFactory = { TournamentId(ids()) },
                resultIdFactory = { TournamentResultId(ids()) },
            )
        ContractSubject(FakeTransactor(), fake, FakeSeeds)
    }
})

private val tournamentRowContract: List<ContractCase<TournamentRepository>> =
    listOf(
        ContractCase("a created tournament starts upcoming at a 1.0000 multiplier and reads back by either id") {
            val request = masters(seeds.season())

            val created = tx.update { repository.create(request) }

            created.status shouldBe TournamentStatus.Upcoming
            created.payoutMultiplier.toPlainString() shouldBe "1.0000"
            created.isTeamEvent shouldBe false
            tx.get { repository.findById(created.id) } shouldBe created
            tx.get { repository.findByPgaTournamentId("evt-401") } shouldBe created
            tx.get { repository.findById(TournamentId(UUID.randomUUID())) }.shouldBeNull()
        },
        ContractCase("findByIdForUpdate reads the row findById does, and null for an unknown id") {
            val request = masters(seeds.season())
            val created = tx.update { repository.create(request) }

            tx.update { repository.findByIdForUpdate(created.id) } shouldBe created
            tx.update { repository.findByIdForUpdate(TournamentId(UUID.randomUUID())) }.shouldBeNull()
        },
        ContractCase("a payout multiplier comes back at the column's four decimal places") {
            val doubled = masters(seeds.season()).copy(payoutMultiplier = BigDecimal(2))
            val halfAgain = UpdateTournamentRequest(payoutMultiplier = BigDecimal("1.5"))

            val created = tx.update { repository.create(doubled) }
            val updated = tx.update { repository.update(created.id, halfAgain) }

            created.payoutMultiplier.toPlainString() shouldBe "2.0000"
            updated?.payoutMultiplier?.toPlainString() shouldBe "1.5000"
        },
        ContractCase("a second tournament on a taken pga id is outraced") {
            val seasonId = seeds.season()
            tx.update { repository.create(masters(seasonId)) }

            tx.updateUnlessOutraced(UNIQUE_PGA_TOURNAMENT_ID) { repository.create(masters(seasonId)) } shouldBe
                Result.Err(Outraced)
            tx.get { repository.findAll(seasonId = null, status = null) }.map { it.name } shouldContainExactly
                listOf("Masters")
        },
        ContractCase("findAll filters by season and status, ordered by start date then creation") {
            val seasonId = seeds.season()
            val april = masters(seasonId).copy(pgaTournamentId = null)
            val elsewhere = april.copy(name = "Elsewhere", seasonId = seeds.season())
            tx.update { repository.create(april.copy(name = "Later", startDate = LocalDate.parse("2026-05-01"))) }
            tx.update { repository.create(april.copy(name = "First")) }
            val second = tx.update { repository.create(april.copy(name = "Second")) }
            tx.update { repository.create(elsewhere) }
            tx.update { repository.update(second.id, COMPLETED) }

            tx.get { repository.findAll(seasonId, status = null) }.map { it.name } shouldContainExactly
                listOf("First", "Second", "Later")
            tx.get { repository.findAll(seasonId, TournamentStatus.Completed) }.map { it.name } shouldContainExactly
                listOf("Second")
            tx.get { repository.findAll(seasonId = null, status = null) }.map { it.name } shouldContainExactly
                listOf("First", "Second", "Elsewhere", "Later")
        },
        ContractCase("update changes only the fields it carries, and answers null for an unknown id") {
            val request = masters(seeds.season()).copy(courseName = "Augusta", week = "15")
            val created = tx.update { repository.create(request) }
            val rename = UpdateTournamentRequest(name = "The Masters", isTeamEvent = true)

            val renamed = tx.update { repository.update(created.id, rename) }

            renamed shouldBe created.copy(name = "The Masters", isTeamEvent = true)
            tx.update { repository.update(created.id, UpdateTournamentRequest()) } shouldBe renamed
            tx.update { repository.update(TournamentId(UUID.randomUUID()), rename) }.shouldBeNull()
        },
        ContractCase("resetSeasonTournaments returns the season's tournaments to upcoming and counts them") {
            val seasonId = seeds.season()
            val otherSeason = seeds.season()
            val mine = tx.update { repository.create(masters(seasonId)).also { repository.update(it.id, COMPLETED) } }
            val other = tx.update { repository.create(masters(otherSeason).copy(pgaTournamentId = null)) }
            tx.update { repository.update(other.id, COMPLETED) }

            tx.update { repository.resetSeasonTournaments(seasonId) } shouldBe 1

            tx.get { repository.findById(mine.id) }?.status shouldBe TournamentStatus.Upcoming
            tx.get { repository.findById(other.id) }?.status shouldBe TournamentStatus.Completed
        },
    )

private val tournamentResultContract: List<ContractCase<TournamentRepository>> =
    listOf(
        ContractCase("upserting a golfer's result again replaces its fields and keeps its id") {
            val seasonId = seeds.season()
            val golfer = seeds.golfer()
            val tournament = tx.update { repository.create(masters(seasonId)) }
            val third = CreateTournamentResultRequest(golfer, position = 3)
            val winnerMissedCut = CreateTournamentResultRequest(golfer, position = 1, madeCut = false)

            val first = tx.update { repository.upsertResult(tournament.id, third) }
            val replaced = tx.update { repository.upsertResult(tournament.id, winnerMissedCut) }

            replaced shouldBe first.copy(position = 1, madeCut = false)
            tx.get { repository.getResults(tournament.id) } shouldContainExactly listOf(replaced)
        },
        ContractCase("results order by position with the unplaced last, ties by id as Postgres orders uuid") {
            val seasonId = seeds.season()
            val tournament = tx.update { repository.create(masters(seasonId)) }

            val upserted =
                listOf(null, 2, null, 1, 2, null).map { position ->
                    val entry = CreateTournamentResultRequest(seeds.golfer(), position)
                    tx.update { repository.upsertResult(tournament.id, entry) }
                }

            tx.get { repository.getResults(tournament.id) } shouldContainExactly upserted.sortedWith(RESULT_ORDER)
        },
        ContractCase("getResultsByTournaments groups by tournament id as Postgres orders uuid") {
            val seasonId = seeds.season()
            val winner = CreateTournamentResultRequest(seeds.golfer(), position = 1)
            val events = (1..4).map { tx.update { repository.create(masters(seasonId).copy(pgaTournamentId = null)) } }
            val asked = events.take(3).map { event -> tx.update { repository.upsertResult(event.id, winner) } }
            tx.update { repository.upsertResult(events.last().id, winner) }

            tx.get { repository.getResultsByTournaments(asked.map { it.tournamentId }) } shouldContainExactly
                asked.sortedWith(compareBy(POSTGRES_UUID_ORDER) { it.tournamentId.value })
            tx.get { repository.getResultsByTournaments(emptyList()) }.shouldBeEmpty()
        },
        ContractCase("deleting results by tournament or by season removes and counts only theirs") {
            val seasonId = seeds.season()
            val otherSeason = seeds.season()
            val golfers = listOf(seeds.golfer(), seeds.golfer())
            val first = tx.update { repository.create(masters(seasonId)) }
            val second = tx.update { repository.create(masters(seasonId).copy(pgaTournamentId = null)) }
            val elsewhere = tx.update { repository.create(masters(otherSeason).copy(pgaTournamentId = null)) }
            tx.update {
                listOf(first, second, elsewhere).forEach { event ->
                    golfers.forEach { golfer ->
                        repository.upsertResult(event.id, CreateTournamentResultRequest(golfer))
                    }
                }
            }

            tx.update { repository.deleteResultsByTournament(first.id) } shouldBe 2
            tx.update { repository.deleteResultsBySeason(seasonId) } shouldBe 2

            tx.get { repository.getResultsByTournaments(listOf(first.id, second.id)) }.shouldBeEmpty()
            tx.get { repository.getResults(elsewhere.id) }.map { it.golferId } shouldContainExactlyInAnyOrder golfers
        },
    )
