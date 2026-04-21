package com.cwfgw.health

import com.cwfgw.module
import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import org.jooq.SQLDialect
import org.jooq.impl.DSL

class HealthRoutesSpec : StringSpec({
    "GET /api/v1/health returns ok when database is reachable" {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:health_ok;DB_CLOSE_DELAY=-1"
        })
        try {
            testApplication {
                application { module(DSL.using(ds, SQLDialect.H2)) }
                val response = client.get("/api/v1/health")
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe """{"status":"ok","service":"cwfgw","database":"connected"}"""
            }
        } finally {
            ds.close()
        }
    }

    "GET /api/v1/health returns degraded when database is unreachable" {
        val ds = HikariDataSource(HikariConfig().apply {
            jdbcUrl = "jdbc:h2:mem:health_fail"
        })
        val dsl = DSL.using(ds, SQLDialect.H2)
        ds.close()
        testApplication {
            application { module(dsl) }
            val response = client.get("/api/v1/health")
            response.status shouldBe HttpStatusCode.InternalServerError
            response.bodyAsText() shouldBe """{"status":"degraded","service":"cwfgw","database":"unreachable"}"""
        }
    }
})
