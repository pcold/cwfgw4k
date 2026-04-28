package com.cwfgw.golfers

import org.jooq.DSLContext

class GolferService(private val repository: GolferRepository) {
    suspend fun list(
        activeOnly: Boolean,
        search: String?,
    ): List<Golfer> = repository.findAll(activeOnly, search)

    suspend fun get(id: GolferId): Golfer? = repository.findById(id)

    suspend fun findByPgaPlayerId(pgaPlayerId: String): Golfer? = repository.findByPgaPlayerId(pgaPlayerId)

    suspend fun create(
        request: CreateGolferRequest,
        dsl: DSLContext? = null,
    ): Golfer = repository.create(request, dsl)

    suspend fun update(
        id: GolferId,
        request: UpdateGolferRequest,
    ): Golfer? = repository.update(id, request)
}
