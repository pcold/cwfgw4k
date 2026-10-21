package com.cwfgw.config

/** Must match the file the `generateBuildId` task in build.gradle.kts writes. */
private const val BUILD_ID_RESOURCE = "/build-id.txt"

/** Null for a run that skipped processResources. */
fun readBuildId(): String? = CacheConfig::class.java.getResource(BUILD_ID_RESOURCE)?.readText()?.trim()
