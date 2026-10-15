package com.cwfgw.espn

import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val START_DATE = LocalDate.parse("2026-04-23")

/** An unlinked finalize imports every sibling event in one call, so their golfer writes are one ordered pass. */
class EspnServiceMultiEventSpec : FunSpec({

    test("every event's backfills and creates are written in one ESPN-id order") {
        val fleetwood = idlessGolfer("Tommy", "Fleetwood")
        val import = MultiEventImport(listOf(fleetwood))

        import.persist(
            event("evt-a", competitor("300", "Ludvig Aberg")),
            event("evt-b", competitor("100", "Tommy Fleetwood"), competitor("200", "Rory McIlroy")),
        )

        import.golfers.writes shouldContainExactly listOf("backfill 100", "create 200", "create 300")
        import.resultGolferIds("evt-b") shouldContainExactlyInAnyOrder
            listOf(fleetwood.id, import.golferWithId("200").id)
    }

    test("one athlete new to two events is one golfer and one write, credited in both") {
        val import = MultiEventImport()

        import.persist(
            event("evt-a", competitor("300", "Ludvig Aberg")),
            event("evt-b", competitor("300", "Ludvig Aberg")),
        )

        import.golfers.writes shouldContainExactly listOf("create 300")
        val aberg = import.golferWithId("300").id
        import.resultGolferIds("evt-a") shouldContainExactly listOf(aberg)
        import.resultGolferIds("evt-b") shouldContainExactly listOf(aberg)
    }

    test("two events naming one id-less row by different ids are both withheld, and the row keeps no id") {
        val fleetwood = idlessGolfer("Tommy", "Fleetwood")
        val import = MultiEventImport(listOf(fleetwood))

        import.persist(
            event("evt-a", competitor("100", "Tommy Fleetwood")),
            event("evt-b", competitor("200", "Tommy Fleetwood")),
        )

        import.golfers.writes.shouldBeEmpty()
        import.resultGolferIds("evt-a").shouldBeEmpty()
        import.resultGolferIds("evt-b").shouldBeEmpty()
        with(noopTransactionContext) { import.golfers.findById(fleetwood.id) }?.pgaPlayerId.shouldBeNull()
    }

    test("one id naming one id-less row in two events is the same athlete, backfilled once") {
        val fleetwood = idlessGolfer("Tommy", "Fleetwood")
        val import = MultiEventImport(listOf(fleetwood))

        import.persist(
            event("evt-a", competitor("100", "Tommy Fleetwood")),
            event("evt-b", competitor("100", "Tommy Fleetwood")),
        )

        import.golfers.writes shouldContainExactly listOf("backfill 100")
        import.resultGolferIds("evt-a") shouldContainExactly listOf(fleetwood.id)
        import.resultGolferIds("evt-b") shouldContainExactly listOf(fleetwood.id)
        with(noopTransactionContext) { import.golfers.findAll(search = null) } shouldHaveSize 1
    }
})

private class MultiEventImport(initialGolfers: List<Golfer> = emptyList()) {
    val golfers = WriteRecordingGolferRepository(FakeGolferRepository(initial = initialGolfers))
    val tournaments = FakeTournamentRepository()
    private val service =
        testEspnService(client = FakeEspnClient(), golferRepository = golfers, tournamentRepository = tournaments)
    private val tournamentsByEvent = mutableMapOf<String, Tournament>()

    fun persist(vararg events: EspnTournament) {
        val imports = events.map { event -> EventImport(tournamentFor(event.espnId), event) }
        with(noopTransactionContext) { service.persistImportsIn(imports) }
    }

    fun resultGolferIds(eventId: String): List<GolferId> =
        with(noopTransactionContext) { tournaments.getResults(tournamentFor(eventId).id) }.map { it.golferId }

    fun golferWithId(pgaPlayerId: String): Golfer =
        checkNotNull(with(noopTransactionContext) { golfers.findByPgaPlayerId(pgaPlayerId) })

    private fun tournamentFor(eventId: String): Tournament =
        tournamentsByEvent.getOrPut(eventId) { tournament(eventId) }
}

private class WriteRecordingGolferRepository(
    private val delegate: GolferRepository,
) : GolferRepository by delegate {
    val writes = mutableListOf<String>()

    context(ctx: WriteTransactionContext)
    override fun findOrCreate(request: CreateGolferRequest): Golfer {
        writes += "create ${request.pgaPlayerId}"
        return delegate.findOrCreate(request)
    }

    context(ctx: WriteTransactionContext)
    override fun backfillPgaPlayerId(
        id: GolferId,
        pgaPlayerId: String,
    ): Golfer? {
        writes += "backfill $pgaPlayerId"
        return delegate.backfillPgaPlayerId(id, pgaPlayerId)
    }
}

private fun idlessGolfer(
    firstName: String,
    lastName: String,
): Golfer =
    Golfer(
        id = GolferId(UUID.randomUUID()),
        pgaPlayerId = null,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        updatedAt = Instant.EPOCH,
    )

private fun competitor(
    espnId: String,
    name: String,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = 1,
        scoreStr = "-10",
        scoreToPar = -10,
        totalStrokes = 278,
        roundScores = emptyList(),
        position = 1,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

private fun event(
    espnId: String,
    vararg competitors: EspnCompetitor,
): EspnTournament =
    EspnTournament(
        espnId = espnId,
        name = "Event $espnId",
        completed = true,
        competitors = competitors.toList(),
        isTeamEvent = false,
    )

private fun tournament(eventId: String): Tournament =
    Tournament(
        id = TournamentId(UUID.randomUUID()),
        pgaTournamentId = eventId,
        name = "Event $eventId",
        seasonId = SeasonId(UUID.randomUUID()),
        startDate = START_DATE,
        endDate = START_DATE.plusDays(3),
        courseName = null,
        status = TournamentStatus.Upcoming,
        purseAmount = null,
        payoutMultiplier = BigDecimal("1.0000"),
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )
