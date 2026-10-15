package com.cwfgw.tournaments

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.scoring.FantasyScore
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.scoring.UpsertScore
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedSeason
import com.cwfgw.testing.seedTeams
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.LocalDate

private val UNLINKED_WEEK: LocalDate = LocalDate.parse("2026-04-23")
private val LINKED_WEEK: LocalDate = LocalDate.parse("2026-04-30")
private val MATCH_NAMES: List<String> = listOf("Zurich Classic", "Corales Puntacana")
private val UNLINKED_ESPN_IDS: List<String> = listOf("401580901", "401580902")
private val LINKED_ESPN_IDS: List<String> = listOf("401580911", "401580912")

private data class Pro(
    val espnId: String,
    val firstName: String,
    val lastName: String,
)

private val SCHEFFLER = Pro(espnId = "9478", firstName = "Scottie", lastName = "Scheffler")
private val MCILROY = Pro(espnId = "3470", firstName = "Rory", lastName = "McIlroy")
private val MORIKAWA = Pro(espnId = "10592", firstName = "Collin", lastName = "Morikawa")

/**
 * An ESPN id is unique across seasons, so the season whose matches finalize one by one links the same two events
 * under ids of its own, a week later.
 */
class TournamentOpsServiceUnlinkedScoringSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("an unlinked finalize scores each tournament it imports into as that tournament's own finalize would") {
        val golfers = tx.update { seedGolfers() }
        val unlinkedWeek = tx.update { seedWeek(golfers, UNLINKED_WEEK, UNLINKED_ESPN_IDS) }
        val linkedWeek = tx.update { seedWeek(golfers, LINKED_WEEK, LINKED_ESPN_IDS) }
        val ops = opsService(tx)

        ops.finalizeAndReport(unlinkedWeek.unlinked.id).orFail()
        linkedWeek.matches.forEach { match -> ops.finalizeAndReport(match.id).orFail() }

        val payouts = tx.payoutsOf(unlinkedWeek.seasonId)
        payouts.map(TeamPayout::tournament).toSet() shouldBe MATCH_NAMES.toSet()
        payouts shouldContainExactlyInAnyOrder tx.payoutsOf(linkedWeek.seasonId)
    }

    test("a scoring failure on the second match rolls back the whole unlinked finalize, the first's scores included") {
        val golfers = tx.update { seedGolfers() }
        val week = tx.update { seedWeek(golfers, UNLINKED_WEEK, UNLINKED_ESPN_IDS) }
        val (first, second) = week.matches
        val upserts = RecordingScoringRepository(ScoringRepository())
        val ops =
            opsService(
                tx,
                scoringTournaments = HidingTournamentRepository(TournamentRepository(), second.id),
                scores = upserts,
            )

        ops.finalizeAndReport(week.unlinked.id) shouldBe Result.Err(TournamentOpsError.TournamentNotFound(second.id))

        upserts.scoredTournaments.toSet() shouldBe setOf(first.id)
        week.matches.forEach { match ->
            tx.get { TournamentRepository().findById(match.id) }?.status shouldBe TournamentStatus.Upcoming
            tx.get { TournamentRepository().getResults(match.id) }.shouldBeEmpty()
        }
        tx.payoutsOf(week.seasonId).shouldBeEmpty()
    }

    test("an unlinked finalize leaves the unlinked tournament upcoming, with no results or scores of its own") {
        val golfers = tx.update { seedGolfers() }
        val week = tx.update { seedWeek(golfers, UNLINKED_WEEK, UNLINKED_ESPN_IDS) }

        opsService(tx).finalizeAndReport(week.unlinked.id).orFail().tournament.status shouldBe TournamentStatus.Upcoming

        tx.get { TournamentRepository().getResults(week.unlinked.id) }.shouldBeEmpty()
        tx.get { ScoringRepository().getScores(week.seasonId, week.unlinked.id) }.shouldBeEmpty()
        week.matches.forEach { match ->
            tx.get { TournamentRepository().findById(match.id) }?.status shouldBe TournamentStatus.Completed
        }
    }
})

/** Tournament and team by name, so two seasons' scores compare; the golfers are seeded once and shared by both. */
private data class TeamPayout(
    val tournament: String,
    val team: String,
    val golferId: GolferId,
    val position: Int,
    val numTied: Int,
    val basePayout: BigDecimal,
    val ownershipPct: BigDecimal,
    val payout: BigDecimal,
    val multiplier: BigDecimal,
)

private suspend fun Transactor.payoutsOf(seasonId: SeasonId): List<TeamPayout> =
    read {
        val tournaments = TournamentRepository().findAll(TournamentFilter(seasonId = seasonId)).associateBy { it.id }
        val teams = TeamRepository().findBySeason(seasonId).associateBy { it.id }
        ScoringRepository().getScoresBySeason(seasonId, tournamentIds = null).map { score ->
            TeamPayout(
                tournament = tournaments.getValue(score.tournamentId).name,
                team = teams.getValue(score.teamId).teamName,
                golferId = score.golferId,
                position = score.position,
                numTied = score.numTied,
                basePayout = score.basePayout,
                ownershipPct = score.ownershipPct,
                payout = score.payout,
                multiplier = score.multiplier,
            )
        }
    }

