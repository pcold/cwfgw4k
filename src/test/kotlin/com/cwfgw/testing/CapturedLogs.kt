package com.cwfgw.testing

import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.slf4j.LoggerFactory
import ch.qos.logback.classic.Logger as LogbackLogger
import org.slf4j.Logger as Slf4jLogger

internal class CapturedLogs(private val appender: ListAppender<ILoggingEvent>) {
    fun messages(): List<String> = events().map { it.formattedMessage }

    /**
     * Severity is not in a line's text, and `logback.xml` splits on it: WARN and above go to stderr, which
     * Cloud Logging reads as ERROR. Copied under the appender's own lock, the one `doAppend` holds, because
     * other threads keep appending while the block reads.
     */
    fun events(): List<ILoggingEvent> = synchronized(appender) { appender.list.toList() }
}

/**
 * Attached to the root logger, never to one named logger: the usual assertion is that a line was not emitted,
 * and a per-logger capture passes by watching the wrong logger. The root hears every thread in the test JVM,
 * so assert on a level or on a marker, never on total silence.
 */
internal fun captureLogs(block: (CapturedLogs) -> Unit) {
    val root =
        LoggerFactory.getLogger(Slf4jLogger.ROOT_LOGGER_NAME) as? LogbackLogger
            ?: error("logback-classic is not the active SLF4J binding")
    val appender = ListAppender<ILoggingEvent>()
    appender.start()
    root.addAppender(appender)
    try {
        block(CapturedLogs(appender))
    } finally {
        root.detachAppender(appender)
        appender.stop()
    }
}
