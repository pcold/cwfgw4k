package com.cwfgw.espn

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.RosterEntryId
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.orFail
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val START_DATE = LocalDate.parse("2026-04-23")

/** A surname credit is a guess: it yields its row to any other claim, and two ids guessing one row are withheld. */
class EspnSurnameGuessSpec : FunSpec({

    test("a surname guess yields the row a full name backfills, and the guesser gets its own golfer") {
        val joeBloggs = idlessGolfer("Joe", "Bloggs")
        val import = SurnameGuessImport(joeBloggs)

        import.persist(event("evt-a", competitor("1", "Joe Bloggs"), competitor("2", "Jim Bloggs")))

        import.golferWithId("1").id shouldBe joeBloggs.id
        import.resultGolferIds("evt-a") shouldContainExactly listOf(joeBloggs.id, import.golferWithId("2").id)
    }

    listOf("the backfill's event first" to true, "the guess's event first" to false).forEach { (order, joeFirst) ->
        test("a surname guess yields the row another event's full name backfills, $order") {
            val joeBloggs = idlessGolfer("Joe", "Bloggs")
            val import = SurnameGuessImport(joeBloggs)
            val joeEvent = event("evt-joe", competitor("1", "Joe Bloggs"))
            val jimEvent = event("evt-jim", competitor("2", "Jim Bloggs"))

            if (joeFirst) import.persist(joeEvent, jimEvent) else import.persist(jimEvent, joeEvent)

            import.resultGolferIds("evt-joe") shouldContainExactly listOf(joeBloggs.id)
            import.resultGolferIds("evt-jim") shouldContainExactly listOf(import.golferWithId("2").id)
        }
    }

    test("a surname guess yields a row two full names contest, which both of them leave unscored") {
        val joeBloggs = idlessGolfer("Joe", "Bloggs")
        val import = SurnameGuessImport(joeBloggs)

        import.persist(
            event("evt-a", competitor("1", "Joe Bloggs"), competitor("3", "Joe Bloggs"), competitor("2", "Jim Bloggs")),
        )

        import.resultGolferIds("evt-a") shouldContainExactly listOf(import.golferWithId("2").id)
        import.golferWithIdOrNull("1").shouldBeNull()
    }

    test("a surname guess yields the row an override names for another competitor") {
        val joeBloggs = idlessGolfer("Joe", "Bloggs")
        val import = SurnameGuessImport(joeBloggs)
        import.override("evt-a", espnCompetitorId = "1", golferId = joeBloggs.id)

        import.persist(event("evt-a", competitor("1", "Joseph Bloggs"), competitor("2", "Jim Bloggs")))

        import.resultGolferIds("evt-a") shouldContainExactly listOf(joeBloggs.id, import.golferWithId("2").id)
    }

    test("a surname guess yields the row a team partner's surname is credited in another event") {
        val joeBloggs = idlessGolfer("Joe", "Bloggs")
        val import = SurnameGuessImport(joeBloggs)

        import.persist(
            event("evt-team", competitor("9001", "Bloggs", isTeamPartner = true)),
            event("evt-a", competitor("2", "Jim Bloggs")),
        )

        import.resultGolferIds("evt-team") shouldContainExactly listOf(joeBloggs.id)
        import.resultGolferIds("evt-a") shouldContainExactly listOf(import.golferWithId("2").id)
    }

    test("two athletes guessing one id-less row are both withheld, and nothing is written") {
        val joeBloggs = idlessGolfer("Joe", "Bloggs")
        val import = SurnameGuessImport(joeBloggs)

        import.persist(event("evt-a", competitor("1", "Jim Bloggs"), competitor("2", "Jack Bloggs")))

        import.resultGolferIds("evt-a").shouldBeEmpty()
        import.allGolfers() shouldContainExactly listOf(joeBloggs)
    }

    test("two athletes guessing one id-less row from different events are both withheld") {
        val joeBloggs = idlessGolfer("Joe", "Bloggs")
        val import = SurnameGuessImport(joeBloggs)

        import.persist(event("evt-a", competitor("1", "Jim Bloggs")), event("evt-b", competitor("2", "Jack Bloggs")))

        import.resultGolferIds("evt-a").shouldBeEmpty()
        import.resultGolferIds("evt-b").shouldBeEmpty()
        import.allGolfers() shouldContainExactly listOf(joeBloggs)
    }

    test("one athlete guessing one id-less row in two events is credited it in both, unbackfilled") {
        val joeBloggs = idlessGolfer("Joe", "Bloggs")
        val import = SurnameGuessImport(joeBloggs)

        import.persist(event("evt-a", competitor("2", "Jim Bloggs")), event("evt-b", competitor("2", "Jim Bloggs")))

        import.resultGolferIds("evt-a") shouldContainExactly listOf(joeBloggs.id)
        import.resultGolferIds("evt-b") shouldContainExactly listOf(joeBloggs.id)
        import.allGolfers() shouldContainExactly listOf(joeBloggs)
    }

    test("the competitor listing links a surname guess to nothing on the row another competitor's full name names") {
        val joeBloggs = idlessGolfer("Joe", "Bloggs")
        val tournament = tournament("evt-a")
        val event = event("evt-a", competitor("1", "Joe Bloggs"), competitor("2", "Jim Bloggs"))
        val service =
            testEspnService(
                client = FakeEspnClient(tournamentsByDate = mapOf(tournament.startDate to listOf(event))),
                golferRepository = FakeGolferRepository(initial = listOf(joeBloggs)),
                tournamentRepository = FakeTournamentRepository(initial = listOf(tournament)),
            )

        val listing = service.listCompetitorsForLinking(tournament.id).orFail()

        listing.competitors.map { it.espnCompetitorId to it.linkedGolfer?.id } shouldContainExactly
            listOf("1" to joeBloggs.id, "2" to null)
    }

    test("the live preview pays a surname guess nothing on the rostered row another competitor's full name names") {
        val joeBloggs = idlessGolfer("Joe", "Bloggs")
        val tournament = tournament("evt-a")

        val preview =
            buildLivePreviewForEvent(
                event("evt-a", competitor("1", "Joe Bloggs"), competitor("2", "Jim Bloggs", position = 2)),
                tournament,
                rosteredContext(tournament.seasonId, joeBloggs),
            ).orFail()

        preview.teams.single().golferScores.map { it.position } shouldContainExactly listOf(1)
        preview.leaderboard.map { it.name to it.teamNames } shouldContainExactly
            listOf("Joe Bloggs" to listOf(TEAM_NAME), "Jim Bloggs" to emptyList())
    }
})

