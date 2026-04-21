package com.cwfgw.golfers

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainInOrder
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import org.flywaydb.core.Flyway
import org.jooq.SQLDialect
import org.jooq.impl.DSL
import org.testcontainers.containers.PostgreSQLContainer
import java.util.UUID

private const val SCHEMA = "cwfgw4k"

class JooqGolferRepositorySpec : FunSpec({

    val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    lateinit var dataSource: HikariDataSource
    lateinit var repository: JooqGolferRepository

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
        repository = JooqGolferRepository(DSL.using(dataSource, SQLDialect.POSTGRES))
    }

    beforeTest {
        DSL.using(dataSource, SQLDialect.POSTGRES).deleteFrom(
            com.cwfgw.jooq.tables.references.GOLFERS,
        ).execute()
    }

    afterSpec {
        dataSource.close()
        postgres.stop()
    }

    test("create persists the golfer and returns a row populated with id and updatedAt") {
        val created =
            repository.create(
                CreateGolferRequest(
                    pgaPlayerId = "pga-001",
                    firstName = "Scottie",
                    lastName = "Scheffler",
                    country = "USA",
                    worldRanking = 1,
                ),
            )

        created.firstName shouldBe "Scottie"
        created.active shouldBe true
        repository.findById(created.id).shouldNotBeNull()
    }

    test("findAll with activeOnly=true excludes inactive golfers") {
        val active = repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
        val inactive = repository.create(CreateGolferRequest(firstName = "Tiger", lastName = "Woods"))
        repository.update(inactive.id, UpdateGolferRequest(active = false))

        val result = repository.findAll(activeOnly = true, search = null)

        result.map { it.id } shouldContainExactly listOf(active.id)
    }

    test("findAll with activeOnly=false returns every golfer") {
        val a = repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy", worldRanking = 2))
        val b = repository.create(CreateGolferRequest(firstName = "Tiger", lastName = "Woods", worldRanking = 1))
        repository.update(b.id, UpdateGolferRequest(active = false))

        val result = repository.findAll(activeOnly = false, search = null)

        result.map { it.id } shouldContainExactly listOf(b.id, a.id)
    }

    test("findAll sorts by world_ranking asc nulls last then last_name") {
        val unranked = repository.create(CreateGolferRequest(firstName = "Phil", lastName = "Mickelson"))
        val ranked2 = repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy", worldRanking = 2))
        val ranked1 =
            repository.create(
                CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler", worldRanking = 1),
            )

        val result = repository.findAll(activeOnly = true, search = null)

        result.map { it.id } shouldContainInOrder listOf(ranked1.id, ranked2.id, unranked.id)
    }

    test("findAll search is case-insensitive substring over first and last name") {
        val rory = repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
        val tiger = repository.create(CreateGolferRequest(firstName = "Tiger", lastName = "Woods"))
        repository.create(CreateGolferRequest(firstName = "Phil", lastName = "Mickelson"))

        val firstNameMatch = repository.findAll(activeOnly = true, search = "ROR")
        firstNameMatch.map { it.id } shouldContainExactly listOf(rory.id)

        val lastNameMatch = repository.findAll(activeOnly = true, search = "wood")
        lastNameMatch.map { it.id } shouldContainExactly listOf(tiger.id)
    }

    test("findById returns null for unknown id") {
        repository.findById(GolferId(UUID.randomUUID())).shouldBeNull()
    }

    test("update applies only the supplied fields and bumps updated_at") {
        val created =
            repository.create(
                CreateGolferRequest(firstName = "Rory", lastName = "McIlroy", country = "NIR", worldRanking = 3),
            )

        val updated = repository.update(created.id, UpdateGolferRequest(worldRanking = 2, active = false))

        updated.shouldNotBeNull()
        updated.firstName shouldBe "Rory"
        updated.country shouldBe "NIR"
        updated.worldRanking shouldBe 2
        updated.active shouldBe false
        (updated.updatedAt >= created.updatedAt) shouldBe true
    }

    test("update with no fields returns the existing row unchanged") {
        val created = repository.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))

        val result = repository.update(created.id, UpdateGolferRequest())

        result shouldBe created
    }

    test("update returns null for unknown id") {
        repository.update(GolferId(UUID.randomUUID()), UpdateGolferRequest(firstName = "Ghost")).shouldBeNull()
    }

    test("empty table returns an empty list") {
        repository.findAll(activeOnly = true, search = null).shouldBeEmpty()
    }
})
