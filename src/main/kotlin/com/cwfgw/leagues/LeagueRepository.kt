package com.cwfgw.leagues

import com.cwfgw.db.TransactionContext
import com.cwfgw.jooq.tables.references.LEAGUES
import org.jooq.Record

interface LeagueRepository {
    context(ctx: TransactionContext)
    fun findAll(): List<League>

    context(ctx: TransactionContext)
    fun findById(id: LeagueId): League?

    context(ctx: TransactionContext)
    fun create(request: CreateLeagueRequest): League
}

fun LeagueRepository(): LeagueRepository = JooqLeagueRepository()

private class JooqLeagueRepository : LeagueRepository {
    context(ctx: TransactionContext)
    override fun findAll(): List<League> =
        ctx.dsl.select(LEAGUES.ID, LEAGUES.NAME, LEAGUES.CREATED_AT)
            .from(LEAGUES)
            .orderBy(LEAGUES.NAME)
            .fetch(::toLeague)

    context(ctx: TransactionContext)
    override fun findById(id: LeagueId): League? =
        ctx.dsl.select(LEAGUES.ID, LEAGUES.NAME, LEAGUES.CREATED_AT)
            .from(LEAGUES)
            .where(LEAGUES.ID.eq(id.value))
            .fetchOne(::toLeague)

    context(ctx: TransactionContext)
    override fun create(request: CreateLeagueRequest): League {
        val inserted =
            ctx.dsl.insertInto(LEAGUES)
                .set(LEAGUES.NAME, request.name)
                .returning(LEAGUES.ID, LEAGUES.NAME, LEAGUES.CREATED_AT)
                .fetchOne() ?: error("INSERT RETURNING produced no row for leagues")
        return toLeague(inserted)
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
