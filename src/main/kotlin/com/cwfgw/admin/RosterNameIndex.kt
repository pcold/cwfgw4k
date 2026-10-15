package com.cwfgw.admin

import com.cwfgw.espn.EspnAthlete
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferNameIndex
import com.cwfgw.golfers.GolferNameMatch
import com.cwfgw.golfers.normalizeName

/**
 * One pool, not ESPN as a fallback after the rows: a name both carry stays Ambiguous for the operator, since the row
 * may be that athlete entered by hand or a namesake. An athlete with a blank name is dropped; confirm refuses one.
 */
internal class RosterNameIndex(
    private val golfers: GolferNameIndex,
    athletes: List<EspnAthlete>,
) {
    private val athletesByName: Map<String, List<EspnAthlete>> =
        athletes.groupBy { normalizeName(it.name) }.filterKeys { it.isNotEmpty() }

    private val golfersByEspnId: Map<String, Golfer> =
        golfers.golfers.mapNotNull { golfer -> golfer.pgaPlayerId?.let { it to golfer } }.toMap()

    fun match(playerName: String): PickMatch {
        val byName = golfers.matchFullName(playerName).golfers()
        val athletes = athletesByName[normalizeName(playerName)].orEmpty()
        // An athlete whose row is spelled otherwise ("Matt" for ESPN's "Matthew") binds to that row; offering it as
        // new would have confirm insert a second golfer that imports never credit.
        val heldRows = athletes.mapNotNull { golfersByEspnId[it.espnId] }
        val unpersisted =
            athletes.filterNot { it.espnId in golfersByEspnId }.map { athlete ->
                GolferCandidate.Espn(espnId = athlete.espnId, name = athlete.name)
            }
        return pickMatchAmong((byName + heldRows).distinctBy(Golfer::id).map(::existingCandidate) + unpersisted)
    }
}

private fun GolferNameMatch.golfers(): List<Golfer> =
    when (this) {
        is GolferNameMatch.Matched -> listOf(golfer)
        is GolferNameMatch.Ambiguous -> candidates
        GolferNameMatch.NoMatch -> emptyList()
    }

private fun existingCandidate(golfer: Golfer): GolferCandidate.Existing =
    GolferCandidate.Existing(golferId = golfer.id, name = "${golfer.firstName} ${golfer.lastName}")

private fun pickMatchAmong(candidates: List<GolferCandidate>): PickMatch =
    when (val only = candidates.singleOrNull()) {
        is GolferCandidate.Existing -> PickMatch.Matched(golferId = only.golferId, golferName = only.name)
        is GolferCandidate.Espn -> PickMatch.MatchedEspn(espnId = only.espnId, golferName = only.name)
        null -> if (candidates.isEmpty()) PickMatch.NoMatch else PickMatch.Ambiguous(candidates)
    }
