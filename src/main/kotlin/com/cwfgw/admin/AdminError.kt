package com.cwfgw.admin

import com.cwfgw.seasons.SeasonId

/**
 * Typed failure modes the admin service surfaces alongside success. Variants
 * stay free of HTTP concerns — routes map them to
 * [com.cwfgw.http.DomainError] at the boundary.
 */
sealed interface AdminError {
    /** The given season id doesn't exist in the DB. */
    data class SeasonNotFound(val seasonId: SeasonId) : AdminError

    /** ESPN returned a non-2xx response when we tried to fetch the calendar. */
    data class UpstreamUnavailable(val status: Int) : AdminError
}
