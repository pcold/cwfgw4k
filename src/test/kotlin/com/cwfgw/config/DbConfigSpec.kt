package com.cwfgw.config

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldNotContain

private const val URL_PASSWORD = "url-password-cleartext"

private fun dbConfig(jdbcUrl: String) =
    DbConfig(
        jdbcUrl = jdbcUrl,
        user = "cwfgw4k",
        password = ConfigSecret("db-password"),
        schema = "cwfgw4k",
        maxPoolSize = 10,
    )

class DbConfigSpec : FunSpec({
    test("jdbcUrlWithoutQuery drops a password carried as a query parameter") {
        val config = dbConfig("jdbc:postgresql://db.internal:5432/cwfgw4k?user=cwfgw4k&password=$URL_PASSWORD")

        config.jdbcUrlWithoutQuery shouldNotContain URL_PASSWORD
        config.jdbcUrlWithoutQuery shouldBe "jdbc:postgresql://db.internal:5432/cwfgw4k"
    }

    test("jdbcUrlWithoutQuery keeps a URL that has no query string whole") {
        dbConfig("jdbc:postgresql://localhost:5432/cwfgw4k").jdbcUrlWithoutQuery shouldBe
            "jdbc:postgresql://localhost:5432/cwfgw4k"
    }
})
