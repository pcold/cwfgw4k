package com.cwfgw.golfers

interface GolferRepository {
    suspend fun findAll(
        activeOnly: Boolean,
        search: String?,
    ): List<Golfer>

    suspend fun findById(id: GolferId): Golfer?

    suspend fun create(request: CreateGolferRequest): Golfer

    suspend fun update(
        id: GolferId,
        request: UpdateGolferRequest,
    ): Golfer?
}