private const val TEAM_NAME = "Team A"

private class SurnameGuessImport(vararg initialGolfers: Golfer) {
    private val golfers = FakeGolferRepository(initial = initialGolfers.toList())
    private val tournaments = FakeTournamentRepository()
    private val links = FakeTournamentLinkRepository()
    private val service =
        testEspnService(
            client = FakeEspnClient(),
            golferRepository = golfers,
            tournamentRepository = tournaments,
            linkRepository = links,
        )
    private val tournamentsByEvent = mutableMapOf<String, Tournament>()

    fun override(
        eventId: String,
        espnCompetitorId: String,
        golferId: GolferId,
    ) {
        with(noopTransactionContext) { links.upsert(tournamentFor(eventId).id, espnCompetitorId, golferId) }
    }

    fun persist(vararg events: EspnTournament) {
        val imports = events.map { event -> EventImport(tournamentFor(event.espnId), event) }
        with(noopTransactionContext) { service.persistImportsIn(imports) }
    }

    fun resultGolferIds(eventId: String): List<GolferId> =
        with(noopTransactionContext) { tournaments.getResults(tournamentFor(eventId).id) }
            .sortedBy { it.position }
            .map { it.golferId }

    fun golferWithIdOrNull(pgaPlayerId: String): Golfer? =
        with(noopTransactionContext) { golfers.findByPgaPlayerId(pgaPlayerId) }

    fun golferWithId(pgaPlayerId: String): Golfer = checkNotNull(golferWithIdOrNull(pgaPlayerId))

    fun allGolfers(): List<Golfer> = with(noopTransactionContext) { golfers.findAll(search = null) }

    private fun tournamentFor(eventId: String): Tournament =
        tournamentsByEvent.getOrPut(eventId) { tournament(eventId) }
}

private fun rosteredContext(
    seasonId: SeasonId,
    rostered: Golfer,
): LivePreviewContext {
    val team =
        Team(
            id = TeamId(UUID.randomUUID()),
            seasonId = seasonId,
            ownerName = "Owner",
            teamName = TEAM_NAME,
            teamNumber = null,
            createdAt = Instant.EPOCH,
            updatedAt = Instant.EPOCH,
        )
    val roster =
        RosterEntry(
            id = RosterEntryId(UUID.randomUUID()),
            teamId = team.id,
            golferId = rostered.id,
            acquiredVia = "draft",
            draftRound = 1,
            ownershipPct = BigDecimal(100),
            acquiredAt = Instant.EPOCH,
        )
    return LivePreviewContext.from(
        allGolfers = listOf(rostered),
        teams = listOf(team),
        rosters = listOf(roster),
        rules = SeasonRules.defaults(),
        overridesByTournament = emptyMap(),
    )
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
    position: Int = espnId.toInt(),
    isTeamPartner: Boolean = false,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = "-10",
        scoreToPar = -10,
        totalStrokes = 278,
        roundScores = listOf(70, 70, 69, 69),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = isTeamPartner,
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
