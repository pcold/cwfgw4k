package com.cwfgw.detekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private const val MEMBER = """    val member = MemberRef("a", "b")"""

class UnsafeMessageInterpolationShapeSpec :
    FunSpec({
        context("a guarded shape is caught wherever it carries its message") {
            listOf(
                "error" to """error("value ${'$'}member")""",
                "require" to """require(member.firstName.isEmpty()) { "value ${'$'}member" }""",
                "check" to """check(member.firstName.isEmpty()) { "value ${'$'}member" }""",
                "requireNotNull" to """requireNotNull(member.lastName) { "value ${'$'}member" }""",
                "checkNotNull" to """checkNotNull(member.lastName) { "value ${'$'}member" }""",
                "a lambda log" to """logger.warn { "value ${'$'}member" }""",
                "a log behind a throwable" to
                    """logger.error(IllegalStateException("x")) { "value ${'$'}member" }""",
                "an event-builder log" to """logger.atInfo { message = "value ${'$'}member" }""",
                "ApplicationFailure" to
                    """ApplicationFailure.newNonRetryableFailure("value ${'$'}member", "type")""",
            ).forEach { (label, call) ->
                test(label) {
                    messagesFor("fun probe() {\n$MEMBER\n    $call\n}")
                        .single() shouldContain "`${'$'}member` is of type MemberRef"
                }
            }
        }

        test("every log level is guarded") {
            listOf("trace", "debug", "info", "warn", "error").forEach { level ->
                messagesFor("fun probe() {\n$MEMBER\n    logger.$level { \"value ${'$'}member\" }\n}")
                    .size shouldBe 1
            }
        }

        test("a literal that names a member in its own words has nothing to resolve") {
            // The limit the rule's KDoc states, pinned so the two cannot drift apart. The
            // reported half keeps the empty half honest: on its own, an empty result reads
            // the same whether the limit holds or the fixture stopped compiling.
            val interpolated = """logger.info { "employee ${'$'}member failed" }"""
            messagesFor("fun probe() {\n$MEMBER\n    $interpolated\n}").size shouldBe 1
            findingsFor(
                """
                fun probe() {
                    logger.info { "employee Jane Doe failed" }
                }
                """,
            ).shouldBeEmpty()
        }

        test("a contract check with no message overload is not a message site") {
            findingsFor(
                """
                fun probe() {
                    val member = MemberRef("a", "b")
                    require(member.firstName.isEmpty())
                }
                """,
            ).shouldBeEmpty()
        }

        test("a call this rule does not guard is left alone") {
            findingsFor(
                """
                fun probe() {
                    val member = MemberRef("a", "b")
                    println("value ${'$'}member")
                }
                """,
            ).shouldBeEmpty()
        }

        context("a message assembled outside the call is not checkable") {
            listOf(
                "a variable" to "error(text)",
                "a function call" to "error(build())",
                "a concatenation" to """error("value " + text)""",
            ).forEach { (label, call) ->
                test(label) {
                    messagesFor(
                        """
                        fun build() = "value"

                        fun probe() {
                            val text = build()
                            $call
                        }
                        """,
                    ).single() shouldContain "the message is assembled outside the call"
                }
            }
        }

        test("a named constant passes where a variable does not") {
            findingsFor(
                """
                fun probe(renderer: String?) {
                    requireNotNull(renderer) { FIXED_REASON }
                }
                """,
            ).shouldBeEmpty()
        }

        test("a safe interpolation passes through every guarded shape") {
            findingsFor(
                """
                fun probe() {
                    val id = EmployeeId(UUID.randomUUID())
                    logger.info { "settled ${'$'}id" }
                    require(FIXED_REASON.isEmpty()) { "settled ${'$'}id" }
                    ApplicationFailure.newNonRetryableFailure("settled ${'$'}id", "type")
                }
                """,
            ).shouldBeEmpty()
        }
    })
