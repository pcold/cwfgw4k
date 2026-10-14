package com.cwfgw.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.LoggerContext
import ch.qos.logback.classic.joran.JoranConfigurator
import ch.qos.logback.classic.spi.LoggingEvent
import ch.qos.logback.classic.util.LogbackMDCAdapter
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldStartWith
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.ByteArrayOutputStream
import java.io.PrintStream

private const val SAMPLE_LOGGER = "com.cwfgw.logging.sample"

private fun encodedLine(
    level: Level,
    message: String,
    cause: Throwable? = null,
): String {
    val logger = LoggerContext().getLogger(SAMPLE_LOGGER)
    val event = LoggingEvent(SAMPLE_LOGGER, logger, level, message, cause, null)
    return CloudLoggingJsonEncoder().encode(event).toString(Charsets.UTF_8)
}

private fun String.parsed(): JsonObject = Json.parseToJsonElement(this).jsonObject

private fun JsonObject.text(field: String): String? = this[field]?.jsonPrimitive?.content

private class Streams(val stdout: String, val stderr: String)

/** Through `logback.xml` itself in a context of its own, so the test JVM's logging is left as it was. */
private fun throughProductionConfig(block: (LoggerContext) -> Unit): Streams {
    val stdout = ByteArrayOutputStream()
    val stderr = ByteArrayOutputStream()
    val originalOut = System.out
    val originalErr = System.err
    // A context built by hand gets no MDC adapter, and the appender then fails every append.
    val context = LoggerContext().apply { setMDCAdapter(LogbackMDCAdapter()) }
    System.setOut(PrintStream(stdout, true, Charsets.UTF_8))
    System.setErr(PrintStream(stderr, true, Charsets.UTF_8))
    try {
        JoranConfigurator().apply { setContext(context) }.doConfigure(
            CloudLoggingJsonEncoderSpec::class.java.getResource("/logback.xml"),
        )
        block(context)
    } finally {
        context.stop()
        System.setOut(originalOut)
        System.setErr(originalErr)
    }
    return Streams(stdout.toString(Charsets.UTF_8), stderr.toString(Charsets.UTF_8))
}

private fun String.linesFrom(logger: String): List<String> = lines().filter { it.contains("\"logger\":\"$logger\"") }

class CloudLoggingJsonEncoderSpec : FunSpec({

    test("each logback level arrives under its Cloud Logging severity name") {
        val severities =
            listOf(Level.TRACE, Level.DEBUG, Level.INFO, Level.WARN, Level.ERROR)
                .map { level -> encodedLine(level, "x").parsed().text("severity") }

        severities shouldBe listOf("DEBUG", "DEBUG", "INFO", "WARNING", "ERROR")
    }

    test("the line's text lands in message and the logger name in logger") {
        val entry = encodedLine(Level.INFO, "cwfgw4k.request method=GET").parsed()

        entry.text("message") shouldBe "cwfgw4k.request method=GET"
        entry.text("logger") shouldBe SAMPLE_LOGGER
    }

    test("an exception's stack trace lands in stack_trace and leaves message as the line's text") {
        val entry = encodedLine(Level.ERROR, "Unhandled error on GET /x", IllegalStateException("boom")).parsed()

        entry.text("message") shouldBe "Unhandled error on GET /x"
        entry.text("stack_trace") shouldStartWith "java.lang.IllegalStateException: boom\n\tat "
    }

    test("a line without an exception carries no stack_trace field") {
        encodedLine(Level.WARN, "plain").parsed().containsKey("stack_trace") shouldBe false
    }

    test("quotes, newlines and control characters stay inside one line of valid JSON") {
        val awkward = "said \"hi\"\nthen \\ left\t\u0001"
        val line = encodedLine(Level.INFO, awkward)

        line shouldEndWith "}\n"
        line.trimEnd('\n').lines() shouldHaveSize 1
        line.parsed().text("message") shouldBe awkward
    }

    test("the production config writes WARN and ERROR to stdout as JSON, and nothing to stderr") {
        val streams =
            throughProductionConfig { context ->
                val logger = context.getLogger(SAMPLE_LOGGER)
                logger.warn("a client mistake")
                logger.error("a real fault", IllegalStateException("boom"))
            }

        val emitted = streams.stdout.linesFrom(SAMPLE_LOGGER).map { it.parsed() }
        emitted.map { it.text("severity") to it.text("message") } shouldBe
            listOf("WARNING" to "a client mistake", "ERROR" to "a real fault")
        emitted.last().text("stack_trace") shouldStartWith "java.lang.IllegalStateException: boom"
        streams.stderr.linesFrom(SAMPLE_LOGGER) shouldBe emptyList()
    }
})
