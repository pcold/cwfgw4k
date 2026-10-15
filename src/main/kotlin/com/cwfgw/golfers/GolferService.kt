package com.cwfgw.golfers

import com.cwfgw.db.Transactor
import com.cwfgw.db.updateOrOutraced
import com.cwfgw.result.Result

class GolferService(
    private val repository: GolferRepository,
    private val tx: Transactor,
) {
    suspend fun list(search: String?): List<Golfer> = tx.get { repository.findAll(search) }

    suspend fun get(id: GolferId): Golfer? = tx.get { repository.findById(id) }

    suspend fun create(request: CreateGolferRequest): Result<Golfer, GolferError> =
        tx.updateOrOutraced(UNIQUE_PGA_PLAYER_ID, outraced = { GolferError.PgaPlayerIdTaken }) {
            Result.Ok(repository.create(request))
        }
}
