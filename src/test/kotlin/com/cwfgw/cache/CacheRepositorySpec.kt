package com.cwfgw.cache

import com.cwfgw.db.Transactor
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.jooq.tables.references.CACHE_GENERATION
import com.cwfgw.testing.PostgresHarness
import com.cwfgw.testing.awaitLockWait
import com.cwfgw.testing.postgresHarness
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.jooq.SQLDialect
import org.jooq.exception.NoDataFoundException
import org.jooq.impl.DSL
import java.sql.Connection
import java.time.Instant

private const val REPORT_KEY = "rev-a /api/v1/seasons/abc/report"

/** Committed by hand, so a second transaction can run against it while it is still open. */
private class OpenTransaction(private val connection: Connection) : AutoCloseable {
    val context: WriteTransactionContext

    init {
        connection.autoCommit = false
        val dsl = DSL.using(connection, SQLDialect.POSTGRES)
        // Bounds a lock wait the assertions don't expect, rather than hanging the run.
        dsl.execute("SET LOCAL lock_timeout = '10s'")
        context = WriteTransactionContext(dsl)
    }

    fun commit() = connection.commit()

    override fun close() {
        connection.rollback()
        connection.close()
    }
}

private fun PostgresHarness.openTransaction(): OpenTransaction = OpenTransaction(dataSource.connection)

class CacheRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = CacheRepository()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    test("clear fails rather than reporting success when the generation row is missing") {
        postgres.openTransaction().use { open ->
            open.context.dsl.deleteFrom(CACHE_GENERATION).execute()
            with(open.context) { shouldThrow<NoDataFoundException> { repository.clear() } }
        }
    }

    // The waiting side runs through Transactor, so a change to the isolation it opens at reaches these specs:
    // under REPEATABLE READ the clear's DELETE would miss the put, and the put would fail to serialize.
    test("a clear that starts while a put is uncommitted waits for it, then deletes its row") {
        val observed = tx.get { repository.generation() }
        postgres.openTransaction().use { putting ->
            with(putting.context) {
                repository.put(REPORT_KEY, "\"pre-write\"", Instant.now().plusSeconds(60), observed).shouldBeTrue()
            }
            coroutineScope {
                val cleared = async(Dispatchers.IO) { tx.update { repository.clear() } }
                postgres.awaitLockWait(cleared).shouldBeTrue()
                putting.commit()

                cleared.await() shouldBe 1
            }
        }

        tx.read { repository.get(REPORT_KEY) }.shouldBeNull()
    }

    test("a put that starts while a clear is uncommitted waits for it, then sees the new generation") {
        val observed = tx.get { repository.generation() }
        postgres.openTransaction().use { clearing ->
            with(clearing.context) { repository.clear() }
            coroutineScope {
                val stored =
                    async(Dispatchers.IO) {
                        tx.update {
                            repository.put(REPORT_KEY, "\"pre-write\"", Instant.now().plusSeconds(60), observed)
                        }
                    }
                postgres.awaitLockWait(stored).shouldBeTrue()
                clearing.commit()

                stored.await().shouldBeFalse()
            }
        }

        tx.read { repository.get(REPORT_KEY) }.shouldBeNull()
    }

    test("a sweep that starts while a clear is uncommitted waits for it") {
        tx.update { repository.put(REPORT_KEY, "\"live\"", Instant.now().plusSeconds(60), repository.generation()) }
        postgres.openTransaction().use { clearing ->
            with(clearing.context) { repository.clear() } shouldBe 1
            coroutineScope {
                val swept = async(Dispatchers.IO) { tx.update { repository.deleteExpired() } }
                postgres.awaitLockWait(swept).shouldBeTrue()
                clearing.commit()

                swept.await() shouldBe 0
            }
        }
    }
})
