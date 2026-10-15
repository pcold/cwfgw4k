package com.cwfgw.datahealth

import com.cwfgw.db.Transactor

/** @see com.cwfgw.AppServices.from */
internal class DataHealthSupport private constructor(
    val dataHealthService: DataHealthService,
) {
    companion object {
        fun from(
            dataHealthRepository: DataHealthRepository,
            transactor: Transactor,
        ): DataHealthSupport =
            DataHealthSupport(dataHealthService = DataHealthService(dataHealthRepository, transactor))
    }
}
