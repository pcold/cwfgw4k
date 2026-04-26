plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.ktor)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    application
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
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21
        languageVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        apiVersion = org.jetbrains.kotlin.gradle.dsl.KotlinVersion.KOTLIN_2_2
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

application {
    mainClass = "com.cwfgw.MainKt"
}

dependencies {
    implementation(libs.bundles.ktor.server)
    implementation(libs.bundles.ktor.client)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    implementation(libs.bundles.jooq)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)

    implementation(libs.bcrypt)
    implementation(libs.bundles.hoplite)

    implementation(libs.kotlin.logging)
    implementation(libs.logback.classic)

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.ktor.client.mock)
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.kotest.extensions.testcontainers)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.docker.java.api)
    testImplementation(libs.docker.java.transport.zerodep)
}

tasks.test {
    useJUnitPlatform()
    environment("DOCKER_API_VERSION", "1.44")
    systemProperty("api.version", "1.44")
}

ktor {
    fatJar {
        archiveFileName = "cwfgw4k-all.jar"
    }
}

val jooqGenDir = layout.buildDirectory.dir("generated/jooq")

sourceSets {
    create("jooqCodegen")
}

dependencies {
    "jooqCodegenImplementation"(libs.jooq)
    "jooqCodegenImplementation"(libs.jooq.codegen)
    "jooqCodegenImplementation"(libs.jooq.meta)
    "jooqCodegenImplementation"(libs.flyway.core)
    "jooqCodegenImplementation"(libs.flyway.postgres)
    "jooqCodegenImplementation"(libs.postgres)
    "jooqCodegenImplementation"(libs.testcontainers.postgres)
    "jooqCodegenImplementation"(libs.docker.java.api)
    "jooqCodegenImplementation"(libs.docker.java.transport.zerodep)
    "jooqCodegenImplementation"(libs.logback.classic)
}

val generateJooq by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Generate jOOQ classes against a throwaway Postgres migrated by Flyway."
    classpath = sourceSets["jooqCodegen"].runtimeClasspath
    mainClass.set("com.cwfgw.jooqcodegen.JooqCodegenKt")
    args(
        layout.projectDirectory.dir("src/main/resources/db/migration").asFile.absolutePath,
        jooqGenDir.get().asFile.absolutePath,
        "com.cwfgw.jooq",
    )
    environment("DOCKER_API_VERSION", "1.44")
    systemProperty("api.version", "1.44")
    inputs.dir("src/main/resources/db/migration")
    outputs.dir(jooqGenDir)
}

sourceSets.main {
    kotlin.srcDir(jooqGenDir)
}

tasks.named("compileKotlin") {
    dependsOn(generateJooq)
}

tasks.named("runKtlintCheckOverMainSourceSet") {
    dependsOn(generateJooq)
}
tasks.named("runKtlintFormatOverMainSourceSet") {
    dependsOn(generateJooq)
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom(files("$rootDir/detekt.yml"))
    ignoreFailures = false
}

tasks.withType<io.gitlab.arturbosch.detekt.Detekt>().configureEach {
    jvmTarget = "21"
    exclude("**/generated/**")
    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
        md.required.set(false)
    }
}

tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
    jvmTarget = "21"
}

ktlint {
    verbose.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

kover {
    reports {
        filters {
            excludes {
                packages(
                    "com.cwfgw.jooq",
                    "com.cwfgw.jooq.*",
                    "com.cwfgw.jooqcodegen",
                )
            }
        }
        verify {
            rule("Overall line coverage") {
                minBound(85, kotlinx.kover.gradle.plugin.dsl.CoverageUnit.LINE)
            }
        }
    }
}

tasks.named("check") {
    dependsOn("koverXmlReport", "koverHtmlReport", "koverVerify")
}

// Route `test` through lint so `./gradlew test` can't pass while ktlint/detekt fails.
tasks.named("test") {
    dependsOn("ktlintCheck", "detekt")
}

// Local-dev convenience: reset the docker-compose Postgres to a clean state and run SeedMain
// against it. SeedMain re-uses the same AppConfig as the running app, so the docker compose
// defaults must match application.yaml's DB defaults (cwfgw4k / cwfgw4k / cwfgw4k).
tasks.register<JavaExec>("seed") {
    group = "application"
    description = "Reset docker-compose Postgres and run SeedMain to populate dev data"
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.cwfgw.seed.SeedMainKt")

    doFirst {
        // Fresh slate: drop the named volume so Flyway runs against an empty DB.
        runCommand("docker", "compose", "down", "-v")
        runCommand("docker", "compose", "up", "-d", "postgres")

        // Postgres container is up but not necessarily accepting connections; poll pg_isready.
        val deadline = System.currentTimeMillis() + pgReadyTimeoutMs
        var ready = false
        while (!ready && System.currentTimeMillis() < deadline) {
            val exit =
                ProcessBuilder("docker", "compose", "exec", "-T", "postgres", "pg_isready", "-U", "cwfgw4k")
                    .redirectErrorStream(true)
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .start()
                    .waitFor()
            if (exit == 0) ready = true else Thread.sleep(pgReadyPollIntervalMs)
        }
        if (!ready) throw GradleException("Postgres did not become ready within ${pgReadyTimeoutMs / 1000}s")
    }
}

private fun runCommand(vararg cmd: String) {
    val exit = ProcessBuilder(*cmd).inheritIO().start().waitFor()
    if (exit != 0) throw GradleException("'${cmd.joinToString(" ")}' exited $exit")
}

private val pgReadyTimeoutMs: Long = 60_000
private val pgReadyPollIntervalMs: Long = 500
