package com.cwfgw.tournaments

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.espn.ContestedCompetitor
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnImportWarnings
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.espn.RosteredSurnameTwin
import com.cwfgw.espn.UnmatchedCompetitor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.scoring.ScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedSeason
import com.cwfgw.testing.seedTeams
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDate

// seedTournament's start date: the fake answers only the date finalize fetches for.
private val WEEK: LocalDate = LocalDate.parse("2026-04-23")
private const val ZURICH_ESPN_ID = "401580901"
private const val CORALES_ESPN_ID = "401580902"
private const val VALSPAR_ESPN_ID = "401580903"

/** The rostered golfers every case seeds; the Kims share a full name and no ESPN id, so neither is told apart. */
private data class Roster(
    val nicolaiHojgaard: Golfer,
    val tomKim: Golfer,
    val tomKimTwin: Golfer,
)

/**
 * "Hojgaard" against the rostered "Højgaard" is the Koivun signature: the matcher reads one surname, and an
 * unnormalized comparison would read two. Real Postgres, because the tripwire reads the season's roster view and
 * the matcher's golfer read, which the fakes only imitate.
 */
class TournamentFinalizeImportWarningsSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("a linked finalize reports a created twin of a rostered surname, a created golfer, and both unplaced kinds") {
        val (seasonId, roster) = tx.update { seedRosteredSeason() }
        val zurich = tx.update { seedTournament(seasonId, "Zurich Classic", ZURICH_ESPN_ID) }
        val field =
            listOf(
                finisher(espnId = "9999", name = "Rasmus Hojgaard", position = 1),
                finisher(espnId = "5000", name = "Sam Burns", position = 2),
                finisher(espnId = "5501", name = "Tom Kim", position = 3),
                finisher(espnId = "5502", name = "", position = 4),
            )

        val finalized = opsService(tx, listOf(event(ZURICH_ESPN_ID, field))).finalizeAndReport(zurich).orFail()

        val twin = RosteredSurnameTwin(created = tx.createdGolfer("9999"), rostered = listOf(roster.nicolaiHojgaard))
        finalized.importWarnings shouldBe
            listOf(
                EspnImportWarnings(
                    tournamentId = zurich,
                    tournamentName = "Zurich Classic",
                    rosteredSurnameTwins = listOf(twin),
                    createdGolfers = listOf(tx.createdGolfer("5000")),
                    unmatchedCompetitors = listOf(UnmatchedCompetitor(espnCompetitorId = "5502", name = "")),
                    contestedCompetitors =
                        listOf(
                            ContestedCompetitor(
                                espnCompetitorId = "5501",
                                name = "Tom Kim",
                                candidates = listOf(roster.tomKim, roster.tomKimTwin),
                            ),
                        ),
                ),
            )
    }

    test("a created golfer whose rostered namesake has its own result in the event is created, not a twin") {
        val (seasonId, _) = tx.update { seedRosteredSeason() }
        val zurich = tx.update { seedTournament(seasonId, "Zurich Classic", ZURICH_ESPN_ID) }
        val field =
            listOf(
                finisher(espnId = "4410", name = "Nicolai Højgaard", position = 1),
                finisher(espnId = "9999", name = "Rasmus Hojgaard", position = 2),
            )

        val finalized = opsService(tx, listOf(event(ZURICH_ESPN_ID, field))).finalizeAndReport(zurich).orFail()

        finalized.importWarnings shouldBe
            listOf(noWarnings(zurich, "Zurich Classic").copy(createdGolfers = listOf(tx.createdGolfer("9999"))))
    }

    test("a created golfer sharing a surname with two rostered golfers names both as its twins") {
        val (seasonId, roster) = tx.update { seedRosteredSeason() }
        val zurich = tx.update { seedTournament(seasonId, "Zurich Classic", ZURICH_ESPN_ID) }
        val field = listOf(finisher(espnId = "7000", name = "Joohyung Kim", position = 1))

        val finalized = opsService(tx, listOf(event(ZURICH_ESPN_ID, field))).finalizeAndReport(zurich).orFail()

        val twin =
            RosteredSurnameTwin(created = tx.createdGolfer("7000"), rostered = listOf(roster.tomKim, roster.tomKimTwin))
        finalized.importWarnings shouldBe
            listOf(noWarnings(zurich, "Zurich Classic").copy(rosteredSurnameTwins = listOf(twin)))
    }

    test("a team partner the import creates a golfer for is reported as created") {
        val (seasonId, _) = tx.update { seedRosteredSeason() }
        val zurich = tx.update { seedTournament(seasonId, "Zurich Classic", ZURICH_ESPN_ID) }
        val partner = finisher(espnId = "team:t1:0", name = "Griffin", position = 1).copy(isTeamPartner = true)
        val scoreboard = listOf(event(ZURICH_ESPN_ID, listOf(partner)))

        val finalized = opsService(tx, scoreboard).finalizeAndReport(zurich).orFail()

        finalized.importWarnings.single().createdGolfers.map { it.firstName to it.lastName } shouldBe
            listOf("" to "Griffin")
    }

    test("a finalize whose import credits every competitor answers that tournament with no warnings") {
        val (seasonId, _) = tx.update { seedRosteredSeason() }
        val zurich = tx.update { seedTournament(seasonId, "Zurich Classic", ZURICH_ESPN_ID) }
        val field = listOf(finisher(espnId = "4410", name = "Nicolai Højgaard", position = 1))

        val finalized = opsService(tx, listOf(event(ZURICH_ESPN_ID, field))).finalizeAndReport(zurich).orFail()

        finalized.tournament.status shouldBe TournamentStatus.Completed
        finalized.importWarnings shouldBe listOf(noWarnings(zurich, "Zurich Classic"))
    }

    test("a linked finalize whose event is missing from the scoreboard imports nothing and answers an empty list") {
        val (seasonId, _) = tx.update { seedRosteredSeason() }
        val zurich = tx.update { seedTournament(seasonId, "Zurich Classic", ZURICH_ESPN_ID) }

        val finalized = opsService(tx, scoreboard = emptyList()).finalizeAndReport(zurich).orFail()

        finalized.importWarnings.shouldBeEmpty()
    }

    test("an unlinked finalize reports each match it imported into under that match, a clean one included") {
        val (seasonId, roster) = tx.update { seedRosteredSeason() }
        val zurich = tx.update { seedTournament(seasonId, "Zurich Classic", ZURICH_ESPN_ID) }
        val corales = tx.update { seedTournament(seasonId, "Corales Puntacana", CORALES_ESPN_ID) }
        val valspar = tx.update { seedTournament(seasonId, "Valspar Championship", VALSPAR_ESPN_ID) }
        val unlinked = tx.update { seedTournament(seasonId, "Week placeholder", pgaTournamentId = null) }
        val scoreboard =
            listOf(
                event(ZURICH_ESPN_ID, listOf(finisher(espnId = "5000", name = "Sam Burns", position = 1))),
                event(CORALES_ESPN_ID, listOf(finisher(espnId = "9999", name = "Rasmus Hojgaard", position = 1))),
                event(VALSPAR_ESPN_ID, listOf(finisher(espnId = "4410", name = "Nicolai Højgaard", position = 1))),
            )

        val finalized = opsService(tx, scoreboard).finalizeAndReport(unlinked).orFail()

        val twin = RosteredSurnameTwin(created = tx.createdGolfer("9999"), rostered = listOf(roster.nicolaiHojgaard))
        finalized.importWarnings shouldBe
            listOf(
                noWarnings(zurich, "Zurich Classic").copy(createdGolfers = listOf(tx.createdGolfer("5000"))),
                noWarnings(corales, "Corales Puntacana").copy(rosteredSurnameTwins = listOf(twin)),
                noWarnings(valspar, "Valspar Championship"),
            )
    }
})

