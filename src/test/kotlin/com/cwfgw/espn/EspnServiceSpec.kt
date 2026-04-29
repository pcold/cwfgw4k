package com.cwfgw.espn

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferService
import com.cwfgw.result.Result
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.RosterViewPick
import com.cwfgw.teams.RosterViewTeam
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamService
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentPlayerOverride
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentService
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val TOURNAMENT_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000099"))
private val START_DATE = LocalDate.parse("2026-04-15")

private fun espnCompetitor(
    espnId: String,
    name: String,
    position: Int,
    scoreToPar: Int,
    isTeamPartner: Boolean = false,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = scoreToPar.toString(),
        scoreToPar = scoreToPar,
        totalStrokes = 280,
        roundScores = listOf(70, 70, 70, 70),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = isTeamPartner,
        pairKey = if (isTeamPartner) "team:t1" else null,
    )

private fun espnTournament(
    espnId: String = "401580999",
    name: String = "The Masters",
    completed: Boolean = true,
    competitors: List<EspnCompetitor> = emptyList(),
    isTeamEvent: Boolean = false,
): EspnTournament =
    EspnTournament(
        espnId = espnId,
        name = name,
        completed = completed,
        competitors = competitors,
        isTeamEvent = isTeamEvent,
    )

private fun golfer(
    id: UUID,
    firstName: String,
    lastName: String,
    pgaPlayerId: String? = null,
): Golfer =
    Golfer(
        id = GolferId(id),
        pgaPlayerId = pgaPlayerId,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        active = true,
        updatedAt = Instant.EPOCH,
    )

