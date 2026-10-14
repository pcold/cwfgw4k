package com.cwfgw.golfers

import com.cwfgw.db.Transactor

class GolferService(
    private val repository: GolferRepository,
    private val tx: Transactor,
) {
    suspend fun list(search: String?): List<Golfer> = tx.get { repository.findAll(search) }

    suspend fun get(id: GolferId): Golfer? = tx.get { repository.findById(id) }

    suspend fun create(request: CreateGolferRequest): Golfer = tx.update { repository.create(request) }
}
