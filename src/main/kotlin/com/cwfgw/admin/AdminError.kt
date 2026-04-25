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

    /**
     * The roster TSV failed to parse. Carries the structured parser error
     * so the route layer can flatten it into a useful 400 response without
     * losing per-row context.
     */
    data class InvalidRoster(val parseError: RosterParseError) : AdminError
}
