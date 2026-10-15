package com.cwfgw.tournaments

/** @see com.cwfgw.db.updateOrOutraced */
sealed interface TournamentError {
    /**
     * Another confirm committed this `pga_tournament_id` first; the schedule confirm re-reads it in a new
     * transaction and skips the entry.
     */
    data object PgaTournamentIdTaken : TournamentError
}
