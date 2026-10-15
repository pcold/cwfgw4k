package com.cwfgw.admin

/**
 * Fills each of [DRAFT_ROUNDS] that [picks] leaves empty with a new golfer named for the team and the round, so a
 * fixture states only the picks it is about and still uploads a roster confirm accepts.
 */
internal fun fullTeam(
    number: Int,
    name: String,
    vararg picks: ConfirmedPick,
): ConfirmedTeam {
    val pickedRounds = picks.map(ConfirmedPick::round).toSet()
    val fillers =
        DRAFT_ROUNDS.filterNot { it in pickedRounds }.map { round ->
            val filler = GolferAssignment.New(name = "Filler $name-$round")
            ConfirmedPick(round = round, ownershipPct = 100, assignment = filler)
        }
    return ConfirmedTeam(teamNumber = number, teamName = name, picks = picks.toList() + fillers)
}
