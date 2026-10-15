package com.cwfgw.espn

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.RosterEntryId
import com.cwfgw.teams.RosterViewPick
import com.cwfgw.teams.RosterViewTeam
import com.cwfgw.teams.Team
import com.cwfgw.teams.TeamId
import com.cwfgw.testing.FakeTransactor
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val SEASON_ID = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000aaa"))
private val TOURNAMENT_ID = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000099"))
private val TEAM_ID = TeamId(UUID.fromString("00000000-0000-0000-0000-000000000c01"))
private val START_DATE = LocalDate.parse("2026-04-15")

private fun competitor(
    espnId: String,
    name: String,
    position: Int,
    isTeamPartner: Boolean = false,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = "-5",
        scoreToPar = -5,
        totalStrokes = 275,
        roundScores = listOf(69, 69, 69, 68),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = isTeamPartner,
        pairKey = if (isTeamPartner) "team:t1" else null,
    )

private fun event(vararg competitors: EspnCompetitor): EspnTournament =
    EspnTournament(
        espnId = "401580999",
        name = "The Masters",
        completed = false,
        competitors = competitors.toList(),
        isTeamEvent = competitors.any { it.isTeamPartner },
    )

private fun golfer(
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

private val TOURNAMENT =
    Tournament(
        id = TOURNAMENT_ID,
        pgaTournamentId = "401580999",
        name = "Masters",
        seasonId = SEASON_ID,
        startDate = START_DATE,
        endDate = START_DATE.plusDays(3),
        courseName = null,
        status = TournamentStatus.InProgress,
        purseAmount = null,
        payoutMultiplier = BigDecimal.ONE,
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

private fun rosterViewOf(rostered: List<Golfer>): RosterViewTeam =
    RosterViewTeam(
        teamId = TEAM_ID,
        teamNumber = 1,
        teamName = "Team A",
        picks =
            rostered.mapIndexed { index, golfer ->
                RosterViewPick(
                    round = index + 1,
                    golferName = "${golfer.firstName} ${golfer.lastName}",
                    ownershipPct = BigDecimal(100),
                    golferId = golfer.id,
                )
            },
    )

private class ImportFixture(
    initialGolfers: List<Golfer>,
    rostered: List<Golfer> = emptyList(),
) {
    val golferRepo = FakeGolferRepository(initial = initialGolfers)
    val tournamentRepo = FakeTournamentRepository(initial = listOf(TOURNAMENT))
    val service =
        EspnService(
            client = FakeEspnClient(),
            seasonRepository = FakeSeasonRepository(),
            golferRepository = golferRepo,
            teamRepository = FakeTeamRepository(initialRosterView = mapOf(SEASON_ID to listOf(rosterViewOf(rostered)))),
            tournamentRepository = tournamentRepo,
            linkRepository = FakeTournamentLinkRepository(),
            tx = FakeTransactor(),
        )

    fun import(event: EspnTournament): List<GolferId> =
        with(noopTransactionContext) {
            service.persistImportIn(TOURNAMENT, event)
            tournamentRepo.getResults(TOURNAMENT_ID).map { it.golferId }
        }

    fun allGolfers(): List<Golfer> = with(noopTransactionContext) { golferRepo.findAll(search = null) }
}

private fun rosteredPreview(
    rostered: List<Golfer>,
    unrostered: List<Golfer>,
    event: EspnTournament,
): EspnLivePreview {
    val rosters =
        rostered.mapIndexed { index, golfer ->
            RosterEntry(
                id = RosterEntryId(UUID.randomUUID()),
                teamId = TEAM_ID,
                golferId = golfer.id,
                acquiredVia = "draft",
                draftRound = index + 1,
                ownershipPct = BigDecimal(100),
                acquiredAt = Instant.EPOCH,
            )
        }
    val team =
        Team(
            id = TEAM_ID,
            seasonId = SEASON_ID,
            ownerName = "Owner",
            teamName = "Team A",
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
    return buildLivePreviewForEvent(event, TOURNAMENT, context)
}

class EspnNameMatchingSpec : FunSpec({

    // Each name has a namesake, so the surname fallback cannot rescue a full-name miss.
    test("import attaches ESPN's hyphenated name to the golfer a roster stored without the hyphen") {
        val byeongHunAn = golfer("Byeong Hun", "An")
        val namesake = golfer("Kevin", "An")
        val fixture = ImportFixture(listOf(byeongHunAn, namesake))

        fixture.import(event(competitor("espn-an", "Byeong-Hun An", 1))) shouldBe listOf(byeongHunAn.id)
        fixture.allGolfers() shouldContainExactlyInAnyOrder listOf(byeongHunAn.copy(pgaPlayerId = "espn-an"), namesake)
    }

    test("import attaches ESPN's accented name to the golfer a roster stored unaccented") {
        val olesen = golfer("Thorbjorn", "Olesen")
        val namesake = golfer("Jacob", "Olesen")
        val fixture = ImportFixture(listOf(olesen, namesake))

        fixture.import(event(competitor("espn-olesen", "Thorbjørn Olesen", 1))) shouldBe listOf(olesen.id)
        fixture.allGolfers() shouldContainExactlyInAnyOrder listOf(olesen.copy(pgaPlayerId = "espn-olesen"), namesake)
    }

    test("a single-word partner name nobody matches is stored as a surname") {
        val fixture = ImportFixture(emptyList())

        fixture.import(event(competitor("team:t1:0", "Griffin", 1, isTeamPartner = true)))

        fixture.allGolfers().map { it.firstName to it.lastName } shouldBe listOf("" to "Griffin")
    }

    test("import keeps a non-partner apart from a golfer whose hyphenated surname ends in his") {
        val norgaardPetersen = golfer("Niklas", "Nørgaard-Petersen")
        val fixture = ImportFixture(listOf(norgaardPetersen))

        val credited = fixture.import(event(competitor("espn-petersen", "Mark Petersen", 1)))

        credited.single() shouldNotBe norgaardPetersen.id
        fixture.allGolfers().map { it.firstName to it.lastName } shouldContainExactlyInAnyOrder
            listOf("Niklas" to "Nørgaard-Petersen", "Mark" to "Petersen")
    }

    // A single-word partner is stored as ("", "Griffin"), whose full name is the partner's whole name.
    test("import credits a partner to his rostered namesake, not to a phantom row named only his surname") {
        val ben = golfer("Ben", "Griffin")
        val phantom = golfer("", "Griffin")
        val fixture = ImportFixture(listOf(ben, phantom, golfer("Lucas", "Griffin")), rostered = listOf(ben))

        fixture.import(event(competitor("team:t1:0", "Griffin", 1, isTeamPartner = true))) shouldBe listOf(ben.id)
    }

    test("the live preview credits a partner to his rostered namesake, not to a phantom row named only his surname") {
        val preview =
            rosteredPreview(
                rostered = listOf(golfer("Ben", "Griffin")),
                unrostered = listOf(golfer("", "Griffin"), golfer("Lucas", "Griffin")),
                event = event(competitor("team:t1:0", "Griffin", 1, isTeamPartner = true)),
            )

        preview.leaderboard.map { it.teamNames } shouldContainExactly listOf(listOf("Team A"))
    }

    test("the live preview credits rostered golfers whose ESPN names differ by accent, hyphen or split") {
        val byeongHunAn = golfer("Byeong Hun", "An")
        val olesen = golfer("Thorbjorn", "Olesen")
        val minWooLee = golfer("Min Woo", "Lee")
        val preview =
            rosteredPreview(
                rostered = listOf(byeongHunAn, olesen, minWooLee),
                unrostered = listOf(golfer("Kevin", "An"), golfer("Jacob", "Olesen")),
                event =
                    event(
                        competitor("espn-an", "Byeong-Hun An", 1),
                        competitor("espn-olesen", "Thorbjørn Olesen", 2),
                        competitor("espn-lee", "Min Woo Lee", 3),
                    ),
            )

        preview.leaderboard.map { it.teamNames } shouldContainExactly List(3) { listOf("Team A") }
    }
})
