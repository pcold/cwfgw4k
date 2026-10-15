package com.cwfgw.db

import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.SeasonId
import com.cwfgw.teams.NewTeam
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import org.jooq.exception.DataAccessException
import java.util.UUID

private const val PGA_PLAYER_ID_KEY = "golfers_pga_player_id_key"

private data object Outraced

class OutracedWritesSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val golfers = GolferRepository()
    val scottie = CreateGolferRequest(pgaPlayerId = "espn-1", firstName = "Scottie", lastName = "Scheffler")

    test("a unique violation on the named constraint answers outraced and rolls the whole block back") {
        tx.update { golfers.create(scottie) }

        val result =
            tx.updateOrOutraced(PGA_PLAYER_ID_KEY, outraced = { Outraced }) {
                golfers.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
                Result.Ok(golfers.create(scottie))
            }

        result shouldBe Result.Err(Outraced)
        tx.read { golfers.findAll(search = null) }.map { it.firstName } shouldContainExactly listOf("Scottie")
    }

    test("a unique violation on a different constraint still throws") {
        tx.update { golfers.create(scottie) }

        shouldThrow<DataAccessException> {
            tx.updateOrOutraced("users_username_key", outraced = { Outraced }) { Result.Ok(golfers.create(scottie)) }
        }
    }

    test("a violation that names the constraint but is not a unique violation still throws") {
        val orphan = NewTeam(ownerName = "Nobody", teamName = "ORPHAN", teamNumber = 1)

        shouldThrow<DataAccessException> {
            tx.updateOrOutraced("teams_season_id_fkey", outraced = { Outraced }) {
                Result.Ok(TeamRepository().create(seasonId = SeasonId(UUID.randomUUID()), request = orphan))
            }
        }
    }

    test("a block that commits answers its own result") {
        val result =
            tx.updateOrOutraced(PGA_PLAYER_ID_KEY, outraced = { Outraced }) {
                Result.Ok(golfers.create(scottie))
            }

        result shouldBe Result.Ok(tx.read { golfers.findByPgaPlayerId("espn-1") })
    }

    test("a write outraced inside a transaction undoes that write alone and answers null") {
        tx.update { golfers.create(scottie) }

        val written =
            tx.update {
                golfers.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
                writeOrOutraced(PGA_PLAYER_ID_KEY) {
                    golfers.create(CreateGolferRequest(firstName = "Ludvig", lastName = "Aberg"))
                    golfers.create(scottie)
                }
            }

        written shouldBe null
        tx.read { golfers.findAll(search = null) }.map { it.firstName } shouldContainExactlyInAnyOrder
            listOf("Scottie", "Rory")
    }

    test("a write outraced on a different constraint still throws") {
        tx.update { golfers.create(scottie) }

        shouldThrow<DataAccessException> {
            tx.update { writeOrOutraced("users_username_key") { golfers.create(scottie) } }
        }
    }
})
