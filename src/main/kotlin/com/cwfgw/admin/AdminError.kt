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
     * concurrent one. The whole roster rolls back. A name the payload repeats is [InvalidConfirmedRoster].
     */
    data class TeamNameTaken(val seasonId: SeasonId) : AdminError

    /** Found before anything is written, every check run so one refusal lists every problem. */
    data class InvalidConfirmedRoster(val problems: List<ConfirmedRosterProblem>) : AdminError
}

/** A confirmRoster payload contradicting itself: checks that need no DB read, each a variant here. */
sealed interface ConfirmedRosterProblem {
    /** Compared exactly, as `UNIQUE (season_id, team_name)` compares: "BROWN" and "Brown" are two teams. */
    data class RepeatedTeamName(val teamName: String) : ConfirmedRosterProblem

    /** Both rows would get one `acquired_at = now()` and collide on `UNIQUE (team_id, golfer_id, acquired_at)`. */
    data class RepeatedExistingGolfer(val teamName: String, val golferId: GolferId) : ConfirmedRosterProblem

    /**
     * Compared by the preview matcher's normalized full name. The DB would take both, as two golfer rows of one
     * name, which leaves every later preview of that name Ambiguous.
     */
    data class RepeatedNewGolfer(
        val teamName: String,
        val firstName: String,
        val lastName: String,
    ) : ConfirmedRosterProblem

    /** One `espnId` resolves to one golfer row, so both picks collide as [RepeatedExistingGolfer]'s do. */
    data class RepeatedEspnGolfer(val teamName: String, val espnId: String) : ConfirmedRosterProblem

    /** No surname to store. The preview never offers such an athlete, so only a hand-built payload carries one. */
    data class BlankEspnGolferName(val teamName: String, val espnId: String) : ConfirmedRosterProblem

    /** One per round however many picks name it, so it is never also a [RepeatedRound]. */
    data class RoundOutOfRange(val teamName: String, val round: Int) : ConfirmedRosterProblem

    /** @see DRAFT_ROUNDS */
    data class RepeatedRound(val teamName: String, val round: Int) : ConfirmedRosterProblem

    /** Ascending, every absent round of the team in one problem rather than one each. */
    data class MissingRounds(val teamName: String, val rounds: List<Int>) : ConfirmedRosterProblem
}
