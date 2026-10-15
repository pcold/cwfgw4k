package com.cwfgw.espn

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferRepository
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
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.LocalDate

private const val FLEETWOOD_ESPN_ID = "100"

/** One ESPN id is one athlete, so a row it alone claims is no contest however many times ESPN lists it. */
class EspnRepeatedIdSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("one event listing an id twice by the full name of one id-less row backfills it, previewed and imported") {
        val seeded = tx.update { seedFleetwood(eventIds = listOf("evt-a")) }
        val tournament = seeded.tournaments.single()
        val event = listedTwice("evt-a")
        val espn = espnService(tx, mapOf(tournament.startDate to listOf(event)))
        val ctx = espn.loadLivePreviewContext(seeded.seasonId, seeded.tournaments)

        val preview = espn.previewForTournament(ctx, tournament).orFail()
        val warnings = tx.update { espn.persistImportIn(tournament, event) }

        preview.leaderboard.map { it.teamNames } shouldContainExactly List(2) { listOf("Alpha") }
        warnings.single().contestedCompetitors.shouldBeEmpty()
        tx.assertBackfilledAndCredited(seeded.fleetwood, seeded.tournaments)
    }

    test("two events each listing an id twice on one id-less row backfill it once and credit it in both") {
        val seeded = tx.update { seedFleetwood(eventIds = listOf("evt-a", "evt-b")) }
        val imports = seeded.tournaments.map { EventImport(it, listedTwice(checkNotNull(it.pgaTournamentId))) }
        val espn = espnService(tx, emptyMap())

        val warnings = tx.update { espn.persistImportsIn(imports) }

        warnings.flatMap { it.contestedCompetitors }.shouldBeEmpty()
        tx.assertBackfilledAndCredited(seeded.fleetwood, seeded.tournaments)
    }
})

private data class SeededFleetwood(
    val seasonId: SeasonId,
    val fleetwood: Golfer,
    val tournaments: List<Tournament>,
)

/** Rostered, so the preview's leaderboard names the team its row is credited to. */
context(ctx: WriteTransactionContext)
private fun seedFleetwood(eventIds: List<String>): SeededFleetwood {
    val seasonId = seedSeason()
    val alpha = seedTeams(seasonId).first()
    val fleetwood = GolferRepository().create(CreateGolferRequest(firstName = "Tommy", lastName = "Fleetwood"))
    TeamRepository().addToRoster(alpha, AddToRosterRequest(golferId = fleetwood.id))
    val tournaments =
        eventIds.map { eventId ->
            checkNotNull(TournamentRepository().findById(seedTournament(seasonId, "Event $eventId", eventId)))
        }
    return SeededFleetwood(seasonId = seasonId, fleetwood = fleetwood, tournaments = tournaments)
}

private suspend fun Transactor.assertBackfilledAndCredited(
    fleetwood: Golfer,
    tournaments: List<Tournament>,
) {
    read { GolferRepository().findAll(search = null) }.map { it.id to it.pgaPlayerId } shouldContainExactly
        listOf(fleetwood.id to FLEETWOOD_ESPN_ID)
    tournaments.forEach { tournament ->
        read { TournamentRepository().getResults(tournament.id) }.map { it.golferId } shouldBe listOf(fleetwood.id)
    }
}

private fun espnService(
    tx: Transactor,
    scoreboards: Map<LocalDate, List<EspnTournament>>,
): EspnService =
    testEspnService(
        client = FakeEspnClient(tournamentsByDate = scoreboards),
        seasonRepository = SeasonRepository(),
        golferRepository = GolferRepository(),
        teamRepository = TeamRepository(),
        tournamentRepository = TournamentRepository(),
        linkRepository = TournamentLinkRepository(),
        tx = tx,
    )

private fun listedTwice(espnId: String): EspnTournament =
    EspnTournament(
        espnId = espnId,
        name = "Event $espnId",
        completed = true,
        competitors = List(2) { fleetwood() },
        isTeamEvent = false,
    )

private fun fleetwood(): EspnCompetitor =
    EspnCompetitor(
        espnId = FLEETWOOD_ESPN_ID,
        name = "Tommy Fleetwood",
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
