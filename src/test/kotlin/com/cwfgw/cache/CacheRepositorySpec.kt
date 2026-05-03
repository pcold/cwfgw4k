package com.cwfgw.cache

import com.cwfgw.db.Transactor
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

class CacheRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = CacheRepository()
    val tx = Transactor(postgres.dsl)

    test("get returns null when no entry exists") {
        tx.read { repository.get("missing") }.shouldBeNull()
    }

    test("put then get round-trips the value") {
        // Use a JSON shape Postgres won't reformat: empty array, primitives,
        // and minimal whitespace. JSONB normalizes object-member spacing on
        // read — see the doc note in CacheRepository for production impact.
        val payload = "[]"
        tx.update {
            repository.put(
                key = "/api/v1/leagues",
                value = payload,
                expiresAt = Instant.now().plusSeconds(60),
            )
        }

        tx.read { repository.get("/api/v1/leagues") } shouldBe payload
    }

    test("put overwrites the previous value and expiry on conflict") {
        val key = "/api/v1/scoring/abc/def"
        tx.update {
            repository.put(key = key, value = "\"stale\"", expiresAt = Instant.now().plusSeconds(60))
            repository.put(key = key, value = "\"fresh\"", expiresAt = Instant.now().plusSeconds(60))
        }

        tx.read { repository.get(key) } shouldBe "\"fresh\""
    }

    test("get does not return entries whose expires_at has already passed") {
        tx.update {
            repository.put(
                key = "/api/v1/expired",
                value = "\"stale\"",
                expiresAt = Instant.now().minusSeconds(1),
            )
        }

        tx.read { repository.get("/api/v1/expired") }.shouldBeNull()
    }

    test("deleteExpired removes only the rows whose expiry has passed") {
        val now = Instant.now()
        tx.update {
            repository.put(key = "/api/v1/fresh", value = "\"ok\"", expiresAt = now.plusSeconds(60))
            repository.put(key = "/api/v1/stale-1", value = "\"old\"", expiresAt = now.minusSeconds(10))
            repository.put(key = "/api/v1/stale-2", value = "\"old\"", expiresAt = now.minusSeconds(1))
        }

        val deleted = tx.update { repository.deleteExpired() }

        deleted shouldBe 2
        tx.read { repository.get("/api/v1/fresh") } shouldBe "\"ok\""
    }
})
