package com.cwfgw.tournaments

/** @see com.cwfgw.golfers.GolferError */
sealed interface TournamentError {
    /** @see com.cwfgw.golfers.GolferError.PgaPlayerIdTaken */
    data object PgaTournamentIdTaken : TournamentError
}
