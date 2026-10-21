package com.cwfgw.detekt

import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.RequiresAnalysisApi
import dev.detekt.api.Rule
import org.jetbrains.kotlin.analysis.api.KaConstantInitializerValue
import org.jetbrains.kotlin.analysis.api.KaExperimentalApi
import org.jetbrains.kotlin.analysis.api.KaSession
import org.jetbrains.kotlin.analysis.api.analyze
import org.jetbrains.kotlin.analysis.api.symbols.KaClassKind
import org.jetbrains.kotlin.analysis.api.symbols.KaNamedClassSymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaPropertySymbol
import org.jetbrains.kotlin.analysis.api.symbols.KaSymbolModality
import org.jetbrains.kotlin.analysis.api.types.KaClassType
import org.jetbrains.kotlin.analysis.api.types.KaDefinitelyNotNullType
import org.jetbrains.kotlin.analysis.api.types.KaFlexibleType
import org.jetbrains.kotlin.analysis.api.types.KaType
import org.jetbrains.kotlin.analysis.api.types.KaTypeParameterType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtBinaryExpression
import org.jetbrains.kotlin.psi.KtCallExpression
import org.jetbrains.kotlin.psi.KtDotQualifiedExpression
import org.jetbrains.kotlin.psi.KtExpression
import org.jetbrains.kotlin.psi.KtLambdaExpression
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtStringTemplateEntryWithExpression
import org.jetbrains.kotlin.psi.KtStringTemplateExpression
import org.jetbrains.kotlin.psi.psiUtil.collectDescendantsOfType

/**
 * Reads the declared type each interpolated expression resolves to, never a name, so
 * `val employeeId: String = employee.firstName` does not satisfy it. A literal naming a member in its own words
 * has nothing to resolve and passes: the guarantee is that no new route to these messages opens without
 * review, not that the messages are clean. The event builder's `payload` map, a structured field channel
 * nothing here writes to, is out of scope.
 */
public class UnsafeMessageInterpolation(
    config: Config,
) : Rule(
        config,
        "A failure or log message must be a string literal whose every interpolated value has a type " +
            "that cannot render member data.",
    ),
    RequiresAnalysisApi {
    override fun visitCallExpression(expression: KtCallExpression) {
        super.visitCallExpression(expression)
        val callable = analyze(expression) { resolvedCallableName(expression) } ?: return
        if (callable in LOGGER_EVENT_BUILDERS) {
            inspectEventBuilder(expression)
            return
        }
        val message = messageArgumentOf(expression, callable) ?: return
        inspect(message.unwrapLambdaBody())
    }

    /** `logger.atInfo { message = "…" }` — the same channel through a builder property. */
    private fun inspectEventBuilder(expression: KtCallExpression) {
        val body =
            (expression.valueArguments.lastOrNull()?.getArgumentExpression() as? KtLambdaExpression)
                ?.bodyExpression ?: return
        body.collectDescendantsOfType<KtBinaryExpression> { assignment ->
            assignment.operationToken == KtTokens.EQ &&
                assignment.left?.referencedName()?.getReferencedName() == "message"
        }.forEach { assignment -> assignment.right?.let { inspect(it) } }
    }

    private fun inspect(message: KtExpression) {
        if (message !is KtStringTemplateExpression) {
            // A named `const val` is not a literal and needs no literal's protection: the
            // compiler has already fixed its value, so the alternative to admitting it is a
            // remediation that inlines a good constant to satisfy the rule.
            if (analyze(message) { isCompileTimeConstant(message) }) return
            report(
                Finding(
                    Entity.from(message),
                    "the message is assembled outside the call, so what it can carry is not checkable here; " +
                        "pass a string literal",
                ),
            )
            return
        }
        analyze(message) {
            for (entry in message.entries.filterIsInstance<KtStringTemplateEntryWithExpression>()) {
                val reason = entry.expression?.let { interpolated -> unsafeReason(interpolated) } ?: continue
                // Anchored at the literal, not the entry: a baseline signature is the element's
                // text, so `$name` would waive every later `$name` in the file. Two identical
                // literals in one class still share one entry.
                report(
                    Finding(
                        Entity.from(message),
                        "`${entry.text}` is $reason, so this message can carry member data",
                    ),
                )
            }
        }
    }
}

private const val KLOGGER = "io.github.oshai.kotlinlogging.KLogger"

private val CONTRACT_CHECKS =
    setOf("kotlin.require", "kotlin.check", "kotlin.requireNotNull", "kotlin.checkNotNull")

