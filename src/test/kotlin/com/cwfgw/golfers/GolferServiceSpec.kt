package com.cwfgw.golfers

import com.cwfgw.testing.FakeTransactor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
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

    test("create delegates to the repo") {
        val newId = GolferId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"))
        val newTime = Instant.parse("2026-03-15T12:00:00Z")
        val fake = FakeGolferRepository(idFactory = { newId }, clock = { newTime })
        val service = GolferService(fake, FakeTransactor())

        val created =
            service.create(
                CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler", worldRanking = 1),
            )

        created.id shouldBe newId
        created.updatedAt shouldBe newTime
        service.get(newId) shouldBe created
    }
})
