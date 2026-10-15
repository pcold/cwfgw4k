package com.cwfgw.golfers

import com.cwfgw.testing.FakeTransactor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

private fun golfer(
    id: GolferId = GolferId(UUID.randomUUID()),
    firstName: String,
    lastName: String,
    worldRanking: Int? = null,
): Golfer =
    Golfer(
        id = id,
        pgaPlayerId = null,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = worldRanking,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

class GolferServiceSpec : FunSpec({

    test("list passes search to the repository") {
        val rory = golfer(firstName = "Rory", lastName = "McIlroy", worldRanking = 2)
        val tiger = golfer(firstName = "Tiger", lastName = "Woods", worldRanking = 1)
        val service = GolferService(FakeGolferRepository(initial = listOf(rory, tiger)), FakeTransactor())

        val all = service.list(search = null).map { it.id }
        all shouldContainExactly listOf(tiger.id, rory.id)

        val woodSearch = service.list(search = "wood").map { it.id }
        woodSearch shouldContainExactly listOf(tiger.id)
    }

    test("get returns the golfer when present and null otherwise") {
        val rory = golfer(firstName = "Rory", lastName = "McIlroy")
        val service = GolferService(FakeGolferRepository(initial = listOf(rory)), FakeTransactor())

        service.get(rory.id) shouldBe rory
        service.get(GolferId(UUID.randomUUID())).shouldBeNull()
    }

    test("listLinkable leaves out a tombstone that list keeps for name matching") {
        val rory = golfer(firstName = "Rory", lastName = "McIlroy")
        val tombstone = golfer(firstName = "Rory", lastName = "Mcilroy").copy(tombstonedAt = Instant.EPOCH)
        val service = GolferService(FakeGolferRepository(initial = listOf(rory, tombstone)), FakeTransactor())

        service.listLinkable(search = "rory").map { it.id } shouldContainExactly listOf(rory.id)
        service.list(search = "rory").map { it.id } shouldContainExactlyInAnyOrder listOf(rory.id, tombstone.id)
    }
})