private data class SeededWeek(
    val seasonId: SeasonId,
    val unlinked: Tournament,
    val matches: List<Tournament>,
)

context(ctx: WriteTransactionContext)
private fun seedGolfers(): Map<Pro, GolferId> =
    listOf(SCHEFFLER, MCILROY, MORIKAWA).associateWith { pro ->
        GolferRepository().create(
            CreateGolferRequest(pgaPlayerId = pro.espnId, firstName = pro.firstName, lastName = pro.lastName),
        ).id
    }

/** Scheffler is split between two teams, and the second match pays double, so scoring by the wrong match shows. */
context(ctx: WriteTransactionContext)
private fun seedWeek(
    golfers: Map<Pro, GolferId>,
    startDate: LocalDate,
    espnIds: List<String>,
): SeededWeek {
    val seasonId = seedSeason()
    val (alpha, bravo, charlie) = seedTeams(seasonId)
    val halfScheffler = AddToRosterRequest(golferId = golfers.getValue(SCHEFFLER), ownershipPct = BigDecimal(50))
    TeamRepository().addToRoster(alpha, halfScheffler)
    TeamRepository().addToRoster(charlie, halfScheffler)
    TeamRepository().addToRoster(bravo, AddToRosterRequest(golferId = golfers.getValue(MCILROY)))
    TeamRepository().addToRoster(charlie, AddToRosterRequest(golferId = golfers.getValue(MORIKAWA)))
    val matches =
        MATCH_NAMES.zip(espnIds).mapIndexed { index, (name, espnId) ->
            TournamentRepository().create(
                CreateTournamentRequest(
                    name = name,
                    seasonId = seasonId,
                    startDate = startDate,
                    endDate = startDate.plusDays(3),
                    pgaTournamentId = espnId,
                    payoutMultiplier = BigDecimal(index + 1),
                ),
            )
        }
    val unlinked =
        TournamentRepository().create(
            CreateTournamentRequest(
                name = "Week placeholder",
                seasonId = seasonId,
                startDate = startDate,
                endDate = startDate.plusDays(3),
            ),
        )
    return SeededWeek(seasonId = seasonId, unlinked = unlinked, matches = matches)
}

/** The first event ties two golfers for second; the second's winner is the first's runner-up. */
private fun scoreboard(espnIds: List<String>): List<EspnTournament> {
    val finishes =
        listOf(
            listOf(SCHEFFLER to 1, MCILROY to 2, MORIKAWA to 2),
            listOf(MORIKAWA to 1, SCHEFFLER to 3, MCILROY to 12),
        )
    return MATCH_NAMES.zip(espnIds).zip(finishes).map { (event, field) ->
        EspnTournament(
            espnId = event.second,
            name = event.first,
            completed = true,
            competitors =
                field.mapIndexed { index, (pro, position) ->
                    finisher(pro, order = index + 1, position = position)
                },
            isTeamEvent = false,
        )
    }
}

private fun finisher(
    pro: Pro,
    order: Int,
    position: Int,
): EspnCompetitor =
    EspnCompetitor(
        espnId = pro.espnId,
        name = "${pro.firstName} ${pro.lastName}",
        order = order,
        scoreStr = "-$position",
        scoreToPar = -position,
        totalStrokes = 280,
        roundScores = listOf(70, 70, 70, 70),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

/** Only scoring reads through [scoringTournaments]; finalize and the import see every row. */
private fun opsService(
    tx: Transactor,
    scoringTournaments: TournamentRepository = TournamentRepository(),
    scores: ScoringRepository = ScoringRepository(),
): TournamentOpsService {
    val scoreboards = mapOf(UNLINKED_WEEK to scoreboard(UNLINKED_ESPN_IDS), LINKED_WEEK to scoreboard(LINKED_ESPN_IDS))
    return TournamentOpsService(
        tournamentRepository = TournamentRepository(),
        scoringService =
            ScoringService(
                repository = scores,
                seasonRepository = SeasonRepository(),
                tournamentRepository = scoringTournaments,
                teamRepository = TeamRepository(),
            ),
        scoringRepository = ScoringRepository(),
        espnService =
            testEspnService(
                client = FakeEspnClient(tournamentsByDate = scoreboards),
                seasonRepository = SeasonRepository(),
                golferRepository = GolferRepository(),
                teamRepository = TeamRepository(),
                tournamentRepository = TournamentRepository(),
                linkRepository = TournamentLinkRepository(),
                tx = tx,
            ),
        tx = tx,
    )
}

/** `calculateScoresIn` returns Err on a null [findById]; finalize has imported every match by then. */
private class HidingTournamentRepository(
    private val delegate: TournamentRepository,
    private val hidden: TournamentId,
) : TournamentRepository by delegate {
    context(ctx: TransactionContext)
    override fun findById(id: TournamentId): Tournament? = if (id == hidden) null else delegate.findById(id)
}

/** Shows a rolled-back finalize had written scores before it failed, which the committed rows no longer can. */
private class RecordingScoringRepository(
    private val delegate: ScoringRepository,
) : ScoringRepository by delegate {
    val scoredTournaments: MutableList<TournamentId> = mutableListOf()

    context(ctx: WriteTransactionContext)
    override fun upsertScore(record: UpsertScore): FantasyScore {
        scoredTournaments += record.tournamentId
        return delegate.upsertScore(record)
    }
}
