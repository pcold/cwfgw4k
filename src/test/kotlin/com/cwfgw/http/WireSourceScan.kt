package com.cwfgw.http

import java.io.File

/**
 * The serial name of every column-0 `@Serializable` declaration under [sourceRoots], read off the text.
 * A text scan rather than a classpath one, which would be a new dependency. Being syntax, it predicts
 * the default serial name for a class-level `@Serializable(with = ...)`, so such a serializer must keep it.
 */
internal fun scanAllSerializableTypes(sourceRoots: List<File>): List<String> =
    sourceRoots
        .asSequence()
        .flatMap(File::kotlinSources)
        .flatMap { file -> declarationsIn(file, withoutComments(file.readText())) }
        .toList()

/**
 * `git ls-files` rather than a directory walk: a walk cannot tell this checkout's roots from a nested
 * worktree's copy of them, and the index keeps `build/` and `node_modules/` out without naming them.
 */
internal fun trackedMainSourceRoots(): List<File> =
    trackedPaths()
        .mapNotNull { File(it).enclosingMainSourceRoot() }
        .distinct()

private const val MAIN_SOURCE_ROOT = "src/main/kotlin"

private val PACKAGE_LINE = Regex("""^package\s+([\w.]+)""")

private val SERIAL_NAME_ANNOTATION = Regex("""@SerialName\("([^"]*)"\)""")

private val TYPE_DECLARATION =
    Regex(
        """^(?:public\s+|internal\s+|private\s+)?""" +
            """(?:(?:sealed|data|value|abstract|open|enum)\s+)*(?:class|interface|object)\s+(\w+)""",
    )

private val SEALED_DECLARATION = Regex("""^(?:public\s+|internal\s+|private\s+)?sealed\s+""")

/** What a `@Serializable(with = ...)` inside a class legitimately annotates. */
private val MEMBER_DECLARATION =
    Regex(
        """^(?:(?:override|private|internal|public|protected|const|lateinit|open|abstract|vararg)\s+)*""" +
            """(?:val|var|fun)\s+""",
    )

/** A type argument annotated on a line of its own, as ktlint wraps `List<@Serializable(with = ...) T>`. */
private val TYPE_ARGUMENT = Regex("""^[A-Z][\w.]*\??,?$""")

/** The annotations, and `@Serializable`'s own argument list, that can stand between it and its target. */
private val LEADING_ANNOTATIONS = Regex("""^\s*(?:\([^)]*\)\s*)?(?:@\w+(?:\([^)]*\))?\s*)*""")

/** The scan is clean when it finds nothing, so a root that resolved to nowhere would pass it rather than fail. */
private fun File.kotlinSources(): Sequence<File> {
    require(isDirectory) { "$absolutePath is not a directory: the scan cannot read the backend source" }
    return walkTopDown().filter { it.isFile && it.extension == "kt" }
}

private fun File.enclosingMainSourceRoot(): File? =
    generateSequence(parentFile, File::getParentFile)
        .firstOrNull { it.path == MAIN_SOURCE_ROOT || it.path.endsWith("/$MAIN_SOURCE_ROOT") }

/** `-z`, because `git ls-files` C-quotes any path outside ASCII and a quoted path names no file. */
private fun trackedPaths(): List<String> {
    val git =
        ProcessBuilder("git", "ls-files", "-z")
            .redirectError(ProcessBuilder.Redirect.INHERIT)
            .start()
    val listing = git.inputStream.bufferedReader().use { it.readText() }
    val status = git.waitFor()
    check(status == 0) { "`git ls-files` exited $status: the source roots are read out of the git index" }
    return listing.split('\u0000').filter(String::isNotEmpty)
}

private fun declarationsIn(
    file: File,
    source: String,
): List<String> {
    val lines = source.lines()
    val packageName = lines.firstNotNullOfOrNull { PACKAGE_LINE.find(it)?.groupValues?.get(1) } ?: return emptyList()
    checkNoNestedWireType(file, lines)
    return lines
        .withIndex()
        .filter { (_, line) -> line.startsWith("@Serializable") }
        .map { (index, _) -> serialNameOfDeclarationAt(file, packageName, lines, index) }
}

