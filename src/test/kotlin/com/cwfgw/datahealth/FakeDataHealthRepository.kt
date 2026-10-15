package com.cwfgw.datahealth

import com.cwfgw.db.TransactionContext

class FakeDataHealthRepository(
    private val rosteredTwins: List<RosteredTwinGroup> = emptyList(),
    private val rosteredWithoutEspnId: List<DataHealthGolfer> = emptyList(),
    private val blankFirstNames: List<DataHealthGolfer> = emptyList(),
) : DataHealthRepository {
    context(ctx: TransactionContext)
    override fun findRosteredTwins(): List<RosteredTwinGroup> = rosteredTwins

    context(ctx: TransactionContext)
    override fun findRosteredWithoutEspnId(): List<DataHealthGolfer> = rosteredWithoutEspnId

    context(ctx: TransactionContext)
    override fun findBlankFirstNames(): List<DataHealthGolfer> = blankFirstNames
}
