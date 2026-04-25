package com.cwfgw.admin

import com.cwfgw.result.Result
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf

private val HEADER = RosterParser.EXPECTED_HEADER.joinToString("\t")

private fun rosterText(vararg rows: String): String = (listOf(HEADER) + rows.toList()).joinToString("\n")

private fun ok(text: String): List<ParsedTeam> =
    RosterParser.parse(text).shouldBeInstanceOf<Result.Ok<List<ParsedTeam>>>().value

private fun rowErrors(text: String): List<RosterParseError.RowError> =
    RosterParser.parse(text)
        .shouldBeInstanceOf<Result.Err<RosterParseError>>()
        .error
        .shouldBeInstanceOf<RosterParseError.InvalidRows>()
        .errors

private fun headerError(text: String): RosterParseError.InvalidHeader =
    RosterParser.parse(text)
        .shouldBeInstanceOf<Result.Err<RosterParseError>>()
        .error
        .shouldBeInstanceOf<RosterParseError.InvalidHeader>()

class RosterParserSpec : FunSpec({

    // ----- happy path -----

    test("a single team with explicit ownership parses to one team and three picks") {
        val text =
            rosterText(
                "1\tBROWN\t1\tScottie Scheffler\t75",
                "1\tBROWN\t2\tJustin Rose\t100",
                "1\tBROWN\t3\tShane Lowry\t100",
            )

        val teams = ok(text)
        teams shouldHaveSize 1
        teams.single().teamNumber shouldBe 1
        teams.single().teamName shouldBe "BROWN"
        teams.single().picks shouldHaveSize 3
    }

    test("a pick parses round, full player name, and ownership pct") {
        val pick = ok(rosterText("1\tBROWN\t1\tScottie Scheffler\t75")).single().picks.single()

        pick.round shouldBe 1
        pick.playerName shouldBe "Scottie Scheffler"
        pick.ownershipPct shouldBe 75
    }

    test("empty ownership_pct cell defaults to 100 — the common case shouldn't require typing 100") {
        val pick = ok(rosterText("1\tBROWN\t1\tShane Lowry\t")).single().picks.single()

        pick.ownershipPct shouldBe 100
    }

    test("multiple rows for the same team_number aggregate into one ParsedTeam in input order") {
        val text =
            rosterText(
                "1\tBROWN\t1\tScottie Scheffler\t75",
                "1\tBROWN\t2\tJustin Rose\t",
                "1\tBROWN\t3\tShane Lowry\t",
            )

        ok(text).single().picks.map { it.playerName } shouldBe
            listOf("Scottie Scheffler", "Justin Rose", "Shane Lowry")
    }

    test("multiple teams parse and preserve team-input order") {
        val text =
            rosterText(
                "1\tBROWN\t1\tScottie Scheffler\t75",
                "2\tWOMBLE\t1\tScottie Scheffler\t25",
                "1\tBROWN\t2\tJustin Rose\t",
            )

        val teams = ok(text)
        teams.map { it.teamNumber } shouldBe listOf(1, 2)
        teams[0].picks shouldHaveSize 2
        teams[1].picks.single().ownershipPct shouldBe 25
    }

    // ----- team name normalization -----

    test("team_name is normalized to UPPER regardless of how the operator typed it") {
        val text =
            rosterText(
                "1\tbrown\t1\tScottie Scheffler\t75",
                "1\tBrown\t2\tJustin Rose\t",
            )

        ok(text).single().teamName shouldBe "BROWN"
    }

    test("a team_name that doesn't match earlier rows for the same team_number is reported as a row error") {
        val text =
            rosterText(
                "1\tBROWN\t1\tScottie Scheffler\t75",
                "1\tWILSON\t2\tJustin Rose\t",
            )

        val error = rowErrors(text).single()
        error.message.shouldContain("name conflict")
        error.message.shouldContain("BROWN")
        error.message.shouldContain("WILSON")
    }

    // ----- blank-line tolerance + multi-word names -----

    test("blank lines between rows are silently skipped") {
        val text =
            HEADER +
                "\n\n1\tBROWN\t1\tScottie Scheffler\t75\n\n1\tBROWN\t2\tJustin Rose\t\n"

        ok(text).single().picks shouldHaveSize 2
    }

    test("multi-word player names round-trip when already init-capped") {
        ok(rosterText("1\tBROWN\t1\tSi Woo Kim\t")).single().picks.single().playerName shouldBe "Si Woo Kim"
    }

    test("player_name is normalized to init cap regardless of how the operator typed it") {
        val text =
            rosterText(
                "1\tBROWN\t1\tscottie SCHEFFLER\t75",
                "1\tBROWN\t2\tJUSTIN ROSE\t",
                "1\tBROWN\t3\tshane lowry\t",
            )

        ok(text).single().picks.map { it.playerName } shouldBe
            listOf("Scottie Scheffler", "Justin Rose", "Shane Lowry")
    }

    test("init cap preserves dotted initials, hyphens, and apostrophes") {
        val text =
            rosterText(
                "1\tBROWN\t1\tk.h. lee\t",
                "1\tBROWN\t2\tJEAN-PAUL gaultier\t",
                "1\tBROWN\t3\tjohn o'connor\t",
            )

        ok(text).single().picks.map { it.playerName } shouldBe
            listOf("K.H. Lee", "Jean-Paul Gaultier", "John O'Connor")
    }

    // ----- header errors -----

    test("missing header row returns InvalidHeader") {
        headerError("").message.shouldContain("Empty input")
    }

    test("header with wrong column order returns InvalidHeader (no rows attempted)") {
        val text =
            "team_name\tteam_number\tround\tplayer_name\townership_pct\n" +
                "BROWN\t1\t1\tScottie Scheffler\t100\n"
        headerError(text).message.shouldContain("Header row must be exactly")
    }

    test("header with a typo'd column returns InvalidHeader") {
        val text = "team_number\tteam_name\tround\tplayer\townership_pct\n"
        headerError(text).message.shouldContain("player_name")
    }

    // ----- row errors -----

    test("non-numeric team_number returns a row error referencing the bad cell") {
        val errors = rowErrors(rosterText("ONE\tBROWN\t1\tScottie Scheffler\t75"))
        errors.single().message.shouldContain("invalid team_number")
    }

    test("empty player_name returns a row error") {
        val errors = rowErrors(rosterText("1\tBROWN\t1\t\t75"))
        errors.single().message.shouldContain("player_name is empty")
    }

    test("empty team_name returns a row error") {
        val errors = rowErrors(rosterText("1\t\t1\tScottie Scheffler\t75"))
        errors.single().message.shouldContain("team_name is empty")
    }

    test("non-numeric round returns a row error referencing the bad cell") {
        val errors = rowErrors(rosterText("1\tBROWN\tNINE\tScottie Scheffler\t75"))
        errors.single().message.shouldContain("invalid round")
        errors.single().message.shouldContain("NINE")
    }

    test("ownership_pct out of range returns a row error and the message names the bad value") {
        val errors = rowErrors(rosterText("1\tBROWN\t1\tScottie Scheffler\t150"))
        errors.single().message.shouldContain("1–100")
        errors.single().message.shouldContain("150")
    }

    test("a row with the wrong number of cells fails with the column count in the message") {
        val errors = rowErrors(rosterText("1\tBROWN\t1\tScottie Scheffler"))
        errors.single().message.shouldContain("expected 5")
    }

    test("multiple bad rows all surface in one error response (not one-by-one)") {
        val text =
            rosterText(
                "1\tBROWN\t1\tScottie Scheffler\t150",
                "2\tWOMBLE\tNINE\tCameron Young\t100",
            )

        val errors = rowErrors(text)
        errors shouldHaveSize 2
        errors[0].rowNumber shouldBe 2
        errors[1].rowNumber shouldBe 3
    }
})
