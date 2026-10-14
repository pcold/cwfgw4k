package com.cwfgw.users

/** @see com.cwfgw.golfers.GolferError */
sealed interface UserError {
    /** Another writer committed this username first — at boot, a second instance seeding the same admin. */
    data object UsernameTaken : UserError
}
