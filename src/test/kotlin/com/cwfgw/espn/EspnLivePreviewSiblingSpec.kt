package com.cwfgw.espn

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.NewRosterEntry
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedSeason
import com.cwfgw.testing.seedTeams
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentOpsService
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentStatus
import com.cwfgw.tournaments.UpdateTournamentRequest
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate

/** seedTournament's one week, so the unlinked finalize's scoreboard holds both linked events. */
private val WEEK: LocalDate = LocalDate.parse("2026-04-23")
private const val GUESSING_EVENT = "evt-guess"
private const val CLAIMING_EVENT = "evt-claim"

/**
 * Jim Bloggs surname-guesses the rostered, id-less Joe Bloggs row in one event while Joe claims it by full name in a
 * sibling the same unlinked finalize imports, so only a read settling both events together sees the claim.
 */
class EspnLivePreviewSiblingSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("a preview pays each sibling event what the unlinked finalize importing both of them scores") {
        val week = tx.update { seedWeek() }
        val espn = espnService(tx)
        val ctx = tx.read { espn.loadLivePreviewContextIn(week.seasonId, listOf(week.guessing)) }
        val previewed = week.linked.associate { it.id to espn.previewForTournament(ctx, it).orFail().payouts() }

        opsService(tx, espn).finalizeAndReport(week.unlinked.id).orFail()

        week.linked.forEach { tournament ->
            previewed.getValue(tournament.id) shouldBe tx.scoredPayouts(week.seasonId, tournament.id)
        }
        previewed.getValue(week.guessing.id).shouldBeEmpty()
        previewed.getValue(week.claiming.id).shouldNotBeEmpty()
    }

    test("the link editor's listing names no row for a guess a sibling event's full name claims") {
        val week = tx.update { seedWeek() }

        val listing = espnService(tx).listCompetitorsForLinking(week.guessing.id).orFail()

        listing.competitors.map { it.name to it.linkedGolfer } shouldContainExactly listOf("Jim Bloggs" to null)
    }

    test("a finalized tournament's listing settles its event alone, since no unlinked finalize imports it") {
        val week = tx.update { seedWeek() }
        val joe =
            tx.update {
                val completed = UpdateTournamentRequest(status = TournamentStatus.Completed)
                TournamentRepository().update(week.guessing.id, completed)
                GolferRepository().findAll(search = null).single()
            }

        val listing = espnService(tx).listCompetitorsForLinking(week.guessing.id).orFail()

        listing.competitors.map { it.name to it.linkedGolfer } shouldContainExactly listOf("Jim Bloggs" to joe)
    }

    test("with no unlinked tournament that day, a preview pays what the linked tournament's own finalize scores") {
        val week = tx.update { seedWeek() }
        val completed = UpdateTournamentRequest(status = TournamentStatus.Completed)
        tx.update { TournamentRepository().update(week.unlinked.id, completed) }
        val espn = espnService(tx)
        val ctx = tx.read { espn.loadLivePreviewContextIn(week.seasonId, listOf(week.guessing)) }
        val previewed = espn.previewForTournament(ctx, week.guessing).orFail().payouts()

        opsService(tx, espn).finalizeAndReport(week.guessing.id).orFail()

        previewed shouldBe tx.scoredPayouts(week.seasonId, week.guessing.id)
        previewed.shouldNotBeEmpty()
    }
})

private data class SiblingWeek(
    val seasonId: SeasonId,
    val guessing: Tournament,
    val claiming: Tournament,
    val unlinked: Tournament,
) {
    val linked: List<Tournament> get() = listOf(guessing, claiming)
}

/** The payout is scale-stripped, since the preview computes it and scoring reads it back from a NUMERIC column. */
private data class TeamPayout(
    val teamId: TeamId,
    val golferId: GolferId,
    val payout: BigDecimal,
)

context(ctx: WriteTransactionContext)
private fun seedWeek(): SiblingWeek {
    val seasonId = seedSeason()
    val alpha = seedTeams(seasonId).first()
    val joe = GolferRepository().create(CreateGolferRequest(firstName = "Joe", lastName = "Bloggs"))
    TeamRepository().addToRoster(alpha, NewRosterEntry(golferId = joe.id))

    fun seeded(
        name: String,
        espnId: String?,
    ): Tournament = checkNotNull(TournamentRepository().findById(seedTournament(seasonId, name, espnId)))
    return SiblingWeek(
        seasonId = seasonId,
        guessing = seeded("Zurich Classic", GUESSING_EVENT),
        claiming = seeded("Corales Puntacana", CLAIMING_EVENT),
        unlinked = seeded("Week placeholder", null),
    )
}

private fun EspnLivePreview.payouts(): Set<TeamPayout> =
    teams.flatMap { team ->
        team.golferScores.map { TeamPayout(team.teamId, it.golferId, it.payout.stripTrailingZeros()) }
    }.toSet()

private suspend fun Transactor.scoredPayouts(
    seasonId: SeasonId,
    tournamentId: TournamentId,
): Set<TeamPayout> =
    read { ScoringRepository().getScores(seasonId, tournamentId) }
        .map { TeamPayout(it.teamId, it.golferId, it.payout.stripTrailingZeros()) }
        .toSet()

private fun espnService(tx: Transactor): EspnService =
    testEspnService(
        client = FakeEspnClient(tournamentsByDate = mapOf(WEEK to scoreboard())),
        seasonRepository = SeasonRepository(),
        golferRepository = GolferRepository(),
        teamRepository = TeamRepository(),
        tournamentRepository = TournamentRepository(),
        linkRepository = TournamentLinkRepository(),
        tx = tx,
    )

private fun opsService(
    tx: Transactor,
    espn: EspnService,
): TournamentOpsService =
    TournamentOpsService(
        tournamentRepository = TournamentRepository(),
        scoringService =
            ScoringService(
                repository = ScoringRepository(),
                seasonRepository = SeasonRepository(),
                tournamentRepository = TournamentRepository(),
                teamRepository = TeamRepository(),
            ),
        scoringRepository = ScoringRepository(),
        espnService = espn,
        tx = tx,
    )

private fun scoreboard(): List<EspnTournament> =
    listOf(
        completedEvent(GUESSING_EVENT, finisher("2", "Jim Bloggs")),
        completedEvent(CLAIMING_EVENT, finisher("1", "Joe Bloggs")),
    )

private fun completedEvent(
    espnId: String,
    winner: EspnCompetitor,
): EspnTournament =
    EspnTournament(
        espnId = espnId,
        name = "Event $espnId",
        completed = true,
        competitors = listOf(winner),
        isTeamEvent = false,
    )

private fun finisher(
    espnId: String,
    name: String,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = 1,
        scoreStr = "-12",
        scoreToPar = -12,
        totalStrokes = 276,
        roundScores = listOf(69, 69, 69, 69),
        position = 1,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )
