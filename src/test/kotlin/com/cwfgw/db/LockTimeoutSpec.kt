package com.cwfgw.db

import ch.qos.logback.classic.Level
import com.cwfgw.http.installErrorHandling
import com.cwfgw.jooq.tables.references.LEAGUES
import com.cwfgw.testing.CapturedLogs
import com.cwfgw.testing.PostgresHarness
import com.cwfgw.testing.captureLogs
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.Json
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

private const val HELD_LEAGUE = "Castlewood"
private const val TOUCH_PATH = "/touch"

/** Long enough that a waiter left unbounded outlasts it and succeeds, so the spec fails rather than hangs. */
private const val HOLD_CEILING_MS: Long = LOCK_TIMEOUT_MS * 2

class LockTimeoutSpec : FunSpec({

    val postgres = postgresHarness()
    val transactor = Transactor(postgres.dsl, postgres.maxPoolSize)

    beforeEach {
        postgres.dsl.insertInto(LEAGUES).set(LEAGUES.NAME, HELD_LEAGUE).execute()
    }

    test("a write parked behind a held row answers 409, logging one lock_timeout WARN line and nothing at ERROR") {
        captureLogs { logs ->
            touchTestApp(transactor) { client ->
                val response = postgres.whileRowIsHeld { client.post(TOUCH_PATH) }

                response.status shouldBe HttpStatusCode.Conflict
                response.bodyAsText() shouldBe """{"error":"another change is being saved here; try again"}"""
            }

            logs.lockTimeouts().map { it.level } shouldBe listOf(Level.WARN)
            logs.lockTimeouts().map { it.formattedMessage } shouldBe
                listOf("cwfgw4k.db.lock_timeout method=POST route=$TOUCH_PATH")
            logs.events().filter { it.level == Level.ERROR }.shouldBeEmpty()
        }
    }

    test("every write transaction bounds its lock waits") {
        transactor.update { lockTimeout() } shouldBe "${TimeUnit.MILLISECONDS.toSeconds(LOCK_TIMEOUT_MS)}s"
    }

    test("reads leave lock_timeout unset, since they take no row lock") {
        transactor.get { lockTimeout() } shouldBe "0"
        transactor.read { lockTimeout() } shouldBe "0"
    }
})

private fun touchTestApp(
    transactor: Transactor,
    block: suspend (HttpClient) -> Unit,
) = testApplication {
    application {
        install(ContentNegotiation) { json(Json) }
        installErrorHandling()
        routing {
            post(TOUCH_PATH) {
                transactor.update { touchHeldLeague() }
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
    block(client)
}

/**
 * The holder writes the row on a bare connection of its own, outside the [Transactor] under test, and keeps
 * its transaction open until [block] returns or [HOLD_CEILING_MS] passes.
 */
private suspend fun <A> PostgresHarness.whileRowIsHeld(block: suspend () -> A): A =
    coroutineScope {
        val held = CompletableDeferred<Unit>()
        val release = CountDownLatch(1)
        async(Dispatchers.IO) {
            dsl.transaction { config ->
                with(TransactionContext(config.dsl())) { touchHeldLeague() }
                held.complete(Unit)
                release.await(HOLD_CEILING_MS, TimeUnit.MILLISECONDS)
            }
        }
        held.await()
        try {
            block()
        } finally {
            release.countDown()
        }
    }

private fun CapturedLogs.lockTimeouts() = events().filter { it.formattedMessage.startsWith("cwfgw4k.db.lock_timeout ") }

context(ctx: TransactionContext)
private fun touchHeldLeague() {
    ctx.dsl.update(LEAGUES).set(LEAGUES.NAME, LEAGUES.NAME).where(LEAGUES.NAME.eq(HELD_LEAGUE)).execute()
}

context(ctx: TransactionContext)
private fun lockTimeout(): String? = ctx.dsl.fetchValue("SHOW lock_timeout")?.toString()
