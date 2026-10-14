package com.cwfgw.detekt

import dev.detekt.api.Config
import dev.detekt.api.Finding
import dev.detekt.test.lintWithContext
import dev.detekt.test.utils.KotlinEnvironmentContainer
import java.io.File
import java.nio.file.Path

/**
 * The real kotlin-logging jar, not a stub of it: the rule matches resolved
 * callable ids and an argument position, both of which a hand-written stub would keep
 * satisfying after the library had moved.
 */
internal val messageEnvironment: KotlinEnvironmentContainer by lazy {
    val classpath =
        requireNotNull(System.getProperty("cwfgw.fixtureClasspath")) {
            "cwfgw.fixtureClasspath is set by the build; run this spec through Gradle"
        }
    KotlinEnvironmentContainer(emptyList(), classpath.split(File.pathSeparator).map { Path.of(it) })
}

/**
 * Every declaration a case might interpolate, so a case is one call and the type under
 * test is the only thing that varies between them.
 */
internal val FIXTURE_PRELUDE: String =
    """
    package fixture

    import io.github.oshai.kotlinlogging.KotlinLogging
    import java.time.Instant
    import java.time.LocalDate
    import java.util.UUID
    import kotlin.time.Duration

    val logger = KotlinLogging.logger {}

    @JvmInline value class EmployeeId(val value: UUID)

    @JvmInline value class ObjectKey(val value: String)

    @JvmInline value class AuditedFields(val fields: List<Pair<String, String?>>)

    @JvmInline value class WrappedMember(val member: MemberRef)

    enum class WaiverReason { COST, SPOUSE }

    data class MemberRef(val firstName: String, val lastName: String)

    sealed interface SettledLine {
        data class Elected(val members: Set<MemberRef>) : SettledLine
    }

    class Opaque

    const val FIXED_REASON = "no prior assignment"

    val TOP_LEVEL_REASON = "folded"

    object Reasons {
        val WITHDRAWN = "withdrawn"
        val derived: String = listOf("a").first()
    }
    """.trimIndent()

/**
 * The call under test. The expression arrives unadorned and is spliced in as an
 * interpolation here, so a case reads as the type it is about and not as escaping.
 */
internal fun interpolating(declarations: String, expression: String): String =
    "fun probe() {\n" + declarations + "\n    error(\"value \${" + expression + "}\")\n}"

internal fun findingsFor(code: String): List<Finding> =
    UnsafeMessageInterpolation(Config.empty)
        .lintWithContext(messageEnvironment, FIXTURE_PRELUDE + "\n\n" + code.trimIndent())

internal fun messagesFor(code: String): List<String> = findingsFor(code).map { finding -> finding.message }
