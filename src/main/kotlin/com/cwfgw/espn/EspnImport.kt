package com.cwfgw.espn

import com.cwfgw.tournaments.TournamentId
import kotlinx.serialization.Serializable

/**
 * Summary of a single tournament's ESPN import — one per tournament the
 * client processed, regardless of whether matching produced any new golfers
 * or unmatched competitors. Returned over the wire so operators can see
 * what happened end-to-end.
 */
@Serializable
data class EspnImport(
    val tournamentId: TournamentId,
    val espnEventId: String,
    val espnEventName: String,
    val completed: Boolean,
    val matched: Int,
    val created: Int,
    val unmatched: List<String>,
    val collisions: List<String>,
)

/**
 * Batch result of a date-scoped import. ESPN can return several events on
 * one date (multiple tours); any event whose `pga_tournament_id` isn't
 * linked to a tournament in our DB lands in [unlinked] rather than failing
 * the whole batch, so one missing linkage doesn't block the rest.
 */
@Serializable
data class EspnImportBatch(
    val imported: List<EspnImport>,
    val unlinked: List<UnlinkedEvent>,
)

@Serializable
data class UnlinkedEvent(
    val espnEventId: String,
    val espnEventName: String,
)
