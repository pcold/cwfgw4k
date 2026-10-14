import org.gradle.api.tasks.testing.logging.TestExceptionFormat
import java.util.Properties

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.ktor)
    alias(libs.plugins.detekt)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.kover)
    alias(libs.plugins.node.gradle)
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
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xcontext-parameters")
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
    implementation(libs.kotlinx.coroutines.debug)

    implementation(libs.bundles.jooq)
    implementation(libs.hikari)
    implementation(libs.postgres)
    implementation(libs.flyway.core)
    implementation(libs.flyway.postgres)
    implementation(libs.cloud.sql.postgres)

    implementation(libs.bcrypt)
    implementation(libs.caffeine)
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

// Mac Docker Desktop 29+ needs DOCKER_API_VERSION=1.44 against the docker.raw.sock workaround;
// Linux CI needs none of it, and a daemon older than that rejects 1.44 as too new. Apply the
// pin only when the macOS raw socket is present so both environments work without per-step
// overrides.
val macDockerRawSock: java.io.File =
    file("${System.getProperty("user.home")}/Library/Containers/com.docker.docker/Data/docker.raw.sock")

fun JavaExec.applyMacDockerWorkaround() {
    if (macDockerRawSock.exists()) {
        environment("DOCKER_HOST", "unix://${macDockerRawSock.absolutePath}")
        environment("DOCKER_API_VERSION", "1.44")
        // Ryuk's cleanup container would try to bind-mount docker.raw.sock from
        // the macOS host path, which fails inside the daemon's Linux VM. The
        // tradeoff is no automatic container reaping; testcontainers cleans up
        // its own containers on JVM exit, so it only matters for crashed runs.
        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
        systemProperty("api.version", "1.44")
    }
}

tasks.withType<Test>().configureEach {
    // Each test JVM starts at most one Testcontainers Postgres, shared by its specs, so forks share
    // no state. The cap of two was sized for a 2 vCPU / 4 GB CI worker (two JVMs + two containers);
    // override with -PtestForks=N where there is headroom.
    maxParallelForks = (project.findProperty("testForks") as String?)?.toIntOrNull() ?: 2
    testLogging {
        // Gradle's default (SHORT) prints the exception class and source line but not
        // getMessage(). An intermittent failure that reaches only the gate log is then
        // undiagnosable.
        exceptionFormat = TestExceptionFormat.FULL
    }

    // Kotest reads its spec/test filters from system properties on the worker JVM, and Gradle
    // forwards none of the daemon's -D by default: an unforwarded filter runs the whole suite and
    // reports the result as the filtered one.
    listOf("kotest.filter.specs", "kotest.filter.tests").forEach { filter ->
        System.getProperty(filter)?.let { systemProperty(filter, it) }
    }
    if (macDockerRawSock.exists()) {
        environment("DOCKER_HOST", "unix://${macDockerRawSock.absolutePath}")
        environment("DOCKER_API_VERSION", "1.44")
        environment("TESTCONTAINERS_RYUK_DISABLED", "true")
        systemProperty("api.version", "1.44")
    }
}

tasks.test {
    useJUnitPlatform()
}

ktor {
    fatJar {
        archiveFileName = "cwfgw4k-all.jar"
    }
}

// Flyway (and other libraries that load plugins via java.util.ServiceLoader)
// register implementations through META-INF/services/* files. Shadow's default
// behaviour is to overwrite same-path files when merging dependency JARs, which
// strips Flyway's location-parser registrations and crashes the app at boot
// with "Unknown prefix for location (should be one of )". mergeServiceFiles()
// concatenates them instead.
tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar>().configureEach {
    mergeServiceFiles()
}

val jooqGenDir = layout.buildDirectory.dir("generated/jooq")
val migrationsDir: File = layout.projectDirectory.dir("src/main/resources/db/migration").asFile
val codegenSourceDir: File = layout.projectDirectory.dir("src/jooqCodegen/kotlin").asFile

// JavaExec's `args` are an @Input and its working directory is not, so an absolute path in an
// argument keys the cache entry to the checkout that produced it and a second worktree at the same
// commit misses on the directory name alone.
val projectRoot: File = layout.projectDirectory.asFile

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
    workingDir(projectRoot)
    args(
        migrationsDir.relativeTo(projectRoot).path,
        jooqGenDir.get().asFile.relativeTo(projectRoot).path,
        "com.cwfgw.jooq",
    )
    applyMacDockerWorkaround()
    inputs.dir(migrationsDir).withPropertyName("migrations").withPathSensitivity(PathSensitivity.RELATIVE)
    // The generator program is an input too: a ForcedType or binding change alters the output
    // without touching a migration, and this task would otherwise report itself up to date.
    inputs.dir(codegenSourceDir).withPropertyName("generator").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(jooqGenDir).withPropertyName("generatedClasses")
    // JavaExec is @DisableCachingByDefault, so without this the entry is never stored and every
    // fresh worktree starts its own container for a migration chain that has not moved.
    outputs.cacheIf { true }
}

