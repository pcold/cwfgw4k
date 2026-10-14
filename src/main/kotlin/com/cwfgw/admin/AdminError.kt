package com.cwfgw.admin

import com.cwfgw.espn.EspnUpstreamFailure
import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId

/**
 * Typed failure modes the admin service surfaces alongside success. Variants
 * stay free of HTTP concerns — routes map them to
 * [com.cwfgw.http.DomainError] at the boundary.
 */
sealed interface AdminError {
    /** The given season id doesn't exist in the DB. */
    data class SeasonNotFound(val seasonId: SeasonId) : AdminError

    /** ESPN returned a non-2xx response, or none, when we tried to fetch the calendar. */
    data class UpstreamUnavailable(val failure: EspnUpstreamFailure) : AdminError

    /**
     * The roster TSV failed to parse. Carries the structured parser error
     * so the route layer can flatten it into a useful 400 response without
     * losing per-row context.
     */
    data class InvalidRoster(val parseError: RosterParseError) : AdminError

    /**
     * One or more `GolferAssignment.Existing` entries in a confirmRoster
     * payload reference golfer ids that aren't in the DB. All bad ids are
     * collected so the operator sees every problem at once instead of
     * one-fix-then-retry.
     */
    data class GolferIdsNotFound(val ids: List<GolferId>) : AdminError

    /**
     * A team name in a confirmRoster payload is already taken in the season, by an earlier upload or a
     * concurrent one. The whole roster rolls back, so the operator renames and resubmits all of it.
     */
    data class TeamNameTaken(val seasonId: SeasonId) : AdminError
}
