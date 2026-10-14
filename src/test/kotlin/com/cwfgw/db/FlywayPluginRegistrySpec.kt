package com.cwfgw.db

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URL

private const val PLUGIN_REGISTRY = "META-INF/services/org.flywaydb.core.extensibility.Plugin"

class FlywayPluginRegistrySpec : FunSpec({
    // Specs load every Flyway jar's own registry, but the fat jar ships only this project's copy: a provider missing
    // from it passes every other spec and fails production at boot. Regenerate it from the jars on each Flyway bump.
    test("the checked-in plugin registry lists exactly the providers the Flyway jars register") {
        val (projectCopies, jarCopies) = registries().partition { it.protocol == "file" }

        projectCopies.size shouldBe 1
        providers(projectCopies) shouldBe providers(jarCopies)
    }
})

private fun registries(): List<URL> =
    FlywayPluginRegistrySpec::class.java.classLoader
        .getResources(PLUGIN_REGISTRY)
        .toList()

private fun providers(registries: List<URL>): Set<String> =
    registries
        .flatMap { registry -> registry.readText().lines() }
        .map(String::trim)
        .filter { line -> line.isNotEmpty() && !line.startsWith("#") }
        .toSet()
