package com.cwfgw.golfers

import com.cwfgw.db.Transactor

/** @see com.cwfgw.AppServices.from */
class GolfersSupport private constructor(
    val golferService: GolferService,
) {
    companion object {
        fun from(
            golferRepository: GolferRepository,
            transactor: Transactor,
        ): GolfersSupport = GolfersSupport(golferService = GolferService(golferRepository, transactor))
    }
}
