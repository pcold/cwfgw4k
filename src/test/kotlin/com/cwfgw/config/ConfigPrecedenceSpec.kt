package com.cwfgw.config

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.engine.spec.tempfile
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path

private const val SCHEMA_OVERRIDE = "schema-from-override"
private const val SCHEMA_EXPORTED = "schema-from-environment"
private const val HOST_EXPORTED = "host-from-environment"
private const val HOST_IN_YAML = "0.0.0.0"

/**
 * A JVM cannot change its own environment, so [ConfigPrecedenceSpec] runs the loader in a child given one.
 * Writes to the file named by its argument, not stdout, because Hoplite prints its own notices there.
 */
object ConfigPrecedenceProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        val results = requireNotNull(args.singleOrNull()) { "usage: ConfigPrecedenceProbe <results file>" }
        val config = AppConfig.load(mapOf("db.schema" to SCHEMA_OVERRIDE))
        File(results).writeText(listOf(config.db.schema, config.http.host).joinToString("\n"))
    }
}

private suspend fun loadInChildJvm(
    exported: Map<String, String>,
    results: File,
): List<String> =
    withContext(Dispatchers.IO) {
        val java = Path.of(System.getProperty("java.home"), "bin", "java").toString()
        val classpath = System.getProperty("java.class.path")
        val process =
            ProcessBuilder(java, "-cp", classpath, ConfigPrecedenceProbe::class.java.name, results.path)
                .inheritIO()
                .apply {
                    environment().clear()
                    environment().putAll(exported)
                }.start()
        withClue("probe exit code") { process.waitFor() shouldBe 0 }
        results.readLines()
    }

class ConfigPrecedenceSpec : FunSpec({

    test("an exported environment variable beats neither an override nor a literal in application.yaml") {
        val exported = mapOf("DB_SCHEMA" to SCHEMA_EXPORTED, "HTTP_HOST" to HOST_EXPORTED)

        loadInChildJvm(exported, results = tempfile()) shouldBe listOf(SCHEMA_OVERRIDE, HOST_IN_YAML)
    }
})
