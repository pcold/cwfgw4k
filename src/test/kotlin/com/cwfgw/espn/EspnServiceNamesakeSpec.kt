package com.cwfgw.espn

import ch.qos.logback.classic.Level
import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.CapturedLogs
import com.cwfgw.testing.captureLogs
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTeams
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import kotlinx.coroutines.runBlocking

private const val EVENT_ID = "evt-401"

/** Run against Postgres because the backfill's guard is the UPDATE's WHERE, which a fake only imitates. */
class EspnServiceNamesakeSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val golfers = GolferRepository()
    val tournaments = TournamentRepository()

    fun service(client: EspnClient = FakeEspnClient()): EspnService =
        testEspnService(
            client = client,
            seasonRepository = SeasonRepository(),
            golferRepository = golfers,
            teamRepository = TeamRepository(),
            tournamentRepository = tournaments,
            linkRepository = TournamentLinkRepository(),
            tx = tx,
        )

    suspend fun seededTournament() = tx.update { checkNotNull(tournaments.findById(seedTournament(EVENT_ID))) }

    test("an import credits the competitor's own new row, not a full-name namesake holding another ESPN id") {
        val tournament = seededTournament()
        val namesake = tx.update { golfers.create(golferRequest("Scottie", "Scheffler", pgaPlayerId = "espn-other")) }

        captureLogs { logs ->
            runBlocking {
                tx.update { service().persistImportIn(tournament, masters(competitor("espn-1", "Scottie Scheffler"))) }

                val credited = tx.read { tournaments.getResults(tournament.id) }.single().golferId
                credited shouldNotBe namesake.id
                tx.get { golfers.findById(credited) }?.pgaPlayerId shouldBe "espn-1"
            }
            val warning = logs.warnings().single { "espn-1" in it }
            warning shouldContain namesake.id.value.toString()
            warning shouldNotContain "Scheffler"
        }
    }

    test("an import credits the competitor's own new row, not a unique-surname namesake holding another ESPN id") {
        val tournament = seededTournament()
        val namesake = tx.update { golfers.create(golferRequest("S.", "Scheffler", pgaPlayerId = "espn-other")) }

        tx.update { service().persistImportIn(tournament, masters(competitor("espn-1", "Scottie Scheffler"))) }

        val credited = tx.read { tournaments.getResults(tournament.id) }.single().golferId
        credited shouldNotBe namesake.id
        tx.get { golfers.findById(credited) }?.pgaPlayerId shouldBe "espn-1"
    }

    test("an import backfills the competitor's ESPN id onto the id-less row its full name matches") {
        val tournament = seededTournament()
        val rory = tx.update { golfers.create(golferRequest("Rory", "McIlroy")) }

        tx.update { service().persistImportIn(tournament, masters(competitor("espn-2", "Rory McIlroy"))) }

        tx.read { tournaments.getResults(tournament.id) }.map { it.golferId } shouldContainExactly listOf(rory.id)
        tx.get { golfers.findAll(search = null) }.map { it.id to it.pgaPlayerId } shouldContainExactly
            listOf(rory.id to "espn-2")
    }

    test("an import credits and backfills the one id-less rostered row among full-name namesakes") {
        val tournament = seededTournament()
        val (drafted, undrafted) =
            tx.update {
                val drafted = golfers.create(golferRequest("John", "Smith"))
                val undrafted = golfers.create(golferRequest("John", "Smith"))
                TeamRepository().addToRoster(seedTeams(tournament.seasonId).first(), AddToRosterRequest(drafted.id))
                drafted to undrafted
            }

        tx.update { service().persistImportIn(tournament, masters(competitor("espn-3", "John Smith"))) }

        tx.read { tournaments.getResults(tournament.id) }.map { it.golferId } shouldContainExactly listOf(drafted.id)
        tx.get { golfers.findAll(search = null) }.map { it.id to it.pgaPlayerId } shouldContainExactlyInAnyOrder
            listOf(drafted.id to "espn-3", undrafted.id to null)
    }

    test("an import leaves a competitor unscored rather than twin two id-less rostered full-name namesakes") {
        val tournament = seededTournament()
        val drafted =
            tx.update {
                val teamId = seedTeams(tournament.seasonId).first()
                List(2) { golfers.create(golferRequest("John", "Smith")) }
                    .onEach { TeamRepository().addToRoster(teamId, AddToRosterRequest(it.id)) }
            }

        captureLogs { logs ->
            runBlocking {
                tx.update { service().persistImportIn(tournament, masters(competitor("espn-4", "John Smith"))) }

                tx.read { tournaments.getResults(tournament.id) }.shouldBeEmpty()
                tx.get { golfers.findAll(search = null) }.map { it.id to it.pgaPlayerId } shouldContainExactlyInAnyOrder
                    drafted.map { it.id to null }
            }
            val warning = logs.warnings().single { "espn-4" in it }
            drafted.forEach { warning shouldContain it.id.value.toString() }
        }
    }

    test("the competitor listing names the row the import credits, and writes nothing") {
        val tournament = seededTournament()
        val (drafted, undrafted) =
            tx.update {
                val drafted = golfers.create(golferRequest("John", "Smith"))
                val undrafted = golfers.create(golferRequest("John", "Smith"))
                TeamRepository().addToRoster(seedTeams(tournament.seasonId).first(), AddToRosterRequest(drafted.id))
                drafted to undrafted
            }
        val event = masters(competitor("espn-5", "John Smith"))
        val client = FakeEspnClient(tournamentsByDate = mapOf(tournament.startDate to listOf(event)))

        val listing = service(client).listCompetitorsForLinking(tournament.id).orFail()

        listing.competitors.single().linkedGolfer?.id shouldBe drafted.id
        tx.get { golfers.findAll(search = null) } shouldContainExactlyInAnyOrder listOf(drafted, undrafted)
        tx.update { service().persistImportIn(tournament, event) }
        tx.read { tournaments.getResults(tournament.id) }.map { it.golferId } shouldContainExactly listOf(drafted.id)
    }
})

private fun CapturedLogs.warnings(): List<String> =
    events().filter { it.level == Level.WARN }.map { it.formattedMessage }

private fun golferRequest(
    firstName: String,
    lastName: String,
    pgaPlayerId: String? = null,
): CreateGolferRequest = CreateGolferRequest(pgaPlayerId = pgaPlayerId, firstName = firstName, lastName = lastName)

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

private fun masters(vararg competitors: EspnCompetitor): EspnTournament =
    EspnTournament(
        espnId = EVENT_ID,
        name = "Masters",
        completed = true,
        competitors = competitors.toList(),
        isTeamEvent = false,
    )
