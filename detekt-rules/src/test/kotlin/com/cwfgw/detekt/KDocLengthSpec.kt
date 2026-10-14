package com.cwfgw.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.test.lint
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private fun findingsIn(code: String): List<Finding> = KDocLength(Config.empty).lint(code.trimIndent())

private fun kdoc(lines: Int): String = (1..lines).joinToString("\n", "/**\n", "\n */") { index -> " * line $index" }

private val OVER_BUDGET: String = kdoc(7)

class KDocLengthSpec :
    FunSpec({
        test("the budget is six lines") {
            MAX_KDOC_LINES shouldBe 6
        }

        test("a KDoc at the budget passes and one line over is reported") {
            findingsIn("${kdoc(6)}\nfun probe() = Unit").shouldBeEmpty()
            findingsIn("$OVER_BUDGET\nfun probe() = Unit").single().message shouldContain "runs to 7 lines"
        }

        test("delimiters and blank lines are not counted") {
            val spaced = (1..6).joinToString("\n *\n", "/**\n", "\n */") { index -> " * line $index" }
            findingsIn("$spaced\nfun probe() = Unit").shouldBeEmpty()
        }

        test("tag lines are counted") {
            findingsIn("${kdoc(6)}\n".replace(" */\n", " * @see Other\n */\n") + "fun probe() = Unit").size shouldBe 1
        }

        test("a KDoc on a private member or inside a body is held to the same budget") {
            findingsIn(
                """
                class Probe {
                    $OVER_BUDGET
                    private val hidden = 1

                    fun run() {
                        $OVER_BUDGET
                        val local = 2
                    }
                }
                """,
            ).size shouldBe 2
        }

        test("a block comment and a line-comment run of the same length are not KDoc") {
            val block = (1..7).joinToString("\n", "/*\n", "\n */") { index -> " * line $index" }
            val run = (1..7).joinToString("\n") { index -> "// line $index" }
            findingsIn("$block\nfun first() = Unit\n\n$run\nfun second() = Unit").shouldBeEmpty()
        }

        test("a waiver with a reason trailing it silences the finding") {
            findingsIn(
                """
                $OVER_BUDGET
                @Suppress("KDocLength") // Three constraints, each one a caller has broken.
                fun probe() = Unit
                """,
            ).shouldBeEmpty()
        }

        test("a waiver without a reason is reported even though it names the rule it silences") {
            findingsIn(
                """
                $OVER_BUDGET
                @Suppress("KDocLength")
                fun probe() = Unit
                """,
            ).single().message shouldContain "needs its reason"
        }

        test("a reason on another line, or trailing another annotation, does not count") {
            findingsIn(
                """
                // Three constraints, each one a caller has broken.
                @Suppress("unused", "KDocLength")
                @Deprecated("x") // Three constraints, each one a caller has broken.
                fun probe() = Unit
                """,
            ).size shouldBe 1
        }

        test("a waiver spelled with the ruleset prefix still needs a reason") {
            findingsIn(
                """
                @Suppress("cwfgw:KDocLength")
                fun probe() = Unit
                """,
            ).size shouldBe 1
        }

        test("a suppression of another rule needs no reason from this one") {
            findingsIn(
                """
                @Suppress("LongParameterList")
                fun probe() = Unit
                """,
            ).shouldBeEmpty()
        }
    })
