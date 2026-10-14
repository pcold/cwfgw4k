package com.cwfgw.golfers

/** Variants stay free of HTTP concerns — routes map them to [com.cwfgw.http.DomainError] at the boundary. */
sealed interface GolferError {
    /**
     * Another writer committed this `pga_player_id` first. The ESPN pool fill ignores it, since its re-list finds
     * the winner.
     */
    data object PgaPlayerIdTaken : GolferError
}
