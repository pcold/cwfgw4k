package com.cwfgw.datahealth

import com.cwfgw.db.Transactor

class DataHealthService(
    private val repository: DataHealthRepository,
    private val tx: Transactor,
) {
    /** One snapshot, so a golfer fixed between two checks cannot appear in one and vanish from the other. */
    suspend fun report(): DataHealthReport =
        tx.read {
            DataHealthReport(
                rosteredTwins = DataHealthCheck(repository.findRosteredTwins()),
                rosteredWithoutEspnId = DataHealthCheck(repository.findRosteredWithoutEspnId()),
                blankFirstNames = DataHealthCheck(repository.findBlankFirstNames()),
            )
        }
}
