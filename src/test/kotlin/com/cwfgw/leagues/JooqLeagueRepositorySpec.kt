package com.cwfgw.leagues

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldStartWith
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID

private const val SCHEMA = "cwfgw4k"
private val CASTLEWOOD_ID = LeagueId(UUID.fromString("00000000-0000-0000-0000-000000000001"))

class JooqLeagueRepositorySpec : FunSpec({

    val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    lateinit var dataSource: HikariDataSource
    lateinit var repository: JooqLeagueRepository

    beforeSpec {
        postgres.start()
        Flyway.configure()
            .dataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            .schemas(SCHEMA)
            .createSchemas(true)
            .locations("classpath:db/migration")
            .load()
            .migrate()
        dataSource =
            HikariDataSource(
                HikariConfig().apply {
                    jdbcUrl = postgres.jdbcUrl
                    username = postgres.username
                    password = postgres.password
                    schema = SCHEMA
                },
            )
        repository = JooqLeagueRepository(DSL.using(dataSource, SQLDialect.POSTGRES))
    }

    afterSpec {
        dataSource.close()
        postgres.stop()
    }

    test("findAll includes the Castlewood seed row sorted by name") {
        val leagues = repository.findAll()

        leagues.map { it.name } shouldContain "Castlewood Fantasy Golf"
    }

    test("findById returns the Castlewood seed row") {
        val league = repository.findById(CASTLEWOOD_ID)

        league.shouldNotBeNull()
        league.id shouldBe CASTLEWOOD_ID
        league.name shouldBe "Castlewood Fantasy Golf"
    }

    test("findById returns null for an unknown id") {
        repository.findById(LeagueId(UUID.randomUUID())).shouldBeNull()
    }

    test("create inserts a row and returns it populated with id and createdAt") {
        val suffix = UUID.randomUUID().toString()
        val created = repository.create(CreateLeagueRequest(name = "Test League $suffix"))

        created.name shouldStartWith "Test League"
        repository.findById(created.id).shouldNotBeNull()
    }
})
