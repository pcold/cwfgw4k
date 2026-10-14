package com.cwfgw.detekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

private data class Case(
    val label: String,
    val declarations: String,
    val expression: String,
    val reason: String = "",
)

private val SAFE =
    listOf(
        Case("a value class over a UUID", "    val id = EmployeeId(UUID.randomUUID())", "id"),
        Case("a value class over a String", """    val key = ObjectKey("k")""", "key"),
        Case("a nullable value class", "    val id: EmployeeId? = null", "id"),
        Case("an enum entry", "    val reason = WaiverReason.COST", "reason"),
        Case("an Int", "    val count = 3", "count"),
        Case("a Long", "    val total = 3L", "total"),
        Case("a Double", "    val ratio = 1.5", "ratio"),
        Case("a Boolean", "    val elected = true", "elected"),
        Case("a UUID", "    val raw = UUID.randomUUID()", "raw"),
        Case("a java.time instant", "    val at = Instant.now()", "at"),
        Case("a java.time date", "    val on = LocalDate.now()", "on"),
        Case("a kotlin.time duration", "    val took = Duration.ZERO", "took"),
        Case("a const val String", "", "FIXED_REASON"),
        Case("a top-level val String folded from a constant", "", "TOP_LEVEL_REASON"),
        Case("an object val String folded from a constant", "", "Reasons.WITHDRAWN"),
        Case("a KClass", "    val type = MemberRef::class", "type"),
        Case("the simple name of a KClass", """    val member = MemberRef("a", "b")""", "member::class.simpleName"),
        Case("a List of a safe element type", "    val widths = listOf(1, 2)", "widths"),
        Case("a Set of a safe element type", "    val ids = setOf(EmployeeId(UUID.randomUUID()))", "ids"),
        Case(
            "a Map of safe key and value types",
            "    val waived = mapOf(EmployeeId(UUID.randomUUID()) to WaiverReason.COST)",
            "waived",
        ),
    )

private val UNSAFE =
    listOf(
        Case(
            "a String the compiler cannot fold",
            """    val name = MemberRef("a", "b").firstName""",
            "name",
            "a String the compiler cannot prove constant",
        ),
        Case(
            "an object val String with a computed initializer",
            "",
            "Reasons.derived",
            "a String the compiler cannot prove constant",
        ),
        Case(
            "the message of a Throwable",
            """    val failure = IllegalStateException("x")""",
            "failure.message",
            "a String the compiler cannot prove constant",
        ),
        Case("a data class", """    val member = MemberRef("a", "b")""", "member", "of type MemberRef"),
        Case(
            "a sealed type",
            "    val line: SettledLine = SettledLine.Elected(emptySet())",
            "line",
            "of type SettledLine",
        ),
        Case(
            "a Throwable",
            """    val failure = IllegalStateException("x")""",
            "failure",
            "of type IllegalStateException",
        ),
        Case("an Any", "    val payload: Any = 3", "payload", "of type Any"),
        Case(
            "a Set of a member-bearing element type",
            """    val members = setOf(MemberRef("a", "b"))""",
            "members",
            "of type Set",
        ),
        Case("a List of String", """    val names = listOf("a")""", "names", "of type List"),
        Case("a class nobody allow-listed", "    val opaque = Opaque()", "opaque", "of type Opaque"),
        Case(
            "a value class over a collection of rendered fields",
            """    val audited = AuditedFields(listOf("firstName" to "Jane"))""",
            "audited",
            "of type AuditedFields",
        ),
        Case(
            "a value class over a data class",
            """    val wrapped = WrappedMember(MemberRef("a", "b"))""",
            "wrapped",
            "of type WrappedMember",
        ),
        Case(
            "a String read off a Java API, which arrives flexible",
            """    val home = System.getProperty("user.home")""",
            "home",
            "a String the compiler cannot prove constant",
        ),
    )

class UnsafeMessageInterpolationTypeSpec :
    FunSpec({
        context("a type whose whole rendering is one opaque token passes") {
            withData(nameFn = { case -> case.label }, SAFE) { case ->
                findingsFor(interpolating(case.declarations, case.expression)).shouldBeEmpty()
            }
        }

        context("a type that can render member data is reported") {
            withData(nameFn = { case -> case.label }, UNSAFE) { case ->
                val messages = messagesFor(interpolating(case.declarations, case.expression))
                messages.size shouldBe 1
                messages.single() shouldBe
                    "`\${${case.expression}}` is ${case.reason}, so this message can carry member data"
            }
        }

        test("an unconstrained type parameter is reported") {
            val messages =
                messagesFor(
                    """
                    fun <T> probe(value: T) {
                        error("value ${'$'}value")
                    }
                    """,
                )
            messages.single() shouldBe
                "`${'$'}value` is of the type parameter T, so this message can carry member data"
        }

        test("a literal with nothing interpolated passes") {
            findingsFor(
                """
                fun probe() {
                    error("the line has no prior assignment")
                }
                """,
            ).shouldBeEmpty()
        }

        test("every unsafe interpolation in one message is reported") {
            messagesFor(
                """
                fun probe() {
                    val member = MemberRef("a", "b")
                    val opaque = Opaque()
                    error("value ${'$'}member and ${'$'}opaque")
                }
                """,
            ).size shouldBe 2
        }
    })
