package com.cwfgw.config

import com.cwfgw.cache.FakeCacheRepository
import com.cwfgw.cache.RequestCache
import com.cwfgw.testing.FakeTransactor
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldMatch
import java.time.Duration

private const val CLOUD_RUN_REVISION = "cwfgw4k-00042-xyz"
private const val BUILD_ID = "5d6c4aba55ed4291"
private const val NEXT_BUILD_ID = "0f1e2d3c4b5a6978"
private const val CACHED_URI = "/api/v1/seasons"
private val SWEEP_INTERVAL = PositiveSeconds.of(300).shouldNotBeNull()

private fun cacheConfig(deployRevision: String): CacheConfig =
    CacheConfig(defaultTtlSeconds = 300, sweepIntervalSeconds = SWEEP_INTERVAL, deployRevision = deployRevision)

private fun requestCache(
    rows: FakeCacheRepository,
    revision: String,
): RequestCache = RequestCache(rows, FakeTransactor(), revision, defaultTtl = Duration.ofMinutes(5))

class CacheRevisionSpec : FunSpec({

    test("a deploy revision is the revision verbatim, whatever the build id") {
        cacheConfig(deployRevision = CLOUD_RUN_REVISION).revision(buildId = BUILD_ID) shouldBe CLOUD_RUN_REVISION
    }

    test("without a deploy revision, the build id is the revision") {
        cacheConfig(deployRevision = "").revision(buildId = BUILD_ID) shouldBe BUILD_ID
    }

    test("without a deploy revision or a build id, the revision is local") {
        cacheConfig(deployRevision = "").revision(buildId = null) shouldBe LOCAL_REVISION
    }

    test("an unexported K_REVISION leaves the deploy revision blank, so the build id can stand in") {
        AppConfig.loadForSeed().cache.deployRevision shouldBe System.getenv("K_REVISION").orEmpty()
    }

    test("the build that runs this spec carries its id on the classpath") {
        readBuildId().shouldNotBeNull() shouldMatch Regex("[0-9a-f]{16}")
    }

    test("two builds without a deploy revision each read only the bodies they stored") {
        val rows = FakeCacheRepository()
        val unversioned = cacheConfig(deployRevision = "")
        val firstBuild = requestCache(rows, unversioned.revision(buildId = BUILD_ID))
        val nextBuild = requestCache(rows, unversioned.revision(buildId = NEXT_BUILD_ID))

        firstBuild.cachedJsonGet(CACHED_URI, CACHED_URI) { "[\"old shape\"]" } shouldBe "[\"old shape\"]"
        nextBuild.cachedJsonGet(CACHED_URI, CACHED_URI) { "[\"new shape\"]" } shouldBe "[\"new shape\"]"
        firstBuild.cachedJsonGet(CACHED_URI, CACHED_URI) { "[\"refetched\"]" } shouldBe "[\"old shape\"]"
    }
})
