package com.cwfgw.tournaments

import com.cwfgw.espn.EspnImportWarnings
import kotlinx.serialization.Serializable

/**
 * [importWarnings] holds one entry per tournament the import wrote into, a clean one included, so an empty list means
 * no ESPN event was imported: a linked event missing from the scoreboard, or an unlinked finalize with no match.
 */
@Serializable
data class FinalizedTournament(
    val tournament: Tournament,
    val importWarnings: List<EspnImportWarnings>,
)
