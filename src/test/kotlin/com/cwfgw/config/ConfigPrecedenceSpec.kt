package com.cwfgw.config

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.util.concurrent.TimeUnit

private const val SCHEMA_OVERRIDE = "schema-from-override"
private const val SCHEMA_EXPORTED = "schema-from-environment"
private const val HOST_EXPORTED = "host-from-environment"
private const val HOST_IN_YAML = "0.0.0.0"
private const val USER_EXPORTED = "user-from-environment"
private const val PROBE_TIMEOUT_SECONDS = 60L

/**
 * A JVM cannot change its own environment, so [ConfigPrecedenceSpec] runs the loader in a child given one.
 * Writes to the file named by its argument, not stdout, because Hoplite prints its own notices there.
 */
object ConfigPrecedenceProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val results = requireNotNull(args.singleOrNull()) { "usage: ConfigPrecedenceProbe <results file>" }
        val config = AppConfig.load(mapOf("db.schema" to SCHEMA_OVERRIDE, "auth.sessionSecret" to "probe-secret"))
        File(results).writeText(listOf(config.db.schema, config.http.host, config.db.user).joinToString("\n"))
    }
}

/** A blocking waitFor ignores coroutine cancellation, so only the timeout stops a stalled child. */
private suspend fun loadInChildJvm(
    exported: Map<String, String>,
    results: File,
    console: File,
): List<String> =
    withContext(Dispatchers.IO) {
        val launcher = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val classpath = System.getProperty("java.class.path")
        val process =
            ProcessBuilder(launcher, "-cp", classpath, ConfigPrecedenceProbe::class.java.name, results.path)
                .redirectErrorStream(true)
                .redirectOutput(console)
                .apply {
                    environment().clear()
                    environment().putAll(exported)
                }.start()
        try {
            withClue({ "probe output:\n${console.readText()}" }) {
                process.waitFor(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS) shouldBe true
                process.exitValue() shouldBe 0
            }
        } finally {
            process.destroyForcibly()
        }
        results.readLines()
    }

class ConfigPrecedenceSpec : FunSpec({

    test("the environment reaches config only through yaml substitution, and below every override") {
        val exported =
            mapOf("DB_SCHEMA" to SCHEMA_EXPORTED, "HTTP_HOST" to HOST_EXPORTED, "DB_USER" to USER_EXPORTED)

        loadInChildJvm(exported, results = tempfile(), console = tempfile()) shouldBe
            listOf(SCHEMA_OVERRIDE, HOST_IN_YAML, USER_EXPORTED)
    }
})