private val LOGGER_LEVELS =
    setOf("trace", "debug", "info", "warn", "error").map { level -> "$KLOGGER.$level" }.toSet()

private val LOGGER_EVENT_BUILDERS =
    setOf("atTrace", "atDebug", "atInfo", "atWarn", "atError", "at")
        .map { level -> "$KLOGGER.$level" }
        .toSet()

/** A lambda-wrapped message — `require(x) { "…" }` — is the literal its body ends on. */
private fun KtExpression.unwrapLambdaBody(): KtExpression =
    (this as? KtLambdaExpression)?.bodyExpression?.statements?.lastOrNull() ?: this

/**
 * The argument each guarded shape carries its message in, or null when the call is a
 * shape this rule does not guard — or an overload of a guarded one with no message at
 * all, which is every one-argument `require` / `check`.
 *
 * Every `KLogger` level overload takes the message last, whether or not a marker and a
 * throwable precede it, so the last index covers the lambda form and an eager `String`
 * one alike.
 */
private fun messageArgumentIndex(callable: String, argumentCount: Int): Int? =
    when {
        callable == "kotlin.error" -> 0
        callable in CONTRACT_CHECKS -> if (argumentCount >= 2) 1 else null
        callable in LOGGER_LEVELS -> if (argumentCount >= 1) argumentCount - 1 else null
        else -> null
    }.takeIf { index -> index == null || index < argumentCount }

private fun messageArgumentOf(expression: KtCallExpression, callable: String): KtExpression? {
    val arguments = expression.valueArguments
    val index = messageArgumentIndex(callable, arguments.size) ?: return null
    return arguments[index].getArgumentExpression()
}

/**
 * Resolved, never spelled: this repo's loggers are variously `log`, `poolMetricsLog`
 * and `acquireLog`, and matching a receiver's name would be the
 * forgeable check this rule exists to replace.
 */
@OptIn(KaExperimentalApi::class)
private fun KaSession.resolvedCallableName(expression: KtCallExpression): String? =
    expression.resolveSymbol()?.callableId?.asSingleFqName()?.asString()

private const val MAX_TYPE_DEPTH = 5

// No `java.math.BigDecimal`: here it is the money and percentage scalar (a payout, a side bet, an
// ownership share), so its bare number is the member datum. Add it only alongside a decision about
// what a message may say about an amount.
private val SAFE_SCALAR_TYPES =
    setOf(
        "kotlin.Byte", "kotlin.Short", "kotlin.Int", "kotlin.Long",
        "kotlin.Float", "kotlin.Double", "kotlin.Boolean",
        "kotlin.reflect.KClass",
        "kotlin.time.Duration",
        "java.util.UUID",
        "java.time.Instant", "java.time.LocalDate", "java.time.LocalDateTime", "java.time.LocalTime",
        "java.time.OffsetDateTime", "java.time.ZonedDateTime", "java.time.Duration", "java.time.Period",
        "java.time.Year", "java.time.YearMonth", "java.time.MonthDay", "java.time.ZoneId",
        "java.time.ZoneOffset",
    )

private val SAFE_CONTAINER_TYPES =
    setOf(
        "kotlin.Array",
        "kotlin.collections.Collection", "kotlin.collections.Iterable", "kotlin.collections.List",
        "kotlin.collections.Map", "kotlin.collections.Set",
        "kotlin.collections.MutableCollection", "kotlin.collections.MutableList",
        "kotlin.collections.MutableMap", "kotlin.collections.MutableSet",
        "kotlin.sequences.Sequence",
    )

/** Why this expression can render member data, phrased for the finding, or null when it cannot. */
private fun KaSession.unsafeReason(expression: KtExpression): String? {
    val type = expression.expressionType?.let { normalized(it) } ?: return "of a type this rule could not resolve"
    if (type.isStringType) {
        return when {
            isCompileTimeConstant(expression) -> null
            rendersOnlyAClassName(expression) -> null
            else -> "a String the compiler cannot prove constant"
        }
    }
    return if (rendersSafely(type, depth = 0)) null else readableName(type)
}

/**
 * A value read off a Java API arrives as a flexible `String!`, which is neither a class type
 * nor a String to `isStringType` — so without this it lands in the catch-all and is reported
 * as an opaque type rather than as the unconstant String it is.
 */
private fun KaSession.normalized(type: KaType): KaType =
    when (val expanded = type.fullyExpandedType) {
        is KaFlexibleType -> expanded.lowerBound
        is KaDefinitelyNotNullType -> expanded.original
        else -> expanded
    }