// The migration chain records what each step changed; nothing records what the steps add up to.
// This renders the result, so a migration's effect is reviewable beside the SQL that caused it.
val trackedSchemaDump: File = layout.projectDirectory.file("db/schema.sql").asFile
val regeneratedSchemaDump: File = layout.buildDirectory.file("schema/schema.sql").get().asFile

val generateSchemaDump by tasks.registering(JavaExec::class) {
    group = "build"
    description = "Dump the fully-migrated schema to build/schema/schema.sql."
    classpath = sourceSets["jooqCodegen"].runtimeClasspath
    mainClass.set("com.cwfgw.jooqcodegen.SchemaDumpKt")
    workingDir(projectRoot)
    args(
        migrationsDir.relativeTo(projectRoot).path,
        regeneratedSchemaDump.relativeTo(projectRoot).path,
    )
    applyMacDockerWorkaround()
    inputs.dir(migrationsDir).withPropertyName("migrations").withPathSensitivity(PathSensitivity.RELATIVE)
    // An input for the same reason generateJooq declares it.
    inputs.dir(codegenSourceDir).withPropertyName("generator").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.file(regeneratedSchemaDump).withPropertyName("schemaDump")
    outputs.cacheIf { true }
}

val updateSchemaDump by tasks.registering(Copy::class) {
    group = "build"
    description = "Refresh the tracked db/schema.sql from the current migration chain."
    from(generateSchemaDump)
    into(trackedSchemaDump.parentFile)
}

val checkSchemaDump by tasks.registering {
    group = "verification"
    description = "Fail when db/schema.sql no longer matches the migration chain."
    dependsOn(generateSchemaDump)
    inputs.file(trackedSchemaDump)
    inputs.file(regeneratedSchemaDump)
    // Locals, not the script properties above: a doLast that reads a script property captures the
    // whole build script, which the configuration cache cannot serialize.
    val tracked = trackedSchemaDump
    val regenerated = regeneratedSchemaDump
    val deltaLimit = 120
    doLast {
        val before = tracked.readLines()
        val after = regenerated.readLines()
        if (before != after) {
            // Lines unique to one side rather than a positional diff: a dump's statements are
            // distinct, so this names the column or constraint that moved, where a positional diff
            // of a file whose middle grew reports every line after the insertion.
            val withoutSeparators = { lines: List<String> -> lines.filter { it.isNotBlank() && it != "--" } }
            val removed = withoutSeparators(before) - withoutSeparators(after).toSet()
            val added = withoutSeparators(after) - withoutSeparators(before).toSet()
            val shown = removed.take(deltaLimit).map { "-$it" } + added.take(deltaLimit).map { "+$it" }
            val hidden =
                (removed.size - deltaLimit).coerceAtLeast(0) + (added.size - deltaLimit).coerceAtLeast(0)
            val delta =
                when {
                    shown.isEmpty() -> listOf("(the same lines, reordered)")
                    hidden == 0 -> shown
                    else -> shown + "… and $hidden further differing lines."
                }
            val preamble =
                listOf(
                    "db/schema.sql no longer matches src/main/resources/db/migration.",
                    "Run `./gradlew updateSchemaDump` and commit the result.",
                    "",
                    "- is a line only in db/schema.sql, + only in the regenerated schema:",
                )
            throw GradleException((preamble + delta).joinToString("\n"))
        }
    }
}

tasks.named("check") {
    dependsOn(checkSchemaDump)
}

sourceSets.main {
    kotlin.srcDir(jooqGenDir)
}

tasks.named("compileKotlin") {
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
    version.set("1.8.0")
    verbose.set(true)
    outputToConsole.set(true)
    ignoreFailures.set(false)
    filter {
        exclude { it.file.path.contains("/build/") }
    }
}