/**
 * Only column-0 declarations are scanned, so a nested `@Serializable` type is refused rather than read
 * past, except a sealed hierarchy's variant: it reaches the wire through its parent's descriptor.
 * A target the regexes fail on is refused too, never granted the member or type-argument skip.
 */
private fun checkNoNestedWireType(
    file: File,
    lines: List<String>,
) {
    lines
        .withIndex()
        .filter { (_, line) -> !line.startsWith("@Serializable") && line.trimStart().startsWith("@Serializable") }
        .map { (index, _) -> index to declarationTextAt(lines, index) }
        .filterNot { (_, target) ->
            target != null && (MEMBER_DECLARATION.containsMatchIn(target) || TYPE_ARGUMENT.matches(target))
        }
        .forEach { (index, target) ->
            if (target == null || !TYPE_DECLARATION.containsMatchIn(target)) {
                error("${file.path}:${index + 1}: @Serializable on a declaration this scan cannot read")
            }
            val enclosing = lines.take(index).lastOrNull(TYPE_DECLARATION::containsMatchIn)
            check(enclosing != null && SEALED_DECLARATION.containsMatchIn(enclosing)) {
                "${file.path}:${index + 1}: a @Serializable type nested outside a sealed hierarchy is one this " +
                    "scan reads past — declare it at file level"
            }
        }
}

private fun serialNameOfDeclarationAt(
    file: File,
    packageName: String,
    lines: List<String>,
    annotationIndex: Int,
): String {
    val typeName =
        declarationTextAt(lines, annotationIndex)?.let { TYPE_DECLARATION.find(it)?.groupValues?.get(1) }
            ?: error("${file.path}:${annotationIndex + 1}: @Serializable on a declaration this scan cannot read")
    return serialNameAt(lines, annotationIndex) ?: "$packageName.$typeName"
}

/** It may share the line with the annotation: ktlint permits that. */
private fun declarationTextAt(
    lines: List<String>,
    annotationIndex: Int,
): String? {
    val annotationLine = lines.getOrNull(annotationIndex) ?: return null
    val sameLine = LEADING_ANNOTATIONS.replaceFirst(annotationLine.substringAfter("@Serializable"), "").trim()
    if (sameLine.isNotEmpty()) return sameLine
    val below = lines.drop(annotationIndex + 1)
    return below.getOrNull(below.takeWhile { it.trimStart().startsWith("@") }.size)?.trimStart()
}

/** The search stops at the first non-annotation line so it cannot reach into the next declaration's stack. */
private fun serialNameAt(
    lines: List<String>,
    annotationIndex: Int,
): String? {
    val above = lines.take(annotationIndex).takeLastWhile { it.trimStart().startsWith("@") }
    val below = lines.drop(annotationIndex + 1).takeWhile { it.trimStart().startsWith("@") }
    return (above + lines.getOrNull(annotationIndex).orEmpty() + below)
        .firstNotNullOfOrNull { SERIAL_NAME_ANNOTATION.find(it)?.groupValues?.get(1) }
}

/**
 * Comment bodies blanked out, so a `@Serializable` in a KDoc sample is not scanned. A block comment must
 * open its own line, or a comment opener inside a string literal would swallow real declarations.
 */
private fun withoutComments(source: String): String {
    val withoutBlockComments =
        BLOCK_COMMENT.replace(source) { match -> match.value.map { if (it == '\n') '\n' else ' ' }.joinToString("") }
    return withoutBlockComments.lines().joinToString("\n") { it.substringBefore("//") }
}

private val BLOCK_COMMENT =
    Regex("""^[ \t]*/\*.*?\*/""", setOf(RegexOption.MULTILINE, RegexOption.DOT_MATCHES_ALL))
