package com.cwfgw

import com.lemonappdev.konsist.api.Konsist
import com.lemonappdev.konsist.api.declaration.KoFileDeclaration
import com.lemonappdev.konsist.api.declaration.KoFunctionDeclaration
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

/**
 * The layering rules of `src/CLAUDE.md` → Ktor and jOOQ, as build failures. A route is a `*Routes.kt` file and a
 * repository a `*Repository.kt` file, so the wiring that hands repositories to services is in neither.
 */
class ArchitectureSpec : FunSpec({
    val production = Konsist.scopeFromProduction(moduleName = "root", sourceSetName = "main")
    val routeFiles = production.files.filter { file -> file.name.endsWith("Routes") }
    val repositoryFiles = production.files.filter(KoFileDeclaration::isRepository)

    test("routes call services, never repositories") {
        val leaks = routeFiles.flatMap { file -> file.linesMatching(REPOSITORY_REFERENCE) }

        leaks shouldBe emptyList()
    }

    test("repositories never import Ktor") {
        val leaks = repositoryFiles.flatMap { file -> file.importsWhere { import -> import.startsWith("io.ktor.") } }

        leaks shouldBe emptyList()
    }

    test("a repository function that writes or takes a row lock declares a WriteTransactionContext") {
        // By the jOOQ call, not the name: findOrCreate, lockSeason and clear need the write context under
        // names no convention catches. A write reached through a helper is the helper's to declare; the
        // compiler holds its caller.
        val leaks =
            repositoryFiles
                .flatMap { file -> file.functions(includeNested = true, includeLocal = true) }
                .filter { function -> WRITE_CALL.containsMatchIn(function.text) && !function.declaresWriteContext() }
                .map { function -> "${function.containingFile.projectPath} → ${function.name}" }

        leaks shouldBe emptyList()
    }

    test("no DSLContext outside a repository or the db package") {
        val leaks =
            production.files
                .filterNot { file -> file.isRepository() || file.packagee?.name == "com.cwfgw.db" }
                .flatMap { file -> file.importsWhere { import -> import == "org.jooq.DSLContext" } }

        leaks shouldBe emptyList()
    }
})

private val REPOSITORY_REFERENCE = Regex("""\w*Repository\b""")

private val WRITE_CALL =
    Regex("""\.(insertInto|update|deleteFrom|delete|mergeInto|forUpdate|forNoKeyUpdate|forShare|forKeyShare)\(""")

private val CONTEXT_CLAUSE_AT_END = Regex("""context\([^()]*\)\s*$""")

private fun KoFileDeclaration.isRepository(): Boolean = name.endsWith("Repository")

private fun KoFileDeclaration.importsWhere(predicate: (String) -> Boolean): List<String> =
    imports.map { import -> import.name }.filter(predicate).map { import -> "$projectPath → $import" }

private fun KoFileDeclaration.linesMatching(pattern: Regex): List<String> =
    text.lines().mapIndexedNotNull { index, line ->
        pattern.find(line)?.let { match -> "$projectPath:${index + 1} → ${match.value}" }
    }

/**
 * Konsist 0.17.3 parses with Kotlin 2.0's PSI, which leaves a `context(...)` clause outside the function it
 * belongs to, so the clause is read from the file text directly before the declaration.
 */
private fun KoFunctionDeclaration.declaresWriteContext(): Boolean {
    val fileText = containingFile.text
    val lineAndColumn = location.removePrefix("${containingFile.path}:")
    val line = lineAndColumn.substringBefore(":").toInt()
    val column = lineAndColumn.substringAfter(":").toInt()
    val start = fileText.lines().take(line - 1).sumOf { above -> above.length + 1 } + column - 1
    val clause = CONTEXT_CLAUSE_AT_END.find(fileText.substring(0, start))?.value

    return clause?.contains("WriteTransactionContext") == true
}
