package com.cwfgw.testing

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.Appender
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.shouldBe
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger as LogbackLogger
import org.slf4j.Logger as Slf4jLogger

/** Outside `com.cwfgw`, so a capture aimed at the application's own loggers never hears it. */
private const val FOREIGN_LOGGER = "org.example.elsewhere"

private fun rootAppenders(): List<Appender<ILoggingEvent>> {
    val root =
        LoggerFactory.getLogger(Slf4jLogger.ROOT_LOGGER_NAME) as? LogbackLogger
            ?: error("logback-classic is not the active SLF4J binding")
    return root.iteratorForAppenders().asSequence().toList()
}

class CapturedLogsSpec : FunSpec({

    test("captures a line from a logger outside the application's packages, with its arguments filled in") {
        captureLogs { logs ->
            LoggerFactory.getLogger(FOREIGN_LOGGER).info("pool {} is ready", "primary")

            logs.messages() shouldContain "pool primary is ready"
        }
    }

    test("events carry the level that a line's text does not") {
        captureLogs { logs ->
            val foreign = LoggerFactory.getLogger(FOREIGN_LOGGER)
            foreign.warn("pool is low")
            foreign.error("pool is empty")

            val heard = logs.events().filter { it.loggerName == FOREIGN_LOGGER }
            heard.map { it.level to it.formattedMessage } shouldBe
                listOf(Level.WARN to "pool is low", Level.ERROR to "pool is empty")
        }
    }

    test("adds one appender to the root logger for the block and leaves the root as it found it") {
        val before = rootAppenders()

        captureLogs { rootAppenders() shouldHaveSize before.size + 1 }

        rootAppenders() shouldBe before
    }

    test("leaves the root logger as it found it when the block throws") {
        val before = rootAppenders()

        shouldThrow<IllegalStateException> { captureLogs { error("the block failed") } }

        rootAppenders() shouldBe before
    }

    test("a line logged after the block is not captured") {
        val foreign = LoggerFactory.getLogger(FOREIGN_LOGGER)
        val escaped = mutableListOf<CapturedLogs>()

        captureLogs { logs ->
            foreign.info("inside the block")
            escaped += logs
        }
        foreign.info("after the block")

        val heard = escaped.flatMap(CapturedLogs::messages)
        heard shouldContain "inside the block"
        heard shouldNotContain "after the block"
    }
})
