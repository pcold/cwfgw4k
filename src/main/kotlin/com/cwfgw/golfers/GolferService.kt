package com.cwfgw.golfers

import com.cwfgw.db.Transactor

class GolferService(
    private val repository: GolferRepository,
    private val tx: Transactor,
) {
    /** Keeps the tombstones, which name matching must see to skip; a picker reads [listLinkable]. */
    suspend fun list(search: String?): List<Golfer> = tx.get { repository.findAll(search) }

    suspend fun listLinkable(search: String?): List<Golfer> = tx.get { repository.findLinkable(search) }

    suspend fun get(id: GolferId): Golfer? = tx.get { repository.findById(id) }
}
