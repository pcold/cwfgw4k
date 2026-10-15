package com.cwfgw.espn

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/** Run against Postgres because Joe's backfill and Jim's create must both land on the rows the results name. */
class EspnServiceSurnameClaimSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val golfers = GolferRepository()
    val tournaments = TournamentRepository()
    val service =
        testEspnService(client = FakeEspnClient(), golferRepository = golfers, tournamentRepository = tournaments)

    suspend fun seededTournament(eventId: String): Tournament =
        tx.update { checkNotNull(tournaments.findById(seedTournament(eventId))) }

    suspend fun idlessJoeBloggs(): Golfer =
        tx.update { golfers.create(CreateGolferRequest(firstName = "Joe", lastName = "Bloggs")) }

    suspend fun creditedEspnIds(tournament: Tournament): Map<GolferId, String?> =
        tx.read { tournaments.getResults(tournament.id) }
            .map { result -> checkNotNull(tx.get { golfers.findById(result.golferId) }) }
            .associate { it.id to it.pgaPlayerId }

    test("one event's surname guess never takes the id-less row another competitor's full name backfills") {
        val tournament = seededTournament("evt-1")
        val joeBloggs = idlessJoeBloggs()

        tx.update { service.persistImportIn(tournament, bloggsEvent("evt-1", joe(), jim())) }

        val jimRow = checkNotNull(tx.get { golfers.findByPgaPlayerId(JIM_ESPN_ID) })
        tx.read { tournaments.getResults(tournament.id) }.associate { it.position to it.golferId } shouldBe
            mapOf(JOE_POSITION to joeBloggs.id, JIM_POSITION to jimRow.id)
        tx.get { golfers.findById(joeBloggs.id) }?.pgaPlayerId shouldBe JOE_ESPN_ID
    }

    listOf("Joe's event first" to true, "Jim's event first" to false).forEach { (order, joeFirst) ->
        test("across two events, a surname guess never takes the row another event's full name backfills, $order") {
            val joeTournament = seededTournament("evt-joe")
            val jimTournament = seededTournament("evt-jim")
            val joeBloggs = idlessJoeBloggs()
            val joeImport = EventImport(joeTournament, bloggsEvent("evt-joe", joe()))
            val jimImport = EventImport(jimTournament, bloggsEvent("evt-jim", jim()))

            val imports = if (joeFirst) listOf(joeImport, jimImport) else listOf(jimImport, joeImport)

            tx.update { service.persistImportsIn(imports) }

            creditedEspnIds(joeTournament) shouldBe mapOf(joeBloggs.id to JOE_ESPN_ID)
            creditedEspnIds(jimTournament).values.toList() shouldBe listOf(JIM_ESPN_ID)
        }
    }
})

private const val JOE_ESPN_ID = "1"
private const val JIM_ESPN_ID = "2"
private const val JOE_POSITION = 1
private const val JIM_POSITION = 2

private fun joe(): EspnCompetitor = competitor(JOE_ESPN_ID, "Joe Bloggs", JOE_POSITION)

private fun jim(): EspnCompetitor = competitor(JIM_ESPN_ID, "Jim Bloggs", JIM_POSITION)

private fun competitor(
    espnId: String,
    name: String,
    position: Int,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = "-10",
        scoreToPar = -10,
        totalStrokes = 278,
        roundScores = emptyList(),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

private fun bloggsEvent(
    eventId: String,
    vararg competitors: EspnCompetitor,
): EspnTournament =
    EspnTournament(
        espnId = eventId,
        name = "Event $eventId",
        completed = true,
        competitors = competitors.toList(),
        isTeamEvent = false,
    )
