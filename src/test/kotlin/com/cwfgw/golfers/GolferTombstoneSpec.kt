package com.cwfgw.golfers

import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnCompetitor
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.EspnStatus
import com.cwfgw.espn.EspnTournament
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTombstone
import com.cwfgw.testing.seedTombstonedGolfer
import com.cwfgw.testing.seedTournament
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.jooq.exception.DataAccessException

private const val EVENT_ID = "evt-tombstone"

/** Through a real import on Postgres: the backfill a tombstone must escape is the UPDATE the import issues. */
class GolferTombstoneSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val golfers = GolferRepository()
    val tournaments = TournamentRepository()

    fun service(): EspnService =
        testEspnService(
            client = FakeEspnClient(),
            seasonRepository = SeasonRepository(),
            golferRepository = golfers,
            teamRepository = TeamRepository(),
            tournamentRepository = tournaments,
            linkRepository = TournamentLinkRepository(),
            tx = tx,
        )

    test("an import whose only name match is a tombstone credits a new row and leaves the tombstone id-less") {
        val tournament = tx.update { checkNotNull(tournaments.findById(seedTournament(EVENT_ID))) }
        val duplicate = tx.update { seedTombstonedGolfer("Thomas", "Detry") }

        tx.update { service().persistImportIn(tournament, masters(competitor("4837", "Thomas Detry"))) }

        val credited = tx.read { tournaments.getResults(tournament.id) }.single().golferId
        credited shouldNotBe duplicate.id
        tx.get { golfers.findById(credited) }?.pgaPlayerId shouldBe "4837"
        tx.get { golfers.findById(duplicate.id) }?.pgaPlayerId shouldBe null
    }

    test("a partner credits the live surname beside a tombstoned surname-only phantom, unrostered") {
        val tournament = tx.update { checkNotNull(tournaments.findById(seedTournament(EVENT_ID))) }
        tx.update { seedTombstonedGolfer("", "Griffin") }
        val ben = tx.update { golfers.create(CreateGolferRequest(firstName = "Ben", lastName = "Griffin")) }

        tx.update { service().persistImportIn(tournament, masters(partner("team:t1:0", "Griffin"))) }

        tx.read { tournaments.getResults(tournament.id) }.map { it.golferId } shouldContainExactly listOf(ben.id)
    }

    test("a golfer tombstoned after its result still answers findById and findAll, so the result keeps its name") {
        val tournament = tx.update { checkNotNull(tournaments.findById(seedTournament(EVENT_ID))) }
        tx.update { service().persistImportIn(tournament, masters(competitor("4837", "Thomas Detry"))) }
        val credited = tx.read { tournaments.getResults(tournament.id) }.single().golferId

        tx.update { seedTombstone(credited) }

        val golfer = tx.get { golfers.findById(credited) }.shouldNotBeNull()
        "${golfer.firstName} ${golfer.lastName}" shouldBe "Thomas Detry"
        golfer.tombstonedAt.shouldNotBeNull()
        tx.get { golfers.findAll(search = null) } shouldContain golfer
    }

    test("the schema refuses a merged_into on a live row, and a row merged into itself") {
        val survivor = tx.update { golfers.create(CreateGolferRequest(firstName = "Thomas", lastName = "Detry")) }
        val duplicate = tx.update { golfers.create(CreateGolferRequest(firstName = "Thomas", lastName = "Detry")) }

        val live =
            shouldThrow<DataAccessException> {
                tx.update { seedTombstone(duplicate.id, mergedInto = survivor.id, tombstonedAt = null) }
            }
        live.message.shouldNotBeNull() shouldContain "golfers_merged_into_is_tombstoned"
        val itself =
            shouldThrow<DataAccessException> { tx.update { seedTombstone(duplicate.id, mergedInto = duplicate.id) } }
        itself.message.shouldNotBeNull() shouldContain "golfers_merged_into_other_row"
    }

    test("the schema admits a tombstone merged into another row") {
        val survivor = tx.update { golfers.create(CreateGolferRequest(firstName = "Thomas", lastName = "Detry")) }
        val duplicate = tx.update { golfers.create(CreateGolferRequest(firstName = "Thomas", lastName = "Detry")) }

        tx.update { seedTombstone(duplicate.id, mergedInto = survivor.id) }

        tx.get { golfers.findById(duplicate.id) }?.tombstonedAt.shouldNotBeNull()
    }
})

private fun competitor(
    espnId: String,
    name: String,
): EspnCompetitor =
    EspnCompetitor(
        espnId = espnId,
        name = name,
        order = 1,
        scoreStr = "-10",
        scoreToPar = -10,
        totalStrokes = 278,
        roundScores = emptyList(),
        position = 1,
        status = EspnStatus.Active,
        isTeamPartner = false,
        pairKey = null,
    )

private fun partner(
    espnId: String,
    name: String,
): EspnCompetitor = competitor(espnId, name).copy(isTeamPartner = true, pairKey = "team:t1")

private fun masters(vararg competitors: EspnCompetitor): EspnTournament =
    EspnTournament(
        espnId = EVENT_ID,
        name = "Masters",
        completed = true,
        competitors = competitors.toList(),
        isTeamEvent = false,
    )
