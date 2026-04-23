package com.cwfgw.scoring

/**
 * Typed failure modes the scoring service surfaces alongside success. Variants
 * stay free of HTTP concerns — routes map them to [com.cwfgw.http.DomainError]
 * at the boundary.
 */
sealed interface ScoringError {
    data object SeasonNotFound : ScoringError

    data object TournamentNotFound : ScoringError

    data object NoTeams : ScoringError
}
