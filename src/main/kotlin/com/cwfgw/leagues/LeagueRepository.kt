package com.cwfgw.leagues

import com.cwfgw.jooq.tables.references.LEAGUES
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Record

interface LeagueRepository {
    suspend fun findAll(): List<League>

    suspend fun findById(id: LeagueId): League?

    suspend fun create(request: CreateLeagueRequest): League
}

fun LeagueRepository(dsl: DSLContext): LeagueRepository = JooqLeagueRepository(dsl)

private class JooqLeagueRepository(private val dsl: DSLContext) : LeagueRepository {
    override suspend fun findAll(): List<League> =
        withContext(Dispatchers.IO) {
            dsl.select(LEAGUES.ID, LEAGUES.NAME, LEAGUES.CREATED_AT)
                .from(LEAGUES)
                .orderBy(LEAGUES.NAME)
                .fetch(::toLeague)
        }

    override suspend fun findById(id: LeagueId): League? =
        withContext(Dispatchers.IO) {
            dsl.select(LEAGUES.ID, LEAGUES.NAME, LEAGUES.CREATED_AT)
                .from(LEAGUES)
                .where(LEAGUES.ID.eq(id.value))
                .fetchOne(::toLeague)
        }

    override suspend fun create(request: CreateLeagueRequest): League =
        withContext(Dispatchers.IO) {
            val inserted =
                dsl.insertInto(LEAGUES)
                    .set(LEAGUES.NAME, request.name)
                    .returning(LEAGUES.ID, LEAGUES.NAME, LEAGUES.CREATED_AT)
                    .fetchOne() ?: error("INSERT RETURNING produced no row for leagues")
            toLeague(inserted)
        }

    private fun toLeague(record: Record): League =
        League(
            id = LeagueId(checkNotNull(record[LEAGUES.ID]) { "leagues.id is NOT NULL but returned null" }),
            name = checkNotNull(record[LEAGUES.NAME]) { "leagues.name is NOT NULL but returned null" },
            createdAt =
                checkNotNull(record[LEAGUES.CREATED_AT]) {
                    "leagues.created_at is NOT NULL but returned null"
                }.toInstant(),
        )
}
