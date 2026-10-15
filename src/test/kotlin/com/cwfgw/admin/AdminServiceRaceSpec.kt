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
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.CreateLeagueRequest
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
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicBoolean

private val RECENT_DATE: LocalDate = LocalDate.parse("2026-04-01")

/**
 * Each loser's read-first guard is blinded once, which is the window a concurrent winner commits into;
 * the winner's row is written up front, so the loser's insert trips the same 23505 a true race would.
 */
class AdminServiceRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    suspend fun seedSeason(): SeasonId =
        tx.update {
            val league = LeagueRepository().create(CreateLeagueRequest(name = "Castlewood"))
            SeasonRepository().create(CreateSeasonRequest(leagueId = league.id, name = "2026", seasonYear = 2026)).id
        }

    fun service(
        tournamentRepository: TournamentRepository = TournamentRepository(),
        golferRepository: GolferRepository = GolferRepository(),
        competitors: List<EspnCompetitor> = emptyList(),
    ): AdminService {
        val recent = EspnCalendarEntry(id = "e-recent", label = "Recent", startDate = "2026-04-01T00:00Z")
        val client =
            FakeEspnClient(
                calendar = listOf(recent),
                tournamentsByDate = mapOf(RECENT_DATE to listOf(espnEvent(competitors))),
            )
        return AdminService(
            tx = tx,
            seasonService = SeasonService(SeasonRepository(), tx),
            tournamentService = TournamentService(tournamentRepository, tx),
            espnService = testEspnService(client = client, golferRepository = golferRepository, tx = tx),
            golferService = GolferService(golferRepository, tx),
            golferRepository = golferRepository,
            teamRepository = TeamRepository(),
        )
    }

    test("a season confirm that loses the race on an ESPN event skips it as already linked") {
        val seasonId = seedSeason()
        val winner =
            tx.update {
                TournamentRepository().create(
                    CreateTournamentRequest(
                        pgaTournamentId = "evt-401",
                        name = "Masters",
                        seasonId = seasonId,
                        startDate = LocalDate.parse("2026-04-09"),
                        endDate = LocalDate.parse("2026-04-12"),
                    ),
                )
            }
        val entry =
            ConfirmedTournamentEntry(
                espnEventId = "evt-401",
                name = "Masters",
                startDate = LocalDate.parse("2026-04-09"),
                endDate = LocalDate.parse("2026-04-12"),
            )

        val result =
            service(tournamentRepository = OnceBlindTournamentRepository(TournamentRepository()))
                .confirmSeasonSchedule(seasonId, listOf(entry))
                .orFail()

        result.created.shouldBeEmpty()
        result.skipped.map { it.reason } shouldContainExactly listOf("already linked to tournament ${winner.id.value}")
    }

    test("a roster confirm that loses the race on a team name answers TeamNameTaken") {
        val seasonId = seedSeason()
        val brown = fullTeam(number = 1, name = "BROWN")
        service().confirmRoster(ConfirmRosterRequest(seasonId = seasonId, teams = listOf(brown))).orFail()

        service().confirmRoster(ConfirmRosterRequest(seasonId = seasonId, teams = listOf(brown))) shouldBe
            Result.Err(AdminError.TeamNameTaken(seasonId))
    }

    test("a roster preview that misses the row an ESPN athlete already holds writes no second one") {
        seedSeason()
        tx.update {
            GolferRepository().create(
                CreateGolferRequest(pgaPlayerId = "espn-1", firstName = "Scottie", lastName = "Scheffler"),
            )
        }
        val tsv = "team_number\tteam_name\tround\tplayer_name\townership_pct\n1\tBROWN\t1\tScottie Scheffler\t100"
        val golfers = OnceBlindGolferRepository(GolferRepository())

        val preview =
            service(golferRepository = golfers, competitors = listOf(espnPlayer("espn-1", "Scottie Scheffler")))
                .previewRoster(tsv)
                .orFail()

        preview.matchedCount shouldBe 1
        tx.read { GolferRepository().findAll(search = null) }.map { it.pgaPlayerId } shouldContainExactly
            listOf("espn-1")
    }

    test("a roster confirm that loses the race inserting an ESPN athlete binds the pick to the winner's row") {
        val seasonId = seedSeason()
        val winner =
            tx.update {
                GolferRepository().create(
                    CreateGolferRequest(pgaPlayerId = "espn-1", firstName = "Scottie", lastName = "Scheffler"),
                )
            }
        val scottie = GolferAssignment.Espn("espn-1", "Scottie Scheffler")
        val brown =
            fullTeam(number = 1, name = "BROWN", ConfirmedPick(round = 1, ownershipPct = 100, assignment = scottie))

        service(golferRepository = OnceBlindPgaIdGolferRepository(GolferRepository()))
            .confirmRoster(ConfirmRosterRequest(seasonId = seasonId, teams = listOf(brown)))
            .orFail()

        tx.read { GolferRepository().findAll(search = null) }.filter { it.pgaPlayerId == "espn-1" } shouldContainExactly
            listOf(winner)
        tx.read { TeamRepository().findRostersBySeason(seasonId) }.single { it.draftRound == 1 }.golferId shouldBe
            winner.id
    }
})

private class OnceBlindTournamentRepository(
    delegate: TournamentRepository,
) : TournamentRepository by delegate {
    private val inner = delegate
    private val blind = AtomicBoolean(true)

    context(ctx: TransactionContext)
    override fun findByPgaTournamentId(pgaTournamentId: String): Tournament? =
        if (blind.getAndSet(false)) null else inner.findByPgaTournamentId(pgaTournamentId)
}

/** @see OnceBlindTournamentRepository */
private class OnceBlindGolferRepository(
    delegate: GolferRepository,
) : GolferRepository by delegate {
    private val inner = delegate
    private val blind = AtomicBoolean(true)

    context(ctx: TransactionContext)
    override fun findAll(search: String?): List<Golfer> =
        if (blind.getAndSet(false)) emptyList() else inner.findAll(search)
}

/** @see OnceBlindTournamentRepository */
private class OnceBlindPgaIdGolferRepository(
    delegate: GolferRepository,
) : GolferRepository by delegate {
    private val inner = delegate
    private val blind = AtomicBoolean(true)

    context(ctx: TransactionContext)
    override fun findByPgaPlayerId(pgaPlayerId: String): Golfer? =
        if (blind.getAndSet(false)) null else inner.findByPgaPlayerId(pgaPlayerId)
}

private fun espnPlayer(
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

private fun espnEvent(competitors: List<EspnCompetitor>): EspnTournament =
    EspnTournament(
        espnId = "evt-1",
        name = "Test Event",
        completed = true,
        competitors = competitors,
        isTeamEvent = false,
    )
