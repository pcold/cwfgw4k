import org.gradle.api.tasks.testing.logging.TestExceptionFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
}

group = "com.cwfgw"
version = "0.1.0"

repositories {
    mavenCentral()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

kotlin {
    // Library module: src/CLAUDE.md → "enable explicit API mode for library modules".
    explicitApi()
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

// Resolvable, not a legacy configuration: this is only ever resolved into a classpath here, and
// the default role would also publish it as an attribute-less variant of this module.
val ruleFixtures = configurations.resolvable("ruleFixtures").get()

// The Gradle daemon caches the worker classloader detekt loads this jar into, keyed on the
// classpath and not on its contents: a rule edit rebuilds the jar, and `:detekt` then runs the
// previous rule and reports the previous findings. `./gradlew --stop` between the edit and the
// run is what makes a local rule change visible.
dependencies {
    // compileOnly, not implementation: detekt loads this jar into a classloader that
    // already holds its own api and analysis-api jars, and a second copy there would
    // give the rule a `Rule` class the loader does not recognise as detekt's own.
    compileOnly(libs.detekt.api)
    compileOnly(libs.detekt.kotlin.analysis.api)

    testImplementation(libs.detekt.api)
    testImplementation(libs.detekt.kotlin.analysis.api)
    testImplementation(libs.detekt.test)
    testImplementation(libs.bundles.kotest)

    // Not testImplementation: these are not on the spec's own classpath, they are the
    // classpath the spec hands the Analysis API to compile its fixtures against. The rule
    // matches Temporal's and kotlin-logging's real signatures, so a stub of either would
    // let the spec keep passing after the argument order it resolves against had moved.
    ruleFixtures(libs.kotlin.logging)
    ruleFixtures(libs.temporal.sdk)

    // This module shares the root `detekt.yml`, and detekt fails a run whose config names a
    // ruleset it did not load — so without loading its own output here, `:detekt-rules:detekt`
    // dies on the `cwfgw:` section rather than checking anything.
    detektPlugins(project(":detekt-rules"))
}

tasks.test {
    useJUnitPlatform()
    // A root-level `tasks.withType<Test>` does not reach this task, so everything below mirrors the
    // root module.
    testLogging {
        exceptionFormat = TestExceptionFormat.FULL
    }
    listOf("kotest.filter.specs", "kotest.filter.tests").forEach { filter ->
        System.getProperty(filter)?.let { systemProperty(filter, it) }
    }
    // The rule-coverage spec reads a checked-in file, which Gradle cannot see. Miss it and
    // editing it leaves this task UP-TO-DATE, so the assertion over it never re-runs.
    inputs.files("$rootDir/detekt.yml")
    systemProperty("cwfgw.repoRoot", rootDir.absolutePath)
    // A `Configuration` captured in a task action is a script object reference, which the
    // configuration cache refuses to serialize; a file collection over it is not.
    val fixtureClasspath = files(ruleFixtures)
    inputs.files(fixtureClasspath)
    doFirst {
        systemProperty("cwfgw.fixtureClasspath", fixtureClasspath.asPath)
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/detekt.yml"))
    ignoreFailures = false
}

tasks.withType<dev.detekt.gradle.Detekt>().configureEach {
    jvmTarget = "21"
    reports {
        html.required.set(true)
        checkstyle.required.set(true)
        sarif.required.set(false)
        markdown.required.set(false)
    }
}

// The rule resolves types, so it reports nothing — as a green build — without a compile
// classpath. Mirrors the root module.
tasks.named<dev.detekt.gradle.Detekt>("detekt") {
    classpath.setFrom(sourceSets["test"].runtimeClasspath, sourceSets["test"].output)
    doFirst {
        check(classpath.files.isNotEmpty()) {
            "detekt has no compile classpath, so every type-resolving rule would be skipped silently"
        }
    }
}

tasks.withType<dev.detekt.gradle.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
    classpath.setFrom(sourceSets["test"].runtimeClasspath, sourceSets["test"].output)
}

ktlint {
    version.set("1.8.0")
    verbose.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

// Above the root module's 85: this module is two rules, and an unexercised branch in one is a
// verdict nobody checked.
kover {
    reports {
        verify {
            rule("Overall line coverage") {
                minBound(95, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("koverXmlReport", "koverVerify")
}

// Route `test` through lint so `./gradlew :detekt-rules:test` can't pass while
// ktlint/detekt fails — mirrors the root module.
tasks.named("test") {
    dependsOn("ktlintCheck", "detekt")
}