// The filter above already drops every generated file, so these tasks need no generateJooq edge.
// The directory must still leave their sources: as a declared input root it fails Gradle's
// implicit-dependency validation once the generator is in the same graph.
listOf("runKtlintCheckOverMainSourceSet", "runKtlintFormatOverMainSourceSet").forEach { taskName ->
    tasks.named<org.jlleitschuh.gradle.ktlint.tasks.BaseKtLintCheckTask>(taskName) {
        setSource(sourceSets.main.get().kotlin.srcDirs - jooqGenDir.get().asFile)
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

// --- Frontend (ui/) ---
// node-gradle downloads its own Node so CI and a fresh checkout can run npm tasks without
// a separate toolchain step. uiInstall/uiTest/uiBuild mirror the Scala project.
node {
    download.set(true)
    version.set("24.15.0")
    nodeProjectDir.set(file("$projectDir/ui"))
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("uiInstall") {
    description = "Install UI dependencies"
    group = "frontend"
    args.set(listOf("ci"))
    inputs.file("ui/package.json")
    inputs.file("ui/package-lock.json")
    outputs.dir("ui/node_modules")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("uiTest") {
    description = "Run UI unit tests with coverage"
    group = "frontend"
    dependsOn("uiInstall")
    args.set(listOf("run", "test:coverage"))
    inputs.dir("ui/src")
    inputs.file("ui/index.html")
    inputs.file("ui/package.json")
    inputs.file("ui/vite.config.ts")
    // eslint.config.test.ts lints fixtures through the real config.
    inputs.file("ui/eslint.config.js")
    // Vitest transforms the sources under the compiler options these set.
    inputs.files("ui/tsconfig.json", "ui/tsconfig.app.json", "ui/tsconfig.node.json")
    outputs.dir("ui/coverage")
    outputs.dir("ui/test-report")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("uiBuild") {
    description = "Build the UI production bundle"
    group = "frontend"
    dependsOn("uiInstall")
    args.set(listOf("run", "build"))
    inputs.dir("ui/src")
    inputs.file("ui/index.html")
    inputs.file("ui/package.json")
    inputs.file("ui/postcss.config.js")
    inputs.file("ui/vite.config.ts")
    // `npm run build` is `tsc -b && vite build`, so the tsconfigs are what it compiles from.
    inputs.files("ui/tsconfig.json", "ui/tsconfig.app.json", "ui/tsconfig.node.json")
    outputs.dir("ui/dist")
}

tasks.register<com.github.gradle.node.npm.task.NpmTask>("uiLint") {
    description = "Lint the UI with ESLint"
    group = "frontend"
    dependsOn("uiInstall")
    args.set(listOf("run", "lint"))
    inputs.dir("ui/src")
    inputs.files("ui/package.json", "ui/package-lock.json")
    inputs.file("ui/eslint.config.js")
    // Type-aware rules resolve every file through these.
    inputs.files("ui/tsconfig.json", "ui/tsconfig.app.json", "ui/tsconfig.node.json")
    // `eslint .` lints the config files at the ui/ root too.
    inputs.files("ui/postcss.config.js", "ui/vite.config.ts")
    // A stamp, not `eslint --cache`: that cache keys a file on its own text, so a type changed in one
    // file leaves the type-aware findings it causes in another unreported.
    val passedStamp = layout.buildDirectory.file("uiLint/passed")
    outputs.file(passedStamp)
    doLast { passedStamp.get().asFile.writeText("") }
}

// Bundle ui/dist into the runtime jar under static/ so StaticRoutes can serve the React UI
// from the classpath in Cloud Run. Hooked into processResources (rather than only into the
// jar tasks) so `./gradlew run` also serves the production bundle locally if you want it,
// while `npm run dev` remains the normal local-dev workflow via the Vite proxy.
tasks.named<Copy>("processResources") {
    dependsOn("uiBuild")
    from("ui/dist") {
        into("static")
    }
}

tasks.named("check") {
    dependsOn("uiTest")
    dependsOn("uiLint")
}

// Load dev.properties (gitignored, copied from dev.properties.example) and apply each entry
// as an environment variable on the JVM the task launches. Mirrors how Cloud Run injects env
// in production, so AppConfig's resolution is identical between local and prod. No-op if the
// file is absent — the app's own validation (e.g., AUTH_SESSION_SECRET) surfaces the missing
// value at startup with a clear message.
fun JavaExec.loadDevEnv() {
    val devProps = layout.projectDirectory.file("dev.properties").asFile
    if (!devProps.exists()) return
    doFirst {
        val props = Properties()
        devProps.inputStream().use(props::load)
        val names: Set<String> = props.stringPropertyNames()
        for (name in names) {
            environment(name, props.getProperty(name))
        }
    }
}

tasks.named<JavaExec>("run") {
    loadDevEnv()
}

// Local-dev convenience: reset the docker-compose Postgres to a clean state and run SeedMain
// against it. SeedMain re-uses the same AppConfig as the running app, so the docker compose
// defaults must match application.yaml's DB defaults (cwfgw4k / cwfgw4k / cwfgw4k).
//
// The docker compose calls and the pg_isready poll are inlined inside doFirst rather than
// extracted to script-level helpers because the configuration cache cannot serialize
// references from a task action back to the build script object.
tasks.register<JavaExec>("seed") {
    group = "application"
    description = "Reset docker-compose Postgres and run SeedMain to populate dev data"
    dependsOn("classes")
    classpath = sourceSets.main.get().runtimeClasspath
    mainClass.set("com.cwfgw.seed.SeedMainKt")
    loadDevEnv()

    val pgReadyTimeoutMs: Long = 60_000
    val pgReadyPollIntervalMs: Long = 500

    doFirst {
        fun runCommand(vararg cmd: String) {
            val exit = ProcessBuilder(*cmd).inheritIO().start().waitFor()
            if (exit != 0) throw GradleException("'${cmd.joinToString(" ")}' exited $exit")
        }

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
