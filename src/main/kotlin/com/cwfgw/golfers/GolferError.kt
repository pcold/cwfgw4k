package com.cwfgw.golfers

/**
 * Typed refusals of a golfer write. Variants stay free of HTTP concerns — routes map them to
 * [com.cwfgw.http.DomainError] at the boundary.
 */
sealed interface GolferError {
    /** Another writer committed this `pga_player_id` first; a find-or-create caller re-reads its row. */
    data object PgaPlayerIdTaken : GolferError
}
