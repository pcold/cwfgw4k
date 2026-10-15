package com.cwfgw.espn

import ch.qos.logback.classic.Level
import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.RosterEntryId
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.CapturedLogs
import com.cwfgw.testing.captureLogs
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTeams
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.Tournament
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
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

private const val EVENT_ID = "evt-401"

/** Run against Postgres because the backfill's guard is the UPDATE's WHERE, which a fake only imitates. */
class EspnServiceNamesakeSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val golfers = GolferRepository()
    val tournaments = TournamentRepository()

    fun service(
        client: EspnClient = FakeEspnClient(),
        golferRepository: GolferRepository = golfers,
    ): EspnService =
        testEspnService(
            client = client,
            seasonRepository = SeasonRepository(),
            golferRepository = golferRepository,
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

    suspend fun rosteredGolfers(
        tournament: Tournament,
        vararg requests: CreateGolferRequest,
    ): List<Golfer> =
        tx.update {
            val teamId = seedTeams(tournament.seasonId).first()
            requests.map {
                golfers.create(it)
            }.onEach { TeamRepository().addToRoster(teamId, AddToRosterRequest(it.id)) }
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

    test("an import scores neither of two competitors claiming one id-less row, nor twins it") {
        val tournament = seededTournament()
        val rory = tx.update { golfers.create(golferRequest("Rory", "McIlroy")) }

        captureLogs { logs ->
            runBlocking {
                tx.update {
                    service().persistImportIn(
                        tournament,
                        masters(competitor("espn-8", "Rory McIlroy"), competitor("espn-7", "Rory McIlroy")),
                    )
                }

                tx.read { tournaments.getResults(tournament.id) }.shouldBeEmpty()
                tx.get { golfers.findAll(search = null) } shouldContainExactly listOf(rory)
            }
            logs.warnings().count { rory.id.value.toString() in it } shouldBe 2
        }
    }

    test("an import credits a unique-surname match on an id-less row without backfilling it") {
        val tournament = seededTournament()
        val nicolai = tx.update { golfers.create(golferRequest("Nicolai", "Hojgaard")) }

        tx.update { service().persistImportIn(tournament, masters(competitor("espn-100", "Rasmus Hojgaard"))) }

        tx.read { tournaments.getResults(tournament.id) }.map { it.golferId } shouldContainExactly listOf(nicolai.id)
        tx.get { golfers.findAll(search = null) } shouldContainExactly listOf(nicolai)
    }

    test("an import never backfills the rostered row a partner is credited to") {
        val tournament = seededTournament()
        val (ben) = rosteredGolfers(tournament, golferRequest("Ben", "Griffin"))

        tx.update { service().persistImportIn(tournament, masters(partner("team:t1:0", "Griffin"))) }

        tx.read { tournaments.getResults(tournament.id) }.map { it.golferId } shouldContainExactly listOf(ben.id)
        tx.get { golfers.findAll(search = null) } shouldContainExactly listOf(ben)
    }

    test("an import never credits a full-named competitor to an id-less rostered row sharing only a surname") {
        val tournament = seededTournament()
        val (minWooLee) = rosteredGolfers(tournament, golferRequest("Min Woo", "Lee"))
        val khLee = tx.update { golfers.create(golferRequest("K.H.", "Lee")) }

        tx.update { service().persistImportIn(tournament, masters(competitor("espn-9", "Junghwan Lee"))) }

        val credited = tx.read { tournaments.getResults(tournament.id) }.single().golferId
        tx.get { golfers.findAll(search = null) }.map { it.id to it.pgaPlayerId } shouldContainExactlyInAnyOrder
            listOf(minWooLee.id to null, khLee.id to null, credited to "espn-9")
    }

    test("a backfill a concurrent writer beat to the row credits the row that writer gave the id") {
        val tournament = seededTournament()
        val winner = tx.update { golfers.create(golferRequest("Rory", "McIlroy", pgaPlayerId = "espn-2")) }

        tx.update {
            service(golferRepository = OnceStaleGolferRepository(golfers))
                .persistImportIn(tournament, masters(competitor("espn-2", "Rory McIlroy")))
        }

        tx.read { tournaments.getResults(tournament.id) }.map { it.golferId } shouldContainExactly listOf(winner.id)
        tx.get { golfers.findAll(search = null) } shouldContainExactly listOf(winner)
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

    test("the competitor listing links neither a namesake holding another ESPN id nor a withheld competitor") {
        val tournament = seededTournament()
        tx.update { golfers.create(golferRequest("Scottie", "Scheffler", pgaPlayerId = "espn-other")) }
        rosteredGolfers(tournament, golferRequest("John", "Smith"), golferRequest("John", "Smith"))
        tx.update { golfers.create(golferRequest("Rory", "McIlroy")) }
        val event = masters(*withheldAndMismatchedField())
        val client = FakeEspnClient(tournamentsByDate = mapOf(tournament.startDate to listOf(event)))

        val listing = service(client).listCompetitorsForLinking(tournament.id).orFail()

        listing.competitors.map { it.espnCompetitorId to it.linkedGolfer } shouldContainExactly
            withheldAndMismatchedField().map { it.espnId to null }
    }

    test("the live preview credits the one id-less rostered row among full-name namesakes") {
        val tournament = seededTournament()
        val drafted = rosteredGolfers(tournament, golferRequest("John", "Smith"))
        val undrafted = tx.update { golfers.create(golferRequest("John", "Smith")) }

        val preview = livePreview(tournament, drafted, listOf(undrafted), masters(competitor("espn-3", "John Smith")))

        preview.leaderboard.map { it.teamNames } shouldContainExactly listOf(listOf(TEAM_NAME))
    }

    test("the live preview pays no namesake holding another ESPN id and no withheld competitor") {
        val tournament = seededTournament()
        val rostered =
            rosteredGolfers(
                tournament,
                golferRequest("Scottie", "Scheffler", pgaPlayerId = "espn-other"),
                golferRequest("John", "Smith"),
                golferRequest("John", "Smith"),
                golferRequest("Rory", "McIlroy"),
            )

        val preview = livePreview(tournament, rostered, emptyList(), masters(*withheldAndMismatchedField()))

        preview.leaderboard.map { it.teamNames } shouldContainExactly
            withheldAndMismatchedField().map { emptyList<String>() }
    }
})

/** A namesake holding another id, an athlete among two rostered namesakes, and two athletes on one id-less row. */
private fun withheldAndMismatchedField(): Array<EspnCompetitor> =
    arrayOf(
        competitor("espn-1", "Scottie Scheffler"),
        competitor("espn-4", "John Smith"),
        competitor("espn-7", "Rory McIlroy"),
        competitor("espn-8", "Rory McIlroy"),
    )

private const val TEAM_NAME = "Team A"

private fun livePreview(
    tournament: Tournament,
    rostered: List<Golfer>,
    unrostered: List<Golfer>,
    event: EspnTournament,
): EspnLivePreview {
    val teamId = TeamId(UUID.randomUUID())
    val rosters =
        rostered.mapIndexed { index, golfer ->
            RosterEntry(
                id = RosterEntryId(UUID.randomUUID()),
                teamId = teamId,
                golferId = golfer.id,
                acquiredVia = "draft",
                draftRound = index + 1,
                ownershipPct = BigDecimal(100),
                acquiredAt = Instant.EPOCH,
            )
        }
    val team =
        Team(
            id = teamId,
            seasonId = tournament.seasonId,
            ownerName = "Owner",
            teamName = TEAM_NAME,
            teamNumber = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    val context =
        LivePreviewContext.from(
            allGolfers = rostered + unrostered,
            teams = listOf(team),
            rosters = rosters,
            rules = SeasonRules.defaults(),
            overridesByTournament = emptyMap(),
        )
    return buildLivePreviewForEvent(event, tournament, context)
}

/**
 * The first [findAll] and the first [findByPgaPlayerId] each answer as before a concurrent writer set the
 * `pga_player_id`, which is the window that writer commits into; the backfill itself then meets the committed row.
 */
private class OnceStaleGolferRepository(
    delegate: GolferRepository,
) : GolferRepository by delegate {
    private val inner = delegate
    private val staleById = AtomicBoolean(true)
    private val staleAll = AtomicBoolean(true)

    context(ctx: TransactionContext)
    override fun findByPgaPlayerId(pgaPlayerId: String): Golfer? =
        if (staleById.getAndSet(false)) null else inner.findByPgaPlayerId(pgaPlayerId)

    context(ctx: TransactionContext)
    override fun findAll(search: String?): List<Golfer> {
        val rows = inner.findAll(search)
        return if (staleAll.getAndSet(false)) rows.map { it.copy(pgaPlayerId = null) } else rows
    }
}

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

private fun partner(
    espnId: String,
    name: String,
): EspnCompetitor = competitor(espnId, name).copy(isTeamPartner = true, pairKey = "team:t1")

private fun masters(vararg competitors: EspnCompetitor): EspnTournament =
    EspnTournament(
        espnId = EVENT_ID,
        name = "Masters",
        completed = true,
        competitors = competitors.toList(),
        isTeamEvent = false,
    )
