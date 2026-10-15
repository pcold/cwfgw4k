package com.cwfgw.tournaments

import com.cwfgw.espn.EspnImportWarnings
import kotlinx.serialization.Serializable

/**
 * [importWarnings] holds one entry per tournament the import wrote into that has any, so an unlinked finalize reports
 * each match it imported; a clean finalize answers an empty list.
 */
@Serializable
data class FinalizedTournament(
    val tournament: Tournament,
    val importWarnings: List<EspnImportWarnings>,
)
