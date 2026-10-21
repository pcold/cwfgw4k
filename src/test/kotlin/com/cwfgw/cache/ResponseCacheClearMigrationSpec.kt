package com.cwfgw.cache

import com.cwfgw.testing.migrateSchemaTo
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private const val SCHEMA = "response_cache_clear"

class ResponseCacheClearMigrationSpec : FunSpec({

    val postgres = postgresHarness()

    test("V027 deletes a live cached body stored before it") {
        postgres.migrateSchemaTo(SCHEMA, "26")
        postgres.dsl.execute(
            "INSERT INTO $SCHEMA.cache (key, value, expires_at) " +
                "VALUES ('rev-a /api/v1/seasons', '[{\"season_year\": 2026}]', now() + interval '1 hour')",
        )

        postgres.migrateSchemaTo(SCHEMA, "27")

        postgres.dsl.fetchCount(postgres.dsl.selectFrom("$SCHEMA.cache")) shouldBe 0
    }
})