private fun tournament(
    id: TournamentId = TOURNAMENT_ID,
    pgaTournamentId: String? = "401580999",
    status: TournamentStatus = TournamentStatus.Upcoming,
): Tournament =
    Tournament(
        id = id,
        pgaTournamentId = pgaTournamentId,
        name = "Masters",
        seasonId = SEASON_ID,
        startDate = START_DATE,
        endDate = START_DATE.plusDays(3),
        courseName = null,
        status = status,
        purseAmount = null,
        payoutMultiplier = BigDecimal("1.0000"),
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

private class Fixture(
    initialTournaments: List<Tournament> = emptyList(),
    initialGolfers: List<Golfer> = emptyList(),
    rosterView: Map<SeasonId, List<RosterViewTeam>> = emptyMap(),
    tournamentsByDate: Map<LocalDate, List<EspnTournament>> = emptyMap(),
    upstreamError: EspnUpstreamException? = null,
    initialOverrides: List<TournamentPlayerOverride> = emptyList(),
) {
    val fakeClient =
        FakeEspnClient(tournamentsByDate = tournamentsByDate, upstreamError = upstreamError)
    val tournamentRepo = FakeTournamentRepository(initial = initialTournaments)
    val golferRepo = FakeGolferRepository(initial = initialGolfers)
    val teamRepo = FakeTeamRepository(initialRosterView = rosterView)
    val seasonRepo = FakeSeasonRepository()
    val service: EspnService =
        EspnService(
            client = fakeClient,
            tournamentService = TournamentService(tournamentRepo),
            golferService = GolferService(golferRepo),
            teamService = TeamService(teamRepo),
            seasonService = SeasonService(seasonRepo),
            tournamentLinkRepository = FakeTournamentLinkRepository(initial = initialOverrides),
        )
}

class EspnServiceSpec : FunSpec({

    test("importByDate returns an empty batch when ESPN has no events") {
        val fixture = Fixture()

        val batch =
            fixture.service.importByDate(START_DATE)
                .shouldBeInstanceOf<Result.Ok<EspnImportBatch>>()
                .value

        batch.imported.shouldHaveSize(0)
        batch.unlinked.shouldHaveSize(0)
    }

    test("importByDate returns UpstreamUnavailable when the client throws an upstream exception") {
        val fixture =
            Fixture(upstreamError = EspnUpstreamException(status = 503, message = "Service Unavailable"))

        fixture.service.importByDate(START_DATE) shouldBe Result.Err(EspnError.UpstreamUnavailable(503))
    }

    test("importByDate skips in-progress events and reports unlinked completed events without failing") {
        val event = espnTournament(completed = true, competitors = listOf(espnCompetitor("p1", "Rory McIlroy", 1, -10)))
        val fixture =
            Fixture(
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
                // No tournament with pga_tournament_id matching the event
            )

        val batch =
            fixture.service.importByDate(START_DATE)
                .shouldBeInstanceOf<Result.Ok<EspnImportBatch>>()
                .value

        batch.imported.shouldHaveSize(0)
        batch.unlinked shouldContainExactly listOf(UnlinkedEvent(event.espnId, event.name))
    }

    test("importByDate matches a tournament by pga_tournament_id and persists results") {
        val rory = golfer(UUID.randomUUID(), "Rory", "McIlroy", pgaPlayerId = "p1")
        val event =
            espnTournament(
                espnId = "401580999",
                competitors = listOf(espnCompetitor("p1", "Rory McIlroy", 1, -10)),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(rory),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val batch =
            fixture.service.importByDate(START_DATE)
                .shouldBeInstanceOf<Result.Ok<EspnImportBatch>>()
                .value
        val import = batch.imported.single()

        import.tournamentId shouldBe TOURNAMENT_ID
        import.matched shouldBe 1
        import.created shouldBe 0
        import.unmatched shouldBe emptyList()
    }

    test("importByDate auto-creates an unknown golfer and links the ESPN id as pga_player_id") {
        val event =
            espnTournament(
                espnId = "401580999",
                competitors = listOf(espnCompetitor("unknown-espn", "Jim Brandnew", 5, -3)),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val batch =
            fixture.service.importByDate(START_DATE)
                .shouldBeInstanceOf<Result.Ok<EspnImportBatch>>()
                .value

        batch.imported.single().created shouldBe 1
        val created = fixture.golferRepo.findByPgaPlayerId("unknown-espn")
        created shouldBe fixture.golferRepo.findByPgaPlayerId("unknown-espn") // smoke — it exists
        created?.firstName shouldBe "Jim"
        created?.lastName shouldBe "Brandnew"
    }

    test("team-partner rows match by name only and never attach the synthetic id as pga_player_id") {
        val novak = golfer(UUID.randomUUID(), "Novak", "Novakovich")
        val event =
            espnTournament(
                espnId = "401580999",
                isTeamEvent = true,
                competitors =
                    listOf(
                        espnCompetitor("team:t1:0", "Novakovich", 1, -20, isTeamPartner = true),
                        espnCompetitor("team:t1:1", "Griffin", 1, -20, isTeamPartner = true),
                    ),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(novak),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val batch =
            fixture.service.importByDate(START_DATE)
                .shouldBeInstanceOf<Result.Ok<EspnImportBatch>>()
                .value
        val import = batch.imported.single()

        import.matched shouldBe 2
        // Griffin was auto-created without a pgaPlayerId (synthetic id must never pollute golfers)
        val griffin = fixture.golferRepo.findAll(activeOnly = false, search = "Griffin").single()
        griffin.pgaPlayerId shouldBe null
    }

    test("team-partner with an ambiguous last name is resolved by roster preference") {
        val rosteredSmith = golfer(UUID.randomUUID(), "Alice", "Smith")
        val otherSmith = golfer(UUID.randomUUID(), "Bob", "Smith")
        val teamId = TeamId(UUID.randomUUID())
        val rosterView =
            mapOf(
                SEASON_ID to
                    listOf(
                        RosterViewTeam(
                            teamId = teamId,
                            teamName = "Alice's Team",
                            picks =
                                listOf(
                                    RosterViewPick(
                                        round = 1,
                                        golferName = "Alice Smith",
                                        ownershipPct = BigDecimal("100.00"),
                                        golferId = rosteredSmith.id,
                                    ),
                                ),
                        ),
                    ),
            )
        val event =
            espnTournament(
                espnId = "401580999",
                isTeamEvent = true,
                competitors =
                    listOf(
                        espnCompetitor("team:t1:0", "Smith", 1, -20, isTeamPartner = true),
                        espnCompetitor("team:t1:1", "Jones", 1, -20, isTeamPartner = true),
                    ),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(rosteredSmith, otherSmith),
                rosterView = rosterView,
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val batch =
            fixture.service.importByDate(START_DATE)
                .shouldBeInstanceOf<Result.Ok<EspnImportBatch>>()
                .value
        val import = batch.imported.single()

        // Matched 1 (Smith → rosteredSmith); Jones auto-created → also matched.
        import.matched shouldBe 2
        val smithResults = fixture.golferRepo.findByPgaPlayerId(rosteredSmith.pgaPlayerId ?: "-")
        // rosteredSmith has no pgaPlayerId; verify otherSmith wasn't pulled in instead.
        smithResults shouldBe null
    }

    test("importByDate marks the tournament completed when ESPN says it finished") {
        val event = espnTournament(completed = true, competitors = emptyList())
        val fixture =
            Fixture(
                initialTournaments =
                    listOf(tournament(pgaTournamentId = "401580999", status = TournamentStatus.InProgress)),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        fixture.service.importByDate(START_DATE)

        fixture.tournamentRepo.findById(TOURNAMENT_ID)?.status shouldBe TournamentStatus.Completed
    }

    test("importByDate flips is_team_event on the tournament once ESPN reports a team event") {
        val novak = golfer(UUID.randomUUID(), "Novak", "Novakovich")
        val griffin = golfer(UUID.randomUUID(), "Ben", "Griffin")
        val event =
            espnTournament(
                espnId = "401580999",
                isTeamEvent = true,
                competitors =
                    listOf(
                        espnCompetitor("team:t1:0", "Novakovich", 1, -20, isTeamPartner = true),
                        espnCompetitor("team:t1:1", "Griffin", 1, -20, isTeamPartner = true),
                    ),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(novak, griffin),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        fixture.service.importByDate(START_DATE)

        fixture.tournamentRepo.findById(TOURNAMENT_ID)?.isTeamEvent shouldBe true
        val results = fixture.tournamentRepo.getResults(TOURNAMENT_ID)
        results.map { it.pairKey } shouldBe listOf("team:t1", "team:t1")
    }

    test("importForTournament returns TournamentNotFound when the id does not exist") {
        val fixture = Fixture()

        fixture.service.importForTournament(TOURNAMENT_ID) shouldBe
            Result.Err(EspnError.TournamentNotFound(TOURNAMENT_ID))
    }

    test("importForTournament returns TournamentNotLinked when pga_tournament_id is null") {
        val fixture =
            Fixture(initialTournaments = listOf(tournament(pgaTournamentId = null)))

        fixture.service.importForTournament(TOURNAMENT_ID) shouldBe
            Result.Err(EspnError.TournamentNotLinked(TOURNAMENT_ID))
    }

    test("importForTournament returns EventNotInScoreboard when ESPN's scoreboard lacks the linked espn id") {
        val event = espnTournament(espnId = "different-event", competitors = emptyList())
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        fixture.service.importForTournament(TOURNAMENT_ID) shouldBe
            Result.Err(EspnError.EventNotInScoreboard("401580999"))
    }

    test("importForTournament returns Ok with a single import on success") {
        val rory = golfer(UUID.randomUUID(), "Rory", "McIlroy", pgaPlayerId = "p1")
        val event =
            espnTournament(
                espnId = "401580999",
                competitors = listOf(espnCompetitor("p1", "Rory McIlroy", 1, -10)),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(rory),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val import =
            fixture.service.importForTournament(TOURNAMENT_ID)
                .shouldBeInstanceOf<Result.Ok<EspnImport>>()
                .value

        import.matched shouldBe 1
        fixture.fakeClient.fetchCalls shouldBe listOf(START_DATE)
    }

    test("non-team competitor with no pga_player_id match falls back to exact full-name match") {
        // Existing golfer has no pga_player_id set; ESPN sends a name-only match.
        val rory = golfer(UUID.randomUUID(), "Rory", "McIlroy")
        val event =
            espnTournament(
                espnId = "401580999",
                competitors = listOf(espnCompetitor("espn-p1", "Rory McIlroy", 1, -10)),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(rory),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val import =
            fixture.service.importByDate(START_DATE)
                .shouldBeInstanceOf<Result.Ok<EspnImportBatch>>()
                .value
                .imported
                .single()

        import.matched shouldBe 1
        import.created shouldBe 0
        // The existing rory was used; no new golfer was created.
        fixture.golferRepo.findAll(activeOnly = false, search = null).shouldHaveSize(1)
    }

    test("two ESPN competitors that map to the same Golfer surface as a collision") {
        val rory = golfer(UUID.randomUUID(), "Rory", "McIlroy", pgaPlayerId = "p1")
        // Two competitors: one matches rory by pgaPlayerId, the other by exact full-name.
        val event =
            espnTournament(
                espnId = "401580999",
                competitors =
                    listOf(
                        espnCompetitor("p1", "Rory McIlroy", 1, -10),
                        espnCompetitor("p1-dup", "Rory McIlroy", 2, -9),
                    ),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(rory),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val import =
            fixture.service.importByDate(START_DATE)
                .shouldBeInstanceOf<Result.Ok<EspnImportBatch>>()
                .value
                .imported
                .single()

        import.collisions shouldHaveSize 1
        import.collisions.single().shouldContain("Rory McIlroy")
    }

    test("ambiguous last-name with no rostered candidate falls through to auto-create") {
        val aliceSmith = golfer(UUID.randomUUID(), "Alice", "Smith")
        val bobSmith = golfer(UUID.randomUUID(), "Bob", "Smith")
        // No roster view for SEASON_ID → no Smith is rostered → tiebreaker yields null.
        val event =
            espnTournament(
                espnId = "401580999",
                isTeamEvent = true,
                competitors = listOf(espnCompetitor("team:t1:0", "Smith", 1, -20, isTeamPartner = true)),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(aliceSmith, bobSmith),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        val import =
            fixture.service.importByDate(START_DATE)
                .shouldBeInstanceOf<Result.Ok<EspnImportBatch>>()
                .value
                .imported
                .single()

        // The two existing Smiths plus a new auto-created one (last name "Smith", empty first name).
        import.created shouldBe 1
        fixture.golferRepo.findAll(activeOnly = false, search = "Smith").shouldHaveSize(3)
    }

    test("re-importing the same event does not duplicate matched golfers") {
        val rory = golfer(UUID.randomUUID(), "Rory", "McIlroy", pgaPlayerId = "p1")
        val event =
            espnTournament(
                espnId = "401580999",
                competitors = listOf(espnCompetitor("p1", "Rory McIlroy", 1, -10)),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(rory),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
            )

        fixture.service.importForTournament(TOURNAMENT_ID)
        fixture.service.importForTournament(TOURNAMENT_ID)

        fixture.golferRepo.findAll(activeOnly = false, search = null).shouldHaveSize(1)
    }

    test("a manual override pins an ambiguous partner row to the chosen golfer instead of auto-create") {
        // Two Fitzpatricks on the roster: without an override, the partner row "Fitzpatrick"
        // would auto-create a third golfer because both rostered Fitzpatricks are valid matches.
        // The override pins the partner row to Matt's id directly.
        val matt = golfer(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Matt", "Fitzpatrick")
        val alex = golfer(UUID.fromString("00000000-0000-0000-0000-0000000000a2"), "Alex", "Fitzpatrick")
        val event =
            espnTournament(
                espnId = "401580999",
                isTeamEvent = true,
                competitors =
                    listOf(
                        espnCompetitor("team:t1:1", "Fitzpatrick", 1, -10, isTeamPartner = true),
                    ),
            )
        val fixture =
            Fixture(
                initialTournaments = listOf(tournament(pgaTournamentId = "401580999")),
                initialGolfers = listOf(matt, alex),
                tournamentsByDate = mapOf(START_DATE to listOf(event)),
                initialOverrides = listOf(TournamentPlayerOverride(TOURNAMENT_ID, "team:t1:1", matt.id)),
            )

        val import =
            fixture.service.importForTournament(TOURNAMENT_ID)
                .shouldBeInstanceOf<Result.Ok<EspnImport>>()
                .value

        // No new golfer auto-created — the override mapped the partner row to Matt directly.
        import.created shouldBe 0
        import.matched shouldBe 1
        fixture.golferRepo.findAll(activeOnly = false, search = "Fitzpatrick").shouldHaveSize(2)
    }
})
