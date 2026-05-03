package com.cwfgw.golfers

import com.cwfgw.db.Transactor

class GolferService(
    private val repository: GolferRepository,
    private val tx: Transactor,
) {
    suspend fun list(
        activeOnly: Boolean,
        search: String?,
    ): List<Golfer> = tx.get { repository.findAll(activeOnly, search) }

    suspend fun get(id: GolferId): Golfer? = tx.get { repository.findById(id) }

    suspend fun findByPgaPlayerId(pgaPlayerId: String): Golfer? =
        tx.get { repository.findByPgaPlayerId(pgaPlayerId) }

    suspend fun create(request: CreateGolferRequest): Golfer = tx.update { repository.create(request) }

    suspend fun update(
        id: GolferId,
        request: UpdateGolferRequest,
    ): Golfer? = tx.update { repository.update(id, request) }
}
