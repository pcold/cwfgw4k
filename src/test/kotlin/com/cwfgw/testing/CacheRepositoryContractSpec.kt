package com.cwfgw.testing

import com.cwfgw.cache.CacheRepository
import com.cwfgw.cache.FakeCacheRepository
import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import java.time.Instant

// Values Postgres's JSONB hands back byte for byte: it respaces an object's members, which the fake does not.
private const val FRESH = "\"fresh\""
private const val STALE = "\"stale\""

context(ctx: WriteTransactionContext)
private fun CacheRepository.putCurrent(
    key: String,
    value: String,
    expiresAt: Instant,
): Boolean = put(key = key, value = value, expiresAt = expiresAt, generation = generation())

class CacheRepositoryContractSpec : FunSpec({
    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    runContract("Postgres", cacheContract) { ContractSubject(tx, CacheRepository(), PostgresSeeds(tx)) }
    runContract("fake", cacheContract) { ContractSubject(FakeTransactor(), FakeCacheRepository(), FakeSeeds) }
})

private val cacheContract: List<ContractCase<CacheRepository>> =
    listOf(
        ContractCase("a put reads back until it expires, and an unknown or expired key reads null") {
            val now = Instant.now()
            tx.update {
                repository.putCurrent("rev-a /api/v1/leagues", "[]", now.plusSeconds(60))
                repository.putCurrent("rev-a /api/v1/expired", STALE, now.minusSeconds(1))
            }

            tx.get { repository.get("rev-a /api/v1/leagues") } shouldBe "[]"
            tx.get { repository.get("rev-a /api/v1/expired") }.shouldBeNull()
            tx.get { repository.get("rev-a /api/v1/missing") }.shouldBeNull()
        },
        ContractCase("a second put on a key replaces its value and expiry") {
            val key = "rev-a /api/v1/scoring"
            tx.update { repository.putCurrent(key, STALE, Instant.now().minusSeconds(1)) }

            tx.update { repository.putCurrent(key, FRESH, Instant.now().plusSeconds(60)) }

            tx.get { repository.get(key) } shouldBe FRESH
        },
        ContractCase("deleteExpired removes only the rows whose expiry has passed, and answers their count") {
            val now = Instant.now()
            tx.update {
                repository.putCurrent("rev-a /api/v1/fresh", FRESH, now.plusSeconds(60))
                repository.putCurrent("rev-a /api/v1/stale-1", STALE, now.minusSeconds(10))
                repository.putCurrent("rev-a /api/v1/stale-2", STALE, now.minusSeconds(1))
            }

            tx.update { repository.deleteExpired() } shouldBe 2
            tx.update { repository.deleteExpired() } shouldBe 0
            tx.get { repository.get("rev-a /api/v1/fresh") } shouldBe FRESH
        },
        ContractCase("clear removes live and expired rows of every revision, and answers their count") {
            val now = Instant.now()
            tx.update {
                repository.putCurrent("rev-a /api/v1/live", FRESH, now.plusSeconds(60))
                repository.putCurrent("rev-b /api/v1/live", FRESH, now.plusSeconds(60))
                repository.putCurrent("rev-a /api/v1/stale", STALE, now.minusSeconds(1))
            }

            tx.update { repository.clear() } shouldBe 3
            tx.get { repository.get("rev-a /api/v1/live") }.shouldBeNull()
            tx.get { repository.get("rev-b /api/v1/live") }.shouldBeNull()
        },
        ContractCase("a put under a generation a clear has since advanced stores nothing; the new generation stores") {
            val key = "rev-a /api/v1/seasons/abc/report"
            val observed = tx.get { repository.generation() }
            tx.update { repository.clear() }
            val expiresAt = Instant.now().plusSeconds(60)

            tx.update { repository.put(key, STALE, expiresAt, observed) }.shouldBeFalse()
            tx.get { repository.get(key) }.shouldBeNull()
            tx.update { repository.put(key, FRESH, expiresAt, repository.generation()) }.shouldBeTrue()
            tx.get { repository.get(key) } shouldBe FRESH
        },
    )
