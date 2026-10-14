package com.cwfgw.logging

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.classic.spi.ThrowableProxyUtil
import ch.qos.logback.core.encoder.EncoderBase
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Cloud Run takes a JSON line's severity from its `severity` field, and a plain-text line's from the stream
 * it arrived on. Wired in `logback.xml`. Fields: https://cloud.google.com/run/docs/logging#special-fields
 */
class CloudLoggingJsonEncoder : EncoderBase<ILoggingEvent>() {
    override fun headerBytes(): ByteArray? = null

    override fun footerBytes(): ByteArray? = null

    override fun encode(event: ILoggingEvent): ByteArray {
        val entry =
            CloudLogEntry(
                severity = cloudSeverity(event.level),
                message = event.formattedMessage.orEmpty(),
                logger = event.loggerName,
                time = event.instant.toString(),
                stackTrace = event.throwableProxy?.let(ThrowableProxyUtil::asString),
            )
        return (entryJson.encodeToString(entry) + "\n").toByteArray(Charsets.UTF_8)
    }
}

/**
 * `ops/` metrics read `severity`, `jsonPayload.message` and `jsonPayload.logger`; renaming any of them silently
 * empties a metric, and no test here can see it.
 */
@Serializable
internal data class CloudLogEntry(
    val severity: String,
    val message: String,
    val logger: String,
    val time: String,
    /** Error Reporting reads `stack_trace` before `message`, which keeps the trace out of the text alerts match. */
    @SerialName("stack_trace") val stackTrace: String? = null,
)

/** @see <a href="https://cloud.google.com/logging/docs/reference/v2/rest/v2/LogEntry#logseverity">LogSeverity</a> */
internal fun cloudSeverity(level: Level): String =
    when (level) {
        Level.ERROR -> "ERROR"
        Level.WARN -> "WARNING"
        Level.INFO -> "INFO"
        Level.DEBUG, Level.TRACE -> "DEBUG"
        else -> "DEFAULT"
    }

private val entryJson = Json { explicitNulls = false }
