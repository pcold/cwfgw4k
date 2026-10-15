package com.cwfgw.admin

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnCalendarEntry
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueId
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

private val RECENT_DATE: LocalDate = LocalDate.parse("2026-04-01")

private const val ROSTER_HEADER = "team_number\tteam_name\tround\tplayer_name\townership_pct"

private val KOIVUN = CreateGolferRequest(firstName = "Jackson", lastName = "Koivun")

/** On Postgres because the backfill's guard is the UPDATE's WHERE against the real UNIQUE `pga_player_id`. */
class AdminServiceRosterBackfillSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    suspend fun seedLeague(): LeagueId =
        tx.update {
            LeagueRepository().create(CreateLeagueRequest(name = "Castlewood")).id
        }

    suspend fun seedSeason(
        leagueId: LeagueId,
        year: Int,
    ): SeasonId =
        tx.update {
            SeasonRepository().create(CreateSeasonRequest(leagueId = leagueId, name = "$year", seasonYear = year)).id
        }

    fun service(
        vararg athletes: EspnCompetitor,
        golferRepository: GolferRepository = GolferRepository(),
    ): AdminService {
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
            espnService = testEspnService(client = client, golferRepository = golferRepository, tx = tx),
            golferService = GolferService(golferRepository, tx),
            golferRepository = golferRepository,
            teamRepository = TeamRepository(),
        )
    }

    suspend fun koivunRows(): List<Golfer> =
        tx.read { GolferRepository().findAll(search = "Koivun") }

    suspend fun rosterGolferIds(seasonId: SeasonId): List<GolferId> =
        tx.read { TeamRepository().findRostersBySeason(seasonId) }.map { it.golferId }

    test("an ESPN pick named like an earlier upload's id-less row writes its id onto that row, not a second one") {
        val leagueId = seedLeague()
        val earlier = seedSeason(leagueId, 2025)
        val current = seedSeason(leagueId, 2026)
        val brown = fullTeam(1, "BROWN", ConfirmedPick(round = 1, ownershipPct = 100, assignment = newGolfer(KOIVUN)))
        service().confirmRoster(ConfirmRosterRequest(earlier, listOf(brown))).orFail()
        val drafted = koivunRows().single()
        // Brand New is what makes the preview ask ESPN at all.
        val tsv = "$ROSTER_HEADER\n1\tWOMBLE\t1\tJackson Koivun\t100\n1\tWOMBLE\t2\tBrand New\t100"
        val service = service(espnAthlete("espn-koivun", "Jackson Koivun"), espnAthlete("espn-new", "Brand New"))

        val offered =
            service.previewRoster(tsv).orFail().teams.single().picks.first().match
                .shouldBeInstanceOf<PickMatch.MatchedBackfill>()
        val womble = fullTeam(1, "WOMBLE", espnPick(1, offered.espnId, offered.golferName))
        val result = service.confirmRoster(ConfirmRosterRequest(current, listOf(womble))).orFail()

        koivunRows() shouldContainExactly listOf(drafted.copy(pgaPlayerId = "espn-koivun"))
        tx.read { GolferRepository().findByPgaPlayerId("espn-koivun") }?.id shouldBe drafted.id
        rosterGolferIds(earlier) shouldContain drafted.id
        rosterGolferIds(current) shouldContain drafted.id
        result.golfersCreated shouldBe DRAFT_ROUNDS.count() - 1
    }

    test("an ESPN pick whose name two id-less rows carry is refused, and the confirm writes nothing") {
        val seasonId = seedSeason(seedLeague(), 2026)
        val twins = tx.update { listOf(GolferRepository().create(KOIVUN), GolferRepository().create(KOIVUN)) }
        val womble = fullTeam(1, "WOMBLE", espnPick(1, "espn-koivun", "Jackson Koivun"))

        service().confirmRoster(ConfirmRosterRequest(seasonId, listOf(womble))) shouldBe
            Result.Err(AdminError.UnresolvableEspnGolfers(listOf("espn-koivun")))
        koivunRows() shouldContainExactlyInAnyOrder twins
        rosterGolferIds(seasonId).shouldBeEmpty()
    }

    test("an ESPN pick whose name an id-less row shares with another athlete's row is refused") {
        val seasonId = seedSeason(seedLeague(), 2026)
        tx.update {
            GolferRepository().create(KOIVUN)
            GolferRepository().create(KOIVUN.copy(pgaPlayerId = "espn-namesake"))
        }
        val womble = fullTeam(1, "WOMBLE", espnPick(1, "espn-koivun", "Jackson Koivun"))

        service().confirmRoster(ConfirmRosterRequest(seasonId, listOf(womble))) shouldBe
            Result.Err(AdminError.UnresolvableEspnGolfers(listOf("espn-koivun")))
    }

    test("two ESPN picks that would both land on one id-less row are both refused") {
        val seasonId = seedSeason(seedLeague(), 2026)
        tx.update { GolferRepository().create(KOIVUN) }
        val brown = fullTeam(1, "BROWN", espnPick(1, "espn-a", "Jackson Koivun"))
        val womble = fullTeam(2, "WOMBLE", espnPick(1, "espn-b", "Jackson Koivun"))

        service().confirmRoster(ConfirmRosterRequest(seasonId, listOf(brown, womble))) shouldBe
            Result.Err(AdminError.UnresolvableEspnGolfers(listOf("espn-a", "espn-b")))
        koivunRows().map(Golfer::pgaPlayerId) shouldContainExactly listOf(null)
    }

    test("an ESPN pick whose only namesake row holds another athlete's id inserts its own row") {
        val seasonId = seedSeason(seedLeague(), 2026)
        val namesake = tx.update { GolferRepository().create(KOIVUN.copy(pgaPlayerId = "espn-namesake")) }
        val womble = fullTeam(1, "WOMBLE", espnPick(1, "espn-koivun", "Jackson Koivun"))

        service().confirmRoster(ConfirmRosterRequest(seasonId, listOf(womble))).orFail()

        val inserted = tx.read { GolferRepository().findByPgaPlayerId("espn-koivun") }.shouldNotBeNull()
        koivunRows() shouldContainExactlyInAnyOrder listOf(namesake, inserted)
        rosterGolferIds(seasonId) shouldContain inserted.id
        rosterGolferIds(seasonId) shouldNotContain namesake.id
    }

    test("a backfill a concurrent writer beat to the row binds the pick to the row it gave the id") {
        val seasonId = seedSeason(seedLeague(), 2026)
        val winner = tx.update { GolferRepository().create(KOIVUN.copy(pgaPlayerId = "espn-koivun")) }
        val womble = fullTeam(1, "WOMBLE", espnPick(1, "espn-koivun", "Jackson Koivun"))

        service(golferRepository = OnceStaleGolferRepository(GolferRepository()))
            .confirmRoster(ConfirmRosterRequest(seasonId, listOf(womble)))
            .orFail()

        koivunRows() shouldContainExactly listOf(winner)
        rosterGolferIds(seasonId) shouldContain winner.id
    }
})

/**
 * Reads every row as it stood before a concurrent writer set its `pga_player_id`, once each, which is the window that
 * writer commits into; the backfill itself then meets the committed row.
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

private fun newGolfer(request: CreateGolferRequest): GolferAssignment.New =
    GolferAssignment.New(firstName = request.firstName, lastName = request.lastName)

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
