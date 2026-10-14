package com.cwfgw.detekt

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.nio.file.Path
import kotlin.io.path.readText

private val repoRoot: Path =
    Path.of(
        requireNotNull(System.getProperty("cwfgw.repoRoot")) {
            "cwfgw.repoRoot is set by the build; run this spec through Gradle"
        },
    )

/** The rule names `detekt.yml` configures under the `cwfgw:` section, in file order. */
private fun configuredRuleNames(): List<String> {
    val lines = repoRoot.resolve("detekt.yml").readText().lines()
    val start = lines.indexOfFirst { line -> line == "cwfgw:" }
    require(start >= 0) { "detekt.yml has no cwfgw section, so the ruleset is configured nowhere" }
    return lines.drop(start + 1)
        .takeWhile { line -> line.isBlank() || line.startsWith(" ") || line.startsWith("#") }
        .mapNotNull { line -> Regex("^ {2}(\\w+):$").find(line)?.groupValues?.get(1) }
}

/**
 * A rule that silently stops matching is a green build, so nothing downstream of it can be
 * the thing that notices. These are the seams that come apart in a checked-in file, where a
 * spec can see them. The one seam that comes apart at run time — a `detekt` task losing its
 * compile classpath, which skips the rule outright — is guarded in each module's build script
 * instead, because by the time this spec runs the analysed build is already over.
 */
class RuleDiscoverySpec :
    FunSpec({
        test("the service file names the provider, which is the only way detekt finds it") {
            val service =
                checkNotNull(
                    javaClass.classLoader.getResource("META-INF/services/dev.detekt.api.RuleSetProvider"),
                ) { "the service registration is missing from the jar" }
            service.readText().trim() shouldBe CwfgwRuleSetProvider::class.qualifiedName
        }

        test("detekt.yml configures exactly the rules the provider supplies") {
            val supplied = CwfgwRuleSetProvider().instance().rules.keys.map { name -> name.value }.sorted()
            // Both directions: an unconfigured rule ships on its default, and a configured rule
            // the provider does not supply fails every detekt run in the repo.
            configuredRuleNames().sorted() shouldContainExactly supplied
        }

        test("the ruleset id is the one detekt.yml names") {
            CwfgwRuleSetProvider().ruleSetId.value shouldBe "cwfgw"
            repoRoot.resolve("detekt.yml").readText() shouldContain "\ncwfgw:\n"
        }
    })
