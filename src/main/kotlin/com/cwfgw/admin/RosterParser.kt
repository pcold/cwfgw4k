package com.cwfgw.admin

import com.cwfgw.result.Result

/**
 * Parses a tab-separated roster file into [ParsedTeam] entries — admin
 * tooling for bulk-importing the league's draft results from a copy-paste
 * out of a spreadsheet. Pure: no IO, no DB. The result feeds
 * [AdminService.previewRoster] which matches names to existing golfers.
 *
 * Required format — TSV with the header row exactly as below:
 * ```
 * team_number\tteam_name\tround\tplayer_name\townership_pct
 * 1\tBROWN\t1\tScottie Scheffler\t75
 * 1\tBROWN\t2\tJustin Rose\t
 * 1\tBROWN\t3\tShane Lowry\t100
 * 2\tWOMBLE\t1\tScottie Scheffler\t25
 * ```
 *
 *  - Header is required and validated exactly. Wrong column order or names
 *    fail with a clear message rather than silently misaligning data.
 *  - One row per pick. Multiple rows with the same `team_number` aggregate
 *    into one [ParsedTeam].
 *  - `team_name` matches case-insensitively across rows for the same number;
 *    the parser normalizes to UPPER so downstream lookups can compare
 *    case-insensitively.
 *  - `player_name` is the full name as it appears in the golfers table
 *    ("Scottie Scheffler", "Cameron Young"). The parser normalizes to init
 *    cap so "scottie SCHEFFLER" and "SCOTTIE SCHEFFLER" land identically;
 *    capitalization resets after spaces, dots, hyphens, and apostrophes so
 *    "K.H. Lee" and "Jean-Paul" survive intact. Empty player names fail; the
 *    parser doesn't validate that the format looks like a name — that's
 *    AdminService's job.
 *  - `ownership_pct` is `1`–`100`. Empty cell → 100 (the common case).
 *  - Blank lines are skipped to be forgiving about copy-paste artifacts.
 */
data class ParsedTeam(
    val teamNumber: Int,
    val teamName: String,
    val picks: List<ParsedPick>,
)

data class ParsedPick(
    val round: Int,
    val playerName: String,
    val ownershipPct: Int,
)

sealed interface RosterParseError {
    /** Header missing, malformed, or columns out of order. Stops the whole parse — there's nothing salvageable. */
    data class InvalidHeader(val message: String) : RosterParseError

    /** All bad data rows collected; operators see every problem at once. */
    data class InvalidRows(val errors: List<RowError>) : RosterParseError

    data class RowError(val rowNumber: Int, val message: String)
}

object RosterParser {
    fun parse(text: String): Result<List<ParsedTeam>, RosterParseError> {
        val numberedLines =
            text.lineSequence()
                .mapIndexed { idx, line -> idx + 1 to line }
                .filter { (_, line) -> line.isNotBlank() }
                .toList()
        if (numberedLines.isEmpty()) return Result.Err(RosterParseError.InvalidHeader("Empty input"))

        val (_, headerLine) = numberedLines.first()
        validateHeader(headerLine)?.let { return Result.Err(it) }

        val rows = mutableListOf<ParsedRow>()
        val errors = mutableListOf<RosterParseError.RowError>()
        for ((rowNumber, line) in numberedLines.drop(1)) {
            when (val r = parseRow(line)) {
                is RowResult.Ok -> rows += r.row
                is RowResult.Err -> errors += RosterParseError.RowError(rowNumber, r.message)
            }
        }
        if (errors.isNotEmpty()) return Result.Err(RosterParseError.InvalidRows(errors))

        return aggregateByTeam(rows)
    }

    private fun validateHeader(line: String): RosterParseError.InvalidHeader? {
        val cells = line.split("\t").map(String::trim)
        if (cells != EXPECTED_HEADER) {
            val expected = EXPECTED_HEADER.joinToString("\\t")
            val got = cells.joinToString("\\t")
            return RosterParseError.InvalidHeader("Header row must be exactly: $expected (got: $got)")
        }
        return null
    }

