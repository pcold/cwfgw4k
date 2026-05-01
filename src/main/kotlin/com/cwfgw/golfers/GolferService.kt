package com.cwfgw.golfers

import com.cwfgw.db.Transactor

class GolferService(
    private val repository: GolferRepository,
    private val tx: Transactor,
) {
    suspend fun list(
        activeOnly: Boolean,
        search: String?,
    ): List<Golfer> = tx.read { repository.findAll(activeOnly, search) }

    suspend fun get(id: GolferId): Golfer? = tx.read { repository.findById(id) }

    suspend fun findByPgaPlayerId(pgaPlayerId: String): Golfer? =
        tx.read { repository.findByPgaPlayerId(pgaPlayerId) }

    suspend fun create(request: CreateGolferRequest): Golfer = tx.update { repository.create(request) }

    suspend fun update(
        id: GolferId,
        request: UpdateGolferRequest,
    ): Golfer? = tx.update { repository.update(id, request) }
}
