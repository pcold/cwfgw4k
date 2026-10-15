package com.cwfgw.golfers

import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.testing.noopTransactionContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

/** Import and confirm both write through [findOrCreateAll], so this order is each of theirs. */
class PgaPlayerIdClaimSpec : FunSpec({

    test("backfills and creates are written in one pga id order, whatever order and kind they arrive in") {
        val fleetwood = idlessGolfer("Tommy", "Fleetwood")
        val repository = ClaimRecordingGolferRepository(FakeGolferRepository(initial = listOf(fleetwood)))

        with(noopTransactionContext) {
            repository.findOrCreateAll(
                listOf(create("300", "Ludvig Aberg"), backfill(fleetwood, "100"), create("200", "Rory McIlroy")),
            )
        }

        repository.writes shouldContainExactly listOf("backfill 100", "create 200", "create 300")
    }

    test("one id claimed twice is written once, as the backfill, and answers that row") {
        val fleetwood = idlessGolfer("Tommy", "Fleetwood")
        val repository = ClaimRecordingGolferRepository(FakeGolferRepository(initial = listOf(fleetwood)))

        val written =
            with(noopTransactionContext) {
                repository.findOrCreateAll(listOf(create("100", "T. Fleetwood"), backfill(fleetwood, "100")))
            }

        repository.writes shouldContainExactly listOf("backfill 100")
        written.getValue("100").id shouldBe fleetwood.id
    }

    test("a lost backfill falls back to findOrCreate and answers the row holding the id") {
        val holder = idlessGolfer("Tommy", "Fleetwood").copy(pgaPlayerId = "100")
        val namesake = idlessGolfer("Tommy", "Fleetwood")
        val repository = ClaimRecordingGolferRepository(FakeGolferRepository(initial = listOf(holder, namesake)))

        val written = with(noopTransactionContext) { repository.findOrCreateAll(listOf(backfill(namesake, "100"))) }

        repository.writes shouldContainExactly listOf("backfill 100", "create 100")
        written.getValue("100") shouldBe holder
    }
})

private class ClaimRecordingGolferRepository(
    private val delegate: GolferRepository,
) : GolferRepository by delegate {
    val writes = mutableListOf<String>()

    context(ctx: WriteTransactionContext)
    override fun findOrCreate(request: CreateGolferRequest): Golfer {
        writes += "create ${request.pgaPlayerId}"
        return delegate.findOrCreate(request)
    }

    context(ctx: WriteTransactionContext)
    override fun backfillPgaPlayerId(
        id: GolferId,
        pgaPlayerId: String,
    ): Golfer? {
        writes += "backfill $pgaPlayerId"
        return delegate.backfillPgaPlayerId(id, pgaPlayerId)
    }
}

private fun name(fullName: String): GolferName = checkNotNull(GolferName.splitName(fullName))

private fun create(
    pgaPlayerId: String,
    fullName: String,
): PgaPlayerIdClaim = PgaPlayerIdClaim.Create(pgaPlayerId, name(fullName))

private fun backfill(
    row: Golfer,
    pgaPlayerId: String,
): PgaPlayerIdClaim = PgaPlayerIdClaim.Backfill(row.id, pgaPlayerId, name("${row.firstName} ${row.lastName}"))

private fun idlessGolfer(
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
