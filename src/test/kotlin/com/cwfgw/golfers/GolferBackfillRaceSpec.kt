package com.cwfgw.golfers

import com.cwfgw.db.Transactor
import com.cwfgw.testing.Pause
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** The insert is held uncommitted, so the backfill's own NOT EXISTS reads past it and only the unique index sees it. */
class GolferBackfillRaceSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val repository = GolferRepository()
    val fleetwood = CreateGolferRequest(pgaPlayerId = "espn-100", firstName = "Tommy", lastName = "Fleetwood")

    test("a backfill that meets an uncommitted insert of its id falls back to the inserted row") {
        val idless = tx.update { repository.create(fleetwood.copy(pgaPlayerId = null)) }
        val pause = Pause()

        val (holder, written, waited) =
            coroutineScope {
                val inserting = async { tx.update { repository.findOrCreate(fleetwood).also { pause.hold() } } }
                val (backfilling, waited) =
                    pause.contend(postgres) {
                        async {
                            val claim = PgaPlayerIdClaim.Backfill(idless.id, "espn-100", TOMMY_FLEETWOOD)
                            tx.update { repository.findOrCreateAll(listOf(claim)) }
                        }
                    }
                Triple(inserting.await(), backfilling.await(), waited)
            }

        written.getValue("espn-100") shouldBe holder
        tx.read { repository.findById(idless.id) }?.pgaPlayerId shouldBe null
        waited.shouldBeTrue()
    }
})

private val TOMMY_FLEETWOOD: GolferName = checkNotNull(GolferName.splitName("Tommy Fleetwood"))
