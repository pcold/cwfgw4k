package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnCalendarEntry
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate

private val RECENT_DATE: LocalDate = LocalDate.parse("2026-04-01")

/** On Postgres because "writes nothing" has to mean nothing committed, which only a real rollback shows. */
class AdminServiceRosterEspnPersistenceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    suspend fun seedSeason(): SeasonId =
        tx.update {
            val league = LeagueRepository().create(CreateLeagueRequest(name = "Castlewood"))
            SeasonRepository().create(CreateSeasonRequest(leagueId = league.id, name = "2026", seasonYear = 2026)).id
        }

    fun service(vararg athletes: EspnCompetitor): AdminService {
        val recent = EspnCalendarEntry(id = "e-recent", label = "Recent", startDate = "2026-04-01T00:00Z")
        val event =
            EspnTournament(
                espnId = "evt-1",
                name = "Test Event",
                completed = true,
                competitors = athletes.toList(),
                isTeamEvent = false,
            )
        val client = FakeEspnClient(calendar = listOf(recent), tournamentsByDate = mapOf(RECENT_DATE to listOf(event)))
        return AdminService(
            tx = tx,
            seasonService = SeasonService(SeasonRepository(), tx),
            tournamentService = TournamentService(TournamentRepository(), tx),
            espnService = testEspnService(client = client, golferRepository = GolferRepository(), tx = tx),
            golferService = GolferService(GolferRepository(), tx),
            golferRepository = GolferRepository(),
            teamRepository = TeamRepository(),
        )
    }

    suspend fun golferCount(): Int = tx.read { GolferRepository().findAll(search = null) }.size

    suspend fun rosterGolferIds(seasonId: SeasonId): List<GolferId> =
        tx.read { TeamRepository().findRostersBySeason(seasonId) }.map { it.golferId }

    test("a preview whose unmatched picks the ESPN pool resolves writes no golfer") {
        seedSeason()
        val tsv = "$ROSTER_HEADER\n1\tBROWN\t1\tScottie Scheffler\t100\n1\tBROWN\t2\tRory McIlroy\t100"
        val before = golferCount()

        val preview =
            service(espnAthlete("espn-1", "Scottie Scheffler"), espnAthlete("espn-2", "Rory McIlroy"))
                .previewRoster(tsv)
                .orFail()

        preview.matchedCount shouldBe 2
        golferCount() shouldBe before
    }

    test("a confirm inserts the athlete the preview offered, keyed by its ESPN id") {
        val seasonId = seedSeason()
        val tsv = "$ROSTER_HEADER\n1\tBROWN\t1\tScottie Scheffler\t100"
        val service = service(espnAthlete("espn-1", "Scottie Scheffler"))
        val offered =
            service.previewRoster(tsv).orFail().teams.single().picks.single().match
                .shouldBeInstanceOf<PickMatch.MatchedEspn>()

        val brown = fullTeam(1, "BROWN", espnPick(1, offered.espnId, offered.golferName))

        val result = service.confirmRoster(ConfirmRosterRequest(seasonId, listOf(brown))).orFail()

        val scottie = tx.read { GolferRepository().findByPgaPlayerId("espn-1") }.shouldNotBeNull()
        (scottie.firstName to scottie.lastName) shouldBe ("Scottie" to "Scheffler")
        result.golfersCreated shouldBe DRAFT_ROUNDS.count()
        rosterGolferIds(seasonId) shouldContain scottie.id
    }

    test("one ESPN athlete on two teams is one golfer row both picks bind") {
        val seasonId = seedSeason()
        val brown = fullTeam(1, "BROWN", espnPick(1, "espn-1", "Scottie Scheffler"))
        val womble = fullTeam(2, "WOMBLE", espnPick(1, "espn-1", "Scottie Scheffler"))

        val result = service().confirmRoster(ConfirmRosterRequest(seasonId, listOf(brown, womble))).orFail()

        val scottie = tx.read { GolferRepository().findByPgaPlayerId("espn-1") }.shouldNotBeNull()
        rosterGolferIds(seasonId).count { it == scottie.id } shouldBe 2
        result.golfersCreated shouldBe 2 * (DRAFT_ROUNDS.count() - 1) + 1
    }

    test("a confirm that fails after inserting an ESPN athlete leaves neither the golfer nor any roster") {
        val seasonId = seedSeason()
        val takenName = CreateTeamRequest(ownerName = "BROWN", teamName = "BROWN", teamNumber = 1)
        tx.update { TeamRepository().create(seasonId, takenName) }
        val before = golferCount()
        val brown = fullTeam(1, "BROWN", espnPick(1, "espn-1", "Scottie Scheffler"))

        val result = service().confirmRoster(ConfirmRosterRequest(seasonId, listOf(brown)))

        result shouldBe Result.Err(AdminError.TeamNameTaken(seasonId))
        golferCount() shouldBe before
        rosterGolferIds(seasonId).shouldBeEmpty()
    }
})

private const val ROSTER_HEADER = "team_number\tteam_name\tround\tplayer_name\townership_pct"

private fun espnPick(
    round: Int,
    espnId: String,
    name: String,
): ConfirmedPick = ConfirmedPick(round = round, ownershipPct = 100, assignment = GolferAssignment.Espn(espnId, name))

private fun espnAthlete(
    espnId: String,
    name: String,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = 1,
        scoreStr = null,
        scoreToPar = null,
        totalStrokes = null,
        roundScores = emptyList(),
        position = 1,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )
