package com.cwfgw.drafts

import com.cwfgw.teams.TeamId

/**
 * Typed failure modes the draft service surfaces alongside success. Variants
 * stay free of HTTP concerns — routes map them to [com.cwfgw.http.DomainError]
 * at the boundary. Reach for these instead of throwing when multiple distinct
 * failure modes need to drive different caller behavior.
 */
sealed interface DraftError {
    data object NotFound : DraftError

    data class WrongStatus(val current: String, val expected: String) : DraftError

    data object AllPicksMade : DraftError

    data class NotYourTurn(val actualTeam: TeamId, val requestedTeam: TeamId) : DraftError

    data object NoTeams : DraftError
}