/** Phrased to slot into "`$x` is …", so the finding names the type rather than the expression. */
private fun KaSession.readableName(type: KaType): String =
    when (val expanded = normalized(type)) {
        is KaClassType -> "of type ${expanded.classId.shortClassName.asString()}"
        is KaTypeParameterType -> "of the type parameter ${expanded.symbol.name.asString()}"
        else -> "of a type this rule could not resolve"
    }

/**
 * An allow-list, so a type nobody thought about is unsafe. `toString()` is the channel:
 * a data class or a sealed type renders its fields, and only a type whose whole rendering
 * is one opaque token — an id, an enum name, a number, an instant — is admissible.
 *
 * Nullability is not stripped because it does not need to be: `EmployeeId?` is the same
 * class type as `EmployeeId`, and a null renders as `null`.
 */
private fun KaSession.rendersSafely(type: KaType, depth: Int): Boolean {
    if (depth > MAX_TYPE_DEPTH) return false
    val expanded = normalized(type)
    if (expanded is KaTypeParameterType) {
        val bounds = expanded.symbol.upperBounds
        return bounds.isNotEmpty() && bounds.all { bound -> rendersSafely(bound, depth + 1) }
    }
    if (expanded.isAnyType) return false
    val classType = expanded as? KaClassType ?: return false
    return when (classType.classId.asSingleFqName().asString()) {
        in SAFE_SCALAR_TYPES -> true
        in SAFE_CONTAINER_TYPES -> rendersSafelyAsContainer(classType, depth)
        else -> rendersSafelyAsDeclared(classType, depth)
    }
}

private fun KaSession.rendersSafelyAsContainer(classType: KaClassType, depth: Int): Boolean {
    val arguments = classType.typeArguments
    // A star projection resolves to no type at all, which is the one case that would otherwise
    // pass an empty `all`.
    return arguments.isNotEmpty() &&
        arguments.all { argument -> argument.type?.let { rendersSafely(it, depth + 1) } == true }
}

private fun KaSession.rendersSafelyAsDeclared(classType: KaClassType, depth: Int): Boolean {
    val symbol = classType.symbol as? KaNamedClassSymbol ?: return false
    if (symbol.isData || symbol.modality == KaSymbolModality.SEALED) return false
    if (symbol.classKind == KaClassKind.ENUM_CLASS) return true
    return symbol.isInline && wrapsOneToken(symbol, depth)
}

/**
 * A value class renders its wrapped value, so the wrapped type is what gets judged:
 * `AuditFields(List<Pair<String, String?>>)` renders every audited field under a default `toString()`.
 * A wrapped bare `String` stays admissible: declaring the class is the reviewable act that makes it an opaque
 * token. A `toString()` override is not consulted, as this rule reads no function bodies.
 */
private fun KaSession.wrapsOneToken(symbol: KaNamedClassSymbol, depth: Int): Boolean {
    val underlying =
        symbol.declaredMemberScope.constructors
            .firstOrNull { constructor -> constructor.isPrimary }
            ?.valueParameters
            ?.singleOrNull()
            ?.returnType ?: return false
    return normalized(underlying).isStringType || rendersSafely(underlying, depth + 1)
}

/**
 * `const val`, and the top-level or `object` `val` the compiler folds to the same thing.
 * A constant-folded initializer is fixed at compile time whoever holds the property, so a
 * member `val` that qualifies is admissible for the same reason a `const val` is.
 */
@OptIn(KaExperimentalApi::class)
private fun KaSession.isCompileTimeConstant(expression: KtExpression): Boolean {
    if (expression.evaluate() != null) return true
    val reference = expression.referencedName() ?: return false
    val symbol = reference.resolveSymbol() as? KaPropertySymbol ?: return false
    return symbol.isVal && symbol.initializer is KaConstantInitializerValue
}

private fun KtExpression.referencedName(): KtNameReferenceExpression? =
    when (this) {
        is KtNameReferenceExpression -> this
        is KtDotQualifiedExpression -> selectorExpression as? KtNameReferenceExpression
        else -> null
    }

/** `${value::class.simpleName}` — a type's own name, which no member's data reaches. */
private fun KaSession.rendersOnlyAClassName(expression: KtExpression): Boolean {
    val qualified = expression as? KtDotQualifiedExpression ?: return false
    val selector = qualified.selectorExpression as? KtNameReferenceExpression ?: return false
    if (selector.getReferencedName() != "simpleName") return false
    val receiver = qualified.receiverExpression.expressionType?.fullyExpandedType as? KaClassType ?: return false
    return receiver.classId.asSingleFqName().asString() == "kotlin.reflect.KClass"
}
