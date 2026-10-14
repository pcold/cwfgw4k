package com.cwfgw.leagues

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.required
import com.cwfgw.jooq.tables.records.LeaguesRecord
import com.cwfgw.jooq.tables.references.LEAGUES

interface LeagueRepository {
    context(ctx: TransactionContext)
    fun findAll(): List<League>

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
            .fetch { row -> toLeague(row.into(LEAGUES)) }

    context(ctx: TransactionContext)
    override fun create(request: CreateLeagueRequest): League {
        val inserted =
            ctx.dsl.insertInto(LEAGUES)
                .set(LEAGUES.NAME, request.name)
                .returning(LEAGUES.ID, LEAGUES.NAME, LEAGUES.CREATED_AT)
                .fetchSingle()
        return toLeague(inserted)
    }

    private fun toLeague(record: LeaguesRecord): League =
        League(
            id = LeagueId(record.required(LEAGUES.ID)),
            name = record.name,
            createdAt = record.required(LEAGUES.CREATED_AT).toInstant(),
        )
}