private fun noWarnings(
    tournamentId: TournamentId,
    tournamentName: String,
): EspnImportWarnings =
    EspnImportWarnings(
        tournamentId = tournamentId,
        tournamentName = tournamentName,
        rosteredSurnameTwins = emptyList(),
        createdGolfers = emptyList(),
        unmatchedCompetitors = emptyList(),
        contestedCompetitors = emptyList(),
    )

private suspend fun Transactor.createdGolfer(espnId: String): Golfer =
    checkNotNull(read { GolferRepository().findByPgaPlayerId(espnId) }) { "no golfer holds ESPN id $espnId" }

/** Ranked so the golfer list, and so the contest's candidates, name the Kims in this order. */
context(ctx: WriteTransactionContext)
private fun seedRosteredSeason(): Pair<SeasonId, Roster> {
    val seasonId = seedSeason()
    val (alpha, bravo) = seedTeams(seasonId)
    val golfers = GolferRepository()
    val roster =
        Roster(
            nicolaiHojgaard =
                golfers.create(CreateGolferRequest(pgaPlayerId = "4410", firstName = "Nicolai", lastName = "Højgaard")),
            tomKim = golfers.create(CreateGolferRequest(firstName = "Tom", lastName = "Kim", worldRanking = 20)),
            tomKimTwin = golfers.create(CreateGolferRequest(firstName = "Tom", lastName = "Kim", worldRanking = 21)),
        )
    TeamRepository().addToRoster(alpha, AddToRosterRequest(golferId = roster.nicolaiHojgaard.id))
    TeamRepository().addToRoster(alpha, AddToRosterRequest(golferId = roster.tomKim.id))
    TeamRepository().addToRoster(bravo, AddToRosterRequest(golferId = roster.tomKimTwin.id))
    return seasonId to roster
}

private fun event(
    espnId: String,
    field: List<EspnCompetitor>,
): EspnTournament =
    EspnTournament(espnId = espnId, name = espnId, completed = true, competitors = field, isTeamEvent = false)

private fun finisher(
    espnId: String,
    name: String,
    position: Int,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = position,
        scoreStr = "-$position",
        scoreToPar = -position,
        totalStrokes = 280,
        roundScores = listOf(70, 70, 70, 70),
        position = position,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

private fun opsService(
    tx: Transactor,
    scoreboard: List<EspnTournament>,
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
        espnService =
            testEspnService(
                client = FakeEspnClient(tournamentsByDate = mapOf(WEEK to scoreboard)),
                seasonRepository = SeasonRepository(),
                golferRepository = GolferRepository(),
                teamRepository = TeamRepository(),
                tournamentRepository = TournamentRepository(),
                linkRepository = TournamentLinkRepository(),
                tx = tx,
            ),
        tx = tx,
    )
