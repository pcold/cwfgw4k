package com.cwfgw.leagues

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant
import java.util.UUID

private val ALPHA =
    League(
        id = LeagueId(UUID.fromString("00000000-0000-0000-0000-00000000000a")),
        name = "Alpha League",
        createdAt = Instant.parse("2026-01-01T00:00:00Z"),
    )
private val BETA =
    League(
        id = LeagueId(UUID.fromString("00000000-0000-0000-0000-00000000000b")),
        name = "Beta League",
        createdAt = Instant.parse("2026-01-02T00:00:00Z"),
    )

class LeagueServiceSpec : FunSpec({

    test("list returns leagues from the repository sorted by name") {
        val service = LeagueService(FakeLeagueRepository(initial = listOf(BETA, ALPHA)))

        service.list() shouldContainExactly listOf(ALPHA, BETA)
    }

    test("get returns the league when present") {
        val service = LeagueService(FakeLeagueRepository(initial = listOf(ALPHA)))

        service.get(ALPHA.id) shouldBe ALPHA
    }

    test("get returns null when the id is unknown") {
        val service = LeagueService(FakeLeagueRepository(initial = listOf(ALPHA)))

        service.get(LeagueId(UUID.randomUUID())).shouldBeNull()
    }

    test("create delegates to the repository and returns the inserted league") {
        val newId = LeagueId(UUID.fromString("00000000-0000-0000-0000-00000000000c"))
        val newCreatedAt = Instant.parse("2026-03-15T00:00:00Z")
        val fake = FakeLeagueRepository(idFactory = { newId }, clock = { newCreatedAt })
        val service = LeagueService(fake)

        val created = service.create(CreateLeagueRequest(name = "Gamma League"))

        created.id shouldBe newId
        created.name shouldBe "Gamma League"
        created.createdAt shouldBe newCreatedAt
        service.get(newId) shouldBe created
    }
})
