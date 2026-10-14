package com.cwfgw.cache

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.Transactor
import com.cwfgw.testing.PostgresHarness
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import java.sql.Connection
import java.time.Duration
import java.time.Instant

private const val REPORT_KEY = "rev-a /api/v1/seasons/abc/report"
private val LOCK_WAIT_DEADLINE: Duration = Duration.ofSeconds(10)

/** Committed by hand, so a second transaction can run against it while it is still open. */
private class OpenTransaction(private val connection: Connection) : AutoCloseable {
    val context: TransactionContext
    val pid: Int

    init {
        connection.autoCommit = false
        val dsl = DSL.using(connection, SQLDialect.POSTGRES)
        // Bounds a lock wait the assertions don't expect, rather than hanging the run.
        dsl.execute("SET LOCAL lock_timeout = '10s'")
        context = TransactionContext(dsl)
        pid = dsl.fetchSingle("select pg_backend_pid()").get(0, Int::class.java)
    }

    fun commit() = connection.commit()

    override fun close() = connection.close()
}

private fun PostgresHarness.openTransaction(): OpenTransaction = OpenTransaction(dataSource.connection)

/** Returns once [pid] waits on a lock, or once [pending] finishes without ever waiting. */
private suspend fun PostgresHarness.awaitLockWait(
    pid: Int,
    pending: Job,
) {
    val deadline = Instant.now().plus(LOCK_WAIT_DEADLINE)
    while (!pending.isCompleted && Instant.now().isBefore(deadline)) {
        val waiting =
            dsl.fetchExists(
                DSL.selectOne().from("pg_stat_activity").where("pid = ? and wait_event_type = 'Lock'", pid),
            )
        if (waiting) return
        delay(10)
    }
}

context(ctx: TransactionContext)
private fun CacheRepository.putCurrent(
    key: String,
    value: String,
    expiresAt: Instant,
): Boolean = put(key = key, value = value, expiresAt = expiresAt, generation = generation())

class CacheRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = CacheRepository()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("get returns null when no entry exists") {
        tx.read { repository.get("missing") }.shouldBeNull()
    }

    test("put then get round-trips the value") {
        // Use a JSON shape Postgres won't reformat: empty array, primitives,
        // and minimal whitespace. JSONB normalizes object-member spacing on
        // read — see the doc note in CacheRepository for production impact.
        val payload = "[]"
        tx.update {
            repository.putCurrent(
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
            repository.putCurrent(key = key, value = "\"stale\"", expiresAt = Instant.now().plusSeconds(60))
            repository.putCurrent(key = key, value = "\"fresh\"", expiresAt = Instant.now().plusSeconds(60))
        }

        tx.read { repository.get(key) } shouldBe "\"fresh\""
    }

    test("get does not return entries whose expires_at has already passed") {
        tx.update {
            repository.putCurrent(
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
            repository.putCurrent(key = "/api/v1/fresh", value = "\"ok\"", expiresAt = now.plusSeconds(60))
            repository.putCurrent(key = "/api/v1/stale-1", value = "\"old\"", expiresAt = now.minusSeconds(10))
            repository.putCurrent(key = "/api/v1/stale-2", value = "\"old\"", expiresAt = now.minusSeconds(1))
        }

        val deleted = tx.update { repository.deleteExpired() }

        deleted shouldBe 2
        tx.read { repository.get("/api/v1/fresh") } shouldBe "\"ok\""
    }

    test("clear removes live and expired rows of every revision") {
        val now = Instant.now()
        tx.update {
            repository.putCurrent(key = "rev-a /api/v1/live", value = "\"ok\"", expiresAt = now.plusSeconds(60))
            repository.putCurrent(key = "rev-b /api/v1/live", value = "\"ok\"", expiresAt = now.plusSeconds(60))
            repository.putCurrent(key = "rev-a /api/v1/stale", value = "\"old\"", expiresAt = now.minusSeconds(1))
        }

        tx.update { repository.clear() } shouldBe 3
        tx.read { repository.get("rev-a /api/v1/live") }.shouldBeNull()
        tx.read { repository.get("rev-b /api/v1/live") }.shouldBeNull()
    }

    test("a put under a generation that a clear has since advanced stores nothing") {
        val observed = tx.get { repository.generation() }
        tx.update { repository.clear() }

        val stored =
            tx.update {
                repository.put(REPORT_KEY, "\"pre-write\"", Instant.now().plusSeconds(60), observed)
            }

        stored.shouldBeFalse()
        tx.read { repository.get(REPORT_KEY) }.shouldBeNull()
    }

    test("a clear that starts while a put is uncommitted waits for it, then deletes its row") {
        val observed = tx.get { repository.generation() }
        postgres.openTransaction().use { putting ->
            postgres.openTransaction().use { clearing ->
                with(putting.context) {
                    repository.put(REPORT_KEY, "\"pre-write\"", Instant.now().plusSeconds(60), observed).shouldBeTrue()
                }
                coroutineScope {
                    val cleared = async(Dispatchers.IO) { with(clearing.context) { repository.clear() } }
                    postgres.awaitLockWait(clearing.pid, cleared)
                    putting.commit()

                    cleared.await() shouldBe 1
                    clearing.commit()
                }
            }
        }

        tx.read { repository.get(REPORT_KEY) }.shouldBeNull()
    }

    test("a put that starts while a clear is uncommitted waits for it, then sees the new generation") {
        val observed = tx.get { repository.generation() }
        postgres.openTransaction().use { clearing ->
            postgres.openTransaction().use { putting ->
                with(clearing.context) { repository.clear() }
                coroutineScope {
                    val stored =
                        async(Dispatchers.IO) {
                            with(putting.context) {
                                repository.put(REPORT_KEY, "\"pre-write\"", Instant.now().plusSeconds(60), observed)
                            }
                        }
                    postgres.awaitLockWait(putting.pid, stored)
                    clearing.commit()

                    stored.await().shouldBeFalse()
                    putting.commit()
                }
            }
        }

        tx.read { repository.get(REPORT_KEY) }.shouldBeNull()
    }
})
