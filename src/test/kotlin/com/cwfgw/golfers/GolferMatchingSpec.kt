package com.cwfgw.golfers

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldNotBeBlank
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.element
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.time.Instant
import java.util.UUID

// Letters, case pairs, every folded letter, a combining accent, hyphens and several kinds of space.
private const val NAME_ALPHABET = "aAzZ øØåÅæÆœŒßẞłŁđĐðÐþÞéÉñÑüÜǿİı́-\t  ."

private fun arbNameLike(): Arb<String> = Arb.string(0..24, Arb.element(NAME_ALPHABET.map { Codepoint(it.code) }))

private fun arbBmp(): Arb<String> =
    Arb.string(0..24, Arb.int(0..0xFFFF).filter { !Character.isSurrogate(it.toChar()) }.map(::Codepoint))

private fun golfer(
    firstName: String,
    lastName: String,
): Golfer =
    Golfer(
        id = GolferId(UUID.randomUUID()),
        pgaPlayerId = null,
        firstName = firstName,
        lastName = lastName,
        country = null,
        worldRanking = null,
        updatedAt = Instant.EPOCH,
    )

private fun GolferName.joined(): String = "$firstName $lastName"

class GolferMatchingSpec : FunSpec({

    context("normalizeName folds what a roster and ESPN spell differently") {
        withData(
            "Byeong-Hun An" to "byeong hun an",
            "Thorbjørn Olesen" to "thorbjorn olesen",
            "Ludvig Åberg" to "ludvig aberg",
            "  niklas  NØRGAARD-petersen " to "niklas norgaard petersen",
            "Nicolás Echavarría" to "nicolas echavarria",
        ) { (raw, normalized) ->
            normalizeName(raw) shouldBe normalized
        }
    }

    test("normalizeName never makes neighbouring first names equal") {
        normalizeName("Tom Kim") shouldNotBe normalizeName("Tim Kim")
    }

    test("normalizeName is idempotent over name-shaped and arbitrary text") {
        checkAll(arbNameLike()) { name -> normalizeName(normalizeName(name)) shouldBe normalizeName(name) }
        checkAll(arbBmp()) { name -> normalizeName(normalizeName(name)) shouldBe normalizeName(name) }
    }

    context("splitName cuts at the last space and keeps a single word as the surname") {
        withData(
            "Byeong Hun An" to ("Byeong Hun" to "An"),
            "Brandon Robinson Thompson" to ("Brandon Robinson" to "Thompson"),
            "Erik van Rooyen" to ("Erik van" to "Rooyen"),
            "  Min   Woo Lee " to ("Min Woo" to "Lee"),
            "Griffin" to ("" to "Griffin"),
        ) { (fullName, expected) ->
            GolferName.splitName(fullName)?.let { it.firstName to it.lastName } shouldBe expected
        }
    }

    test("splitName refuses a blank name") {
        GolferName.splitName(" \t ") shouldBe null
    }

    test("splitName round-trips, never leaves a blank surname, and never changes what a name matches") {
        checkAll(arbNameLike()) { name ->
            val split = GolferName.splitName(name) ?: return@checkAll
            split.lastName.shouldNotBeBlank()
            GolferName.splitName(split.joined()) shouldBe split
            normalizeName(split.joined()) shouldBe normalizeName(name)
        }
    }

    test("a full name matches one golfer however either side spells it") {
        val an = golfer("Byeong Hun", "An")
        val index = GolferNameIndex(listOf(an, golfer("Kevin", "An")))

        index.matchFullName("byeong-hun  AN") shouldBe GolferNameMatch.Matched(an)
    }

    test("a full name two golfers share is ambiguous between exactly those two") {
        val first = golfer("Brandon Robinson", "Thompson")
        val second = golfer("Brandon", "Robinson Thompson")
        val index = GolferNameIndex(listOf(first, second, golfer("Brandon", "Thompson")))

        val match = index.matchFullName("Brandon Robinson Thompson").shouldBeInstanceOf<GolferNameMatch.Ambiguous>()
        match.candidates.toSet() shouldBe setOf(first, second)
    }

    test("a surname finds a golfer whichever space his stored name was split at") {
        val firstSpaceSplit = golfer("Byeong", "Hun An")
        val firstNameOnly = golfer("Griffin", "")
        val lastNameOnly = golfer("", "Novak")
        val index = GolferNameIndex(listOf(firstSpaceSplit, firstNameOnly, lastNameOnly))

        index.matchSurname("An") shouldBe GolferNameMatch.Matched(firstSpaceSplit)
        index.matchSurname("Griffin") shouldBe GolferNameMatch.Matched(firstNameOnly)
        index.matchSurname("Novák") shouldBe GolferNameMatch.Matched(lastNameOnly)
    }

    test("a surname-only row is found by its bare surname, with no stray space from the blank first name") {
        val griffin = golfer("", "Griffin")
        val index = GolferNameIndex(listOf(griffin, golfer("Ben", "Griffin")))

        index.matchFullName("Griffin") shouldBe GolferNameMatch.Matched(griffin)
        index.matchFullName(" Griffin") shouldBe GolferNameMatch.Matched(griffin)
        index.matchSurname("Griffin").shouldBeInstanceOf<GolferNameMatch.Ambiguous>().candidates shouldContain griffin
    }

    context("surnameKey keeps a hyphenated surname whole and looks past a generational suffix") {
        withData(
            "Niklas Nørgaard-Petersen" to "norgaard-petersen",
            "Harold Varner III" to "varner",
            "Davis Love iii" to "love",
            "Sam Burns Jr." to "burns",
            "III" to "iii",
            "Griffin" to "griffin",
        ) { (name, key) ->
            surnameKey(name) shouldBe key
        }
    }

    test("a lone second half or suffix is not the unique surname of a longer one") {
        val norgaardPetersen = golfer("Niklas", "Nørgaard-Petersen")
        val varner = golfer("Harold", "Varner III")
        val index = GolferNameIndex(listOf(norgaardPetersen, varner))

        index.matchSurname("Mark Petersen") shouldBe GolferNameMatch.NoMatch
        index.matchSurname("Davis Love III") shouldBe GolferNameMatch.NoMatch
        index.matchSurname("Norgaard-Petersen") shouldBe GolferNameMatch.Matched(norgaardPetersen)
        index.matchSurname("Varner") shouldBe GolferNameMatch.Matched(varner)
    }

    test("a blank name matches nothing, not a blank-named row") {
        val index = GolferNameIndex(listOf(golfer("", "")))

        index.matchFullName(" ") shouldBe GolferNameMatch.NoMatch
        index.matchSurname("") shouldBe GolferNameMatch.NoMatch
    }

    test("Ambiguous refuses fewer than two candidates") {
        GolferNameMatch.Ambiguous.of(emptyList()) shouldBe null
        GolferNameMatch.Ambiguous.of(listOf(golfer("Ben", "Griffin"))) shouldBe null
    }
})
