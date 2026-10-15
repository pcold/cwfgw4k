package com.cwfgw.golfers

/**
 * Both lookups key on the "first last" concatenation, never on the stored columns, so a row split at either
 * space (old ESPN imports cut at the first) is found the same way. Neither finds a tombstoned row, but [golfers]
 * keeps it: an ESPN id a tombstone holds is still held.
 */
internal class GolferNameIndex(val golfers: List<Golfer>) {
    private val matchable: List<Golfer> = golfers.filter { it.tombstonedAt == null }
    private val byFullName: Map<String, List<Golfer>> = matchable.groupBy { normalizeName(fullName(it)) }
    private val bySurname: Map<String, List<Golfer>> = matchable.groupBy { surnameKey(fullName(it)) }

    fun matchFullName(name: String): GolferNameMatch = lookUp(byFullName, normalizeName(name))

    /** Compares surname keys, so a surname-only ESPN partner row ("Griffin") meets "Ben Griffin". */
    fun matchSurname(name: String): GolferNameMatch = lookUp(bySurname, surnameKey(name))

    // A blank key would match every blank-named row; no name is a reason to match nothing.
    private fun lookUp(
        index: Map<String, List<Golfer>>,
        key: String,
    ): GolferNameMatch = if (key.isEmpty()) GolferNameMatch.NoMatch else GolferNameMatch.among(index[key].orEmpty())
}

private fun fullName(golfer: Golfer): String = "${golfer.firstName} ${golfer.lastName}"

/**
 * Ambiguity is an answer, not an error: ESPN import breaks a surname tie by roster, and roster upload asks the
 * operator.
 */
internal sealed interface GolferNameMatch {
    /** @see GolferNameMatch */
    data class Matched(val golfer: Golfer) : GolferNameMatch

    /** Two or more [candidates]; [of] answers null below that. */
    @ConsistentCopyVisibility
    data class Ambiguous private constructor(val candidates: List<Golfer>) : GolferNameMatch {
        companion object {
            fun of(candidates: List<Golfer>): Ambiguous? = if (candidates.size >= 2) Ambiguous(candidates) else null
        }
    }

    /** @see GolferNameMatch */
    data object NoMatch : GolferNameMatch

    companion object {
        fun among(candidates: List<Golfer>): GolferNameMatch =
            candidates.singleOrNull()?.let(::Matched) ?: Ambiguous.of(candidates) ?: NoMatch
    }
}
