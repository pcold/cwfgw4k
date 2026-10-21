package com.cwfgw.espn

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.noopTransactionContext
import com.cwfgw.testing.orFail
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentPlayerOverride
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentId
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

private val START_DATE = LocalDate.parse("2026-04-09")

private val MASTERS =
    Tournament(
        id = TournamentId(UUID.fromString("00000000-0000-0000-0000-000000000401")),
        pgaTournamentId = "evt-401",
        name = "Masters",
        seasonId = SeasonId(UUID.fromString("00000000-0000-0000-0000-000000000026")),
        startDate = START_DATE,
        endDate = START_DATE.plusDays(3),
        courseName = null,
        status = TournamentStatus.InProgress,
        purseAmount = null,
        payoutMultiplier = BigDecimal("1.0000"),
        week = null,
        isTeamEvent = false,
        createdAt = Instant.EPOCH,
    )

private fun golfer(
    serial: Long,
    firstName: String,
    lastName: String,
    pgaPlayerId: String? = null,
    tombstonedAt: Instant? = null,
): Golfer =
    Golfer(
        id = GolferId(UUID(0L, serial)),
        pgaPlayerId = pgaPlayerId,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        updatedAt = Instant.EPOCH,
        tombstonedAt = tombstonedAt,
    )

/** [position] doubles as each competitor's key in the results, so it must differ across one field. */
private fun competitor(
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

private fun masters(vararg competitors: EspnCompetitor): EspnTournament =
    EspnTournament(
        espnId = "evt-401",
        name = "Masters",
        completed = true,
        competitors = competitors.toList(),
        isTeamEvent = false,
    )

private class MixedFieldFixture(
    golfers: List<Golfer>,
    overrides: Map<String, GolferId>,
    val event: EspnTournament,
) {
    val golferRepo = FakeGolferRepository(initial = golfers)
    val tournamentRepo = FakeTournamentRepository(initial = listOf(MASTERS))
    val service: EspnService =
        testEspnService(
            client = FakeEspnClient(tournamentsByDate = mapOf(START_DATE to listOf(event))),
            golferRepository = golferRepo,
            tournamentRepository = tournamentRepo,
            linkRepository =
                FakeTournamentLinkRepository(
                    initial =
                        overrides.map { (espnId, golferId) ->
                            TournamentPlayerOverride(
                                tournamentId = MASTERS.id,
                                espnCompetitorId = espnId,
                                golferId = golferId,
                            )
                        },
                ),
        )
}

private fun MixedFieldFixture.persistImport() = with(noopTransactionContext) { service.persistImportIn(MASTERS, event) }

private fun MixedFieldFixture.golferByPosition(): Map<Int?, GolferId> =
    with(noopTransactionContext) { tournamentRepo.getResults(MASTERS.id) }.associate { it.position to it.golferId }

private suspend fun MixedFieldFixture.linkedGolfers(): Map<String, GolferId?> =
    service.listCompetitorsForLinking(MASTERS.id).orFail().competitors
        .associate { it.espnCompetitorId to it.linkedGolfer?.id }

private fun MixedFieldFixture.pointLookups(): Int = golferRepo.findByIdCalls + golferRepo.findByPgaPlayerIdCalls

private val COLLIN = golfer(1, "Collin", "Morikawa", pgaPlayerId = "c1")
private val ADAM = golfer(2, "Adam", "Scott", pgaPlayerId = "a1")
private val BRYSON = golfer(3, "Bryson", "DeChambeau")

/**
 * With its step skipped, the listing links Tom Kim and Adam Scott to no row: Tom Kim has no row but his override's, and
 * by name alone Adam Scott's row reads as a namesake's. An import still credits Adam, through his id's holder.
 */
private fun mixedField(): MixedFieldFixture =
    MixedFieldFixture(
        golfers = listOf(COLLIN, ADAM, BRYSON),
        overrides = mapOf("o1" to COLLIN.id),
        event =
            masters(
                competitor("o1", "Tom Kim", position = 1),
                competitor("a1", "Adam Scott", position = 2),
                competitor("b1", "Bryson DeChambeau", position = 3),
                competitor("n1", "Nick Newman", position = 4),
            ),
    )

class EspnServiceMixedFieldSpec : FunSpec({

    test("an import credits an override, a PGA-id match and a name match, and creates the stranger") {
        val fixture = mixedField()

        fixture.persistImport()

        val created = with(noopTransactionContext) { fixture.golferRepo.findAll(search = "Newman") }.single()
        created.pgaPlayerId shouldBe "n1"
        fixture.golferByPosition() shouldBe mapOf(1 to COLLIN.id, 2 to ADAM.id, 3 to BRYSON.id, 4 to created.id)
    }

    test("an import looks no golfer up per competitor") {
        val fixture = mixedField()

        fixture.persistImport()

        fixture.pointLookups() shouldBe 0
    }

    test("the competitor listing links the same field without a golfer lookup per competitor") {
        val fixture = mixedField()

        val listing = fixture.service.listCompetitorsForLinking(MASTERS.id).orFail()

        listing.competitors.map { Triple(it.espnCompetitorId, it.linkedGolfer?.id, it.hasOverride) } shouldBe
            listOf(
                Triple("o1", COLLIN.id, true),
                Triple("a1", ADAM.id, false),
                Triple("b1", BRYSON.id, false),
                Triple("n1", null, false),
            )
        fixture.pointLookups() shouldBe 0
    }

    test("a tombstoned golfer still answers both its PGA id and an override naming it") {
        val retired = Instant.parse("2026-10-15T00:00:00Z")
        val heldId = golfer(4, "Kevin", "Na", pgaPlayerId = "k1", tombstonedAt = retired)
        val overridden = golfer(5, "Charley", "Hoffman", tombstonedAt = retired)
        val fixture =
            MixedFieldFixture(
                golfers = listOf(heldId, overridden),
                overrides = mapOf("o2" to overridden.id),
                event =
                    masters(
                        competitor("k1", "Kevin Na", position = 1),
                        competitor("o2", "C. Hoffman", position = 2),
                    ),
            )

        fixture.linkedGolfers() shouldBe mapOf("k1" to heldId.id, "o2" to overridden.id)
    }

    test("an override naming no golfer falls through to the PGA-id match") {
        val fixture =
            MixedFieldFixture(
                golfers = listOf(ADAM),
                overrides = mapOf("a1" to GolferId(UUID.randomUUID())),
                event = masters(competitor("a1", "Adam Scott", position = 1)),
            )

        fixture.linkedGolfers() shouldBe mapOf("a1" to ADAM.id)
    }
})
