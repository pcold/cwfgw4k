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
    active: Boolean = true,
): Golfer =
    Golfer(
        id = id,
        pgaPlayerId = null,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = worldRanking,
        active = active,
        updatedAt = Instant.parse("2026-01-01T00:00:00Z"),
    )

class GolferServiceSpec : FunSpec({

    test("list passes activeOnly and search to the repository") {
        val active = golfer(firstName = "Rory", lastName = "McIlroy", worldRanking = 2)
        val inactive = golfer(firstName = "Tiger", lastName = "Woods", worldRanking = 1, active = false)
        val service = GolferService(FakeGolferRepository(initial = listOf(active, inactive)), FakeTransactor())

        val activeOnly = service.list(activeOnly = true, search = null).map { it.id }
        activeOnly shouldContainExactly listOf(active.id)

        val all = service.list(activeOnly = false, search = null).map { it.id }
        all shouldContainExactly listOf(inactive.id, active.id)

        val woodSearch = service.list(activeOnly = false, search = "wood").map { it.id }
        woodSearch shouldContainExactly listOf(inactive.id)
    }

    test("get returns the golfer when present and null otherwise") {
        val rory = golfer(firstName = "Rory", lastName = "McIlroy")
        val service = GolferService(FakeGolferRepository(initial = listOf(rory)), FakeTransactor())

        service.get(rory.id) shouldBe rory
        service.get(GolferId(UUID.randomUUID())).shouldBeNull()
    }

    test("create defaults to active=true and delegates to the repo") {
        val newId = GolferId(UUID.fromString("00000000-0000-0000-0000-0000000000aa"))
        val newTime = Instant.parse("2026-03-15T12:00:00Z")
        val fake = FakeGolferRepository(idFactory = { newId }, clock = { newTime })
        val service = GolferService(fake, FakeTransactor())

        val created =
            service.create(
                CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler", worldRanking = 1),
            )

        created.id shouldBe newId
        created.active shouldBe true
        created.updatedAt shouldBe newTime
        service.get(newId) shouldBe created
    }

    test("update merges only the supplied fields and returns null for unknown id") {
        val rory = golfer(firstName = "Rory", lastName = "McIlroy", worldRanking = 3)
        val fake =
            FakeGolferRepository(
                initial = listOf(rory),
                clock = { Instant.parse("2026-03-15T12:00:00Z") },
            )
        val service = GolferService(fake, FakeTransactor())

        val updated = service.update(rory.id, UpdateGolferRequest(worldRanking = 1))
        updated?.worldRanking shouldBe 1
        updated?.firstName shouldBe "Rory"

        service.update(GolferId(UUID.randomUUID()), UpdateGolferRequest(firstName = "Nobody")).shouldBeNull()
    }
})
