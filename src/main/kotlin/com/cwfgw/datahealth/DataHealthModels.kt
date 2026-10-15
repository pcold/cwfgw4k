package com.cwfgw.datahealth

import com.cwfgw.golfers.GolferId
import kotlinx.serialization.Serializable

/** [pgaPlayerId] is the ESPN id that scoring resolves a competitor by before it tries the name. */
@Serializable
data class DataHealthGolfer(
    val id: GolferId,
    val firstName: String,
    val lastName: String,
    val pgaPlayerId: String?,
)

/**
 * ESPN's results resolve to a golfer in [holdingEspnId] by id, so the rostered one in [rosteredWithoutEspnId]
 * scores nothing. A twin with neither a roster spot nor an ESPN id takes nothing from it and is left out.
 */
@Serializable
data class RosteredTwinGroup(
    val normalizedName: String,
    val rosteredWithoutEspnId: List<DataHealthGolfer>,
    val holdingEspnId: List<DataHealthGolfer>,
)

/** [count] is derived rather than passed so it cannot disagree with [rows]; a body property is still encoded. */
@Serializable
data class DataHealthCheck<T>(
    val rows: List<T>,
) {
    val count: Int = rows.size
}

/** @see DataHealthRepository for what each check reads. */
@Serializable
data class DataHealthReport(
    val rosteredTwins: DataHealthCheck<RosteredTwinGroup>,
    val rosteredWithoutEspnId: DataHealthCheck<DataHealthGolfer>,
    val blankFirstNames: DataHealthCheck<DataHealthGolfer>,
)
