package com.cwfgw.detekt

import com.intellij.psi.PsiElement
import dev.detekt.api.Config
import dev.detekt.api.Entity
import dev.detekt.api.Finding
import dev.detekt.api.Location
import dev.detekt.api.Rule
import org.jetbrains.kotlin.kdoc.psi.api.KDoc
import org.jetbrains.kotlin.psi.KtAnnotationEntry

/**
 * Counts the lines that carry text, so neither where the delimiters sit nor a blank line between two
 * constraints moves the count; tag lines count, being prose a reader reads.
 *
 * The waiver is `@Suppress("KDocLength")` with a `//` reason trailing it on its line; ktlint forbids the house
 * habit of a reason on the line above, which here would sit between the KDoc and the annotation. A reasonless
 * waiver is reported against the file, where the declaration's own `@Suppress` cannot reach.
 */
public class KDocLength(
    config: Config,
) : Rule(
        config,
        "A KDoc keeps the constraint a future editor would otherwise break, in at most $MAX_KDOC_LINES lines.",
    ) {
    override fun visitElement(element: PsiElement) {
        super.visitElement(element)
        if (element !is KDoc) return
        val lines = element.text.lines().count { line -> line.isContentLine() }
        if (lines > MAX_KDOC_LINES) {
            report(
                Finding(
                    Entity.from(element),
                    "this KDoc runs to $lines lines of text, over the budget of $MAX_KDOC_LINES; keep the " +
                        "constraint and cut the argument, or waive it with a reasoned @Suppress",
                ),
            )
        }
    }

    override fun visitAnnotationEntry(annotationEntry: KtAnnotationEntry) {
        super.visitAnnotationEntry(annotationEntry)
        if (!annotationEntry.suppresses(ruleName.value) || annotationEntry.hasTrailingReason()) return
        report(
            Finding(
                Entity(
                    signature = Entity.from(annotationEntry).signature,
                    location = Location.from(annotationEntry),
                    ktElement = annotationEntry.containingKtFile,
                ),
                "a @Suppress of ${ruleName.value} needs its reason in a `//` comment trailing it on its line",
            ),
        )
    }
}

internal const val MAX_KDOC_LINES = 6

private val SUPPRESS_ANNOTATIONS = setOf("Suppress", "SuppressWarnings", "kotlin.Suppress")

private val RULE_ID_PREFIX = Regex("^(detekt|${CwfgwRuleSetProvider.RULE_SET_ID})[.:]")

private fun String.isContentLine(): Boolean =
    trim().removePrefix("/**").removeSuffix("*/").trim().removePrefix("*").isNotBlank()

private fun KtAnnotationEntry.suppresses(ruleId: String): Boolean =
    typeReference?.text in SUPPRESS_ANNOTATIONS &&
        valueArguments.any { argument ->
            argument.getArgumentExpression()?.text?.trim('"')?.replace(RULE_ID_PREFIX, "") == ruleId
        }

private fun KtAnnotationEntry.hasTrailingReason(): Boolean {
    val restOfLine = containingKtFile.text.substring(textRange.endOffset).substringBefore('\n').trim()
    return restOfLine.startsWith("//") && restOfLine.removePrefix("//").isNotBlank()
}