    private fun parseRow(line: String): RowResult {
        val cells = line.split("\t")
        if (cells.size != EXPECTED_HEADER.size) {
            return RowResult.Err("expected ${EXPECTED_HEADER.size} tab-separated cells, got ${cells.size}")
        }
        val teamNumber =
            cells[0].trim().toIntOrNull()
                ?: return RowResult.Err("invalid team_number: '${cells[0]}'")
        val teamName = cells[1].trim().uppercase()
        if (teamName.isEmpty()) return RowResult.Err("team_name is empty")
        val round =
            cells[2].trim().toIntOrNull()
                ?: return RowResult.Err("invalid round: '${cells[2]}'")
        val rawPlayerName = cells[3].trim()
        if (rawPlayerName.isEmpty()) return RowResult.Err("player_name is empty")
        val playerName = rawPlayerName.toInitCap()
        val ownership =
            when (val raw = cells[4].trim()) {
                "" -> MAX_OWNERSHIP
                else ->
                    raw.toIntOrNull()?.takeIf { it in MIN_OWNERSHIP..MAX_OWNERSHIP }
                        ?: return RowResult.Err("ownership_pct must be 1–100 or empty (got: '$raw')")
            }
        return RowResult.Ok(
            ParsedRow(
                teamNumber = teamNumber,
                teamName = teamName,
                pick = ParsedPick(round = round, playerName = playerName, ownershipPct = ownership),
            ),
        )
    }

    /**
     * Group rows by team_number while keeping insertion order. The first row
     * for a given team sets the canonical name; later rows must agree
     * (case-insensitively, since [parseRow] already uppercased).
     */
    private fun aggregateByTeam(rows: List<ParsedRow>): Result<List<ParsedTeam>, RosterParseError> {
        val byTeam = linkedMapOf<Int, ParsedTeam>()
        val errors = mutableListOf<RosterParseError.RowError>()
        for ((rowIndex, row) in rows.withIndex()) {
            val existing = byTeam[row.teamNumber]
            if (existing != null && existing.teamName != row.teamName) {
                // +1 for the header row, +1 because rowIndex is 0-based.
                val rowNumber = rowIndex + 2
                errors +=
                    RosterParseError.RowError(
                        rowNumber = rowNumber,
                        message =
                            "team ${row.teamNumber} name conflict: " +
                                "'${row.teamName}' but earlier rows used '${existing.teamName}'",
                    )
                continue
            }
            byTeam[row.teamNumber] =
                ParsedTeam(
                    teamNumber = row.teamNumber,
                    teamName = row.teamName,
                    picks = (existing?.picks ?: emptyList()) + row.pick,
                )
        }
        return if (errors.isEmpty()) {
            Result.Ok(byTeam.values.toList())
        } else {
            Result.Err(RosterParseError.InvalidRows(errors))
        }
    }

    /** Header columns, in required order. Exposed so test fixtures can build matching rows. */
    val EXPECTED_HEADER: List<String> = listOf("team_number", "team_name", "round", "player_name", "ownership_pct")

    private const val MIN_OWNERSHIP = 1
    private const val MAX_OWNERSHIP = 100
}

/**
 * Normalize a free-text name to init cap. Capitalizes the first letter of
 * each token; tokens are split on space, dot, hyphen, and apostrophe so
 * dotted initials ("K.H. Lee") and hyphenated/apostrophed surnames
 * ("Jean-Paul", "O'Connor") capitalize correctly. Lowercases everything
 * else, so "JUSTIN ROSE" and "justin rose" both land as "Justin Rose".
 */
private fun String.toInitCap(): String {
    val sb = StringBuilder(length)
    var capitalizeNext = true
    for (ch in this) {
        if (ch.isNameTokenBoundary()) {
            sb.append(ch)
            capitalizeNext = true
        } else if (capitalizeNext) {
            sb.append(ch.uppercaseChar())
            capitalizeNext = false
        } else {
            sb.append(ch.lowercaseChar())
        }
    }
    return sb.toString()
}

private fun Char.isNameTokenBoundary(): Boolean = isWhitespace() || this in NAME_PUNCTUATION_BOUNDARIES

private val NAME_PUNCTUATION_BOUNDARIES = setOf('.', '-', '\'')

private data class ParsedRow(
    val teamNumber: Int,
    val teamName: String,
    val pick: ParsedPick,
)

private sealed interface RowResult {
    data class Ok(val row: ParsedRow) : RowResult

    data class Err(val message: String) : RowResult
}
