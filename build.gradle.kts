plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.shadow)
    alias(libs.plugins.ktor)
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
    testImplementation(libs.bundles.kotest)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.postgres)
    testImplementation(libs.h2)
}

tasks.test {
    useJUnitPlatform()
}

ktor {
    fatJar {
        archiveFileName = "cwfgw4k-all.jar"
    }
}
