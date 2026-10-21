@file:OptIn(ExperimentalSerializationApi::class)

package com.cwfgw.http

import com.cwfgw.admin.PickMatch
import com.cwfgw.reports.TeamStanding
import com.cwfgw.seasons.SeasonRules
import com.cwfgw.teams.TeamId
import io.kotest.assertions.fail
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainAll
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.serializer
import java.io.File
import java.util.UUID

/** A detekt plugin loaded by the build, never by a client. Listed so a new module can't be in neither list. */
private val NON_WIRE_SOURCE_ROOTS = listOf(File("detekt-rules/src/main/kotlin"))

private const val SNAPSHOT_PATH = "src/test/resources/wire-contract.txt"

private const val REGENERATED_PATH = "build/wire-contract-actual.txt"

private const val ASSERTIONS_PATH = "ui/src/shared/api/wireContract.generated.ts"

private const val REGENERATED_ASSERTIONS_PATH = "build/wireContract.generated.ts"

/**
 * Declarations the scan always finds, one per syntax it parses: a plain class, a sealed hierarchy, a
 * value class and a class-level `@Serializable(with = ...)`. An inert scan fails here, not clean.
 */
private val SCAN_FLOOR =
    listOf(
        "com.cwfgw.http.ErrorBody",
        "com.cwfgw.admin.PickMatch",
        "com.cwfgw.golfers.GolferId",
        "com.cwfgw.tournaments.TournamentStatus",
    )

/** Every `@Serializable` type no client sees, so absent from the snapshot on purpose. */
private val NON_WIRE_TYPES =
    setOf(
        // ESPN's scoreboard JSON, read from ESPN and never written to a client.
        "com.cwfgw.espn.EspnCalendarJson",
        "com.cwfgw.espn.EspnCompetitionJson",
        "com.cwfgw.espn.EspnCompetitorJson",
        "com.cwfgw.espn.EspnEventJson",
        "com.cwfgw.espn.EspnLeagueJson",
        "com.cwfgw.espn.EspnLinescoreJson",
        "com.cwfgw.espn.EspnNamedJson",
        "com.cwfgw.espn.EspnScoreboardResponse",
        "com.cwfgw.espn.EspnStatusJson",
        "com.cwfgw.espn.EspnStatusType",
        // An in-process command the admin and ESPN imports build; no route receives it.
        "com.cwfgw.golfers.CreateGolferRequest",
        // A log line on stdout, for Cloud Logging.
        "com.cwfgw.logging.CloudLogEntry",
        // The session cookie's payload, which the client never parses.
        "com.cwfgw.users.UserSession",
    )

/**
 * Pins the JSON shape the backend serves and receives, and through the generated TypeScript assertions
 * holds `ui/src/shared/api/types.ts` to it. Keys only: a `kotlin.Int` read as a TypeScript `string` passes
 * both halves, and a default's value is invisible to a descriptor, though gaining or losing one is not.
 */
class WireContractSpec : FunSpec({
    // Every setting below changes the wire while leaving wire-contract.txt byte-identical.
    test("appJson still reads and writes the wire the way the snapshot assumes") {
        appJson.configuration.ignoreUnknownKeys shouldBe true
        appJson.configuration.encodeDefaults shouldBe true
        appJson.configuration.namingStrategy shouldBe JsonNamingStrategy.SnakeCase
        appJson.configuration.explicitNulls shouldBe true
        appJson.configuration.useArrayPolymorphism shouldBe false
        appJson.configuration.classDiscriminator shouldBe "type"
    }

    test("the keys the snapshot computes are the keys appJson writes") {
        val encoded =
            listOf(
                encodedKeys(serializer<SeasonRules>(), SeasonRules.defaults()),
                encodedKeys(serializer<ErrorBody>(), ErrorBody("nope")),
                encodedKeys(serializer<PickMatch>(), PickMatch.MatchedEspn(espnId = "1", golferName = "A Golfer")),
                encodedKeys(
                    serializer<TeamStanding>(),
                    TeamStanding.Leader(teamId = TeamId(UUID(0L, 0L)), tied = false, aheadOf = null),
                ),
            )
        val computed =
            listOf(
                wireFieldNames(serializer<SeasonRules>().descriptor),
                wireFieldNames(serializer<ErrorBody>().descriptor),
                variantKeys(serializer<PickMatch>(), "matched_espn"),
                variantKeys(serializer<TeamStanding>(), "leader"),
            )

        encoded shouldBe computed
    }

    // The scan reads the roots it is given and is clean when it finds nothing.
    test("the scan is pointed at every Kotlin source root in the repository") {
        val discovered = trackedMainSourceRoots().map { it.canonicalPath }.sorted()

        withClue(
            """
            |A module's sources are not being scanned, so its @Serializable types are invisible to every
            |test below. Add the root to MAIN_SOURCE_ROOTS, or to NON_WIRE_SOURCE_ROOTS if no client can
            |ever see what it declares.
            """.trimMargin(),
        ) {
            discovered shouldBe (MAIN_SOURCE_ROOTS + NON_WIRE_SOURCE_ROOTS).map { it.canonicalPath }.sorted()
        }
    }

    test("every @Serializable type either reaches the wire or is recorded as one that does not") {
        val scanned = scanAllSerializableTypes(MAIN_SOURCE_ROOTS)
        val reached = wireReachableSerialNames(WIRE_TYPES)

        withClue("the scan found none of the declarations it always finds, so it is inert rather than clean") {
            scanned shouldContainAll SCAN_FLOOR
        }
        withClue(
            """
            |@Serializable type(s) that no WIRE_TYPES root reaches and NON_WIRE_TYPES does not excuse. If a
            |route receives or responds with one, add it to WIRE_TYPES; if no client can see it, add it to
            |NON_WIRE_TYPES beside the others and say which category it joins.
            """.trimMargin(),
        ) {
            scanned.filterNot { it in reached || it in NON_WIRE_TYPES }.sorted() shouldBe emptyList()
        }
        withClue("NON_WIRE_TYPES excuses a type that is gone, or that the wire now reaches: delete the entry") {
            NON_WIRE_TYPES.filter { it !in scanned || it in reached }.sorted() shouldBe emptyList()
        }
    }

    test("the wire shape of every type in WIRE_TYPES matches the reviewed snapshot") {
        val snapshot = File(SNAPSHOT_PATH)
        val actual = wireContractSnapshot(WIRE_TYPES)
        val recorded = if (snapshot.exists()) snapshot.readText() else ""
        if (recorded == actual) return@test

        val regenerated = File(REGENERATED_PATH).also { it.parentFile.mkdirs() }
        regenerated.writeText(actual)
        fail(
            """
            |The wire shape of at least one type changed. Read the drift below; if it is intended,
            |regenerate the snapshot, and the TypeScript assertions the test below names.
            |
            |    cp ${regenerated.path} $SNAPSHOT_PATH
            |
            |${describeDrift(recorded = recorded, actual = actual)}
            """.trimMargin(),
        )
    }

    test("every type the TypeScript mapping names is a shape the snapshot still pins") {
        val pinned = wireReachableDescriptors(WIRE_TYPES).mapKeys { it.key.substringBefore('<') }
        val named = TYPESCRIPT_WIRE_TYPES.keys + UNMODELLED_WIRE_TYPES + UNMODELLED_WIRE_FIELDS.keys

        withClue("an entry names a type nothing puts on the wire now, so it would excuse one put back: delete it") {
            named.filterNot { it in pinned }.sorted() shouldBe emptyList()
        }
        withClue("a type is both renamed and excluded, and the exclusion wins, so the rename never applies") {
            TYPESCRIPT_WIRE_TYPES.keys.intersect(UNMODELLED_WIRE_TYPES).sorted() shouldBe emptyList()
        }
        withClue("a type excluded whole has a field excused too, and the field entry is dead weight") {
            UNMODELLED_WIRE_FIELDS.keys.intersect(UNMODELLED_WIRE_TYPES).sorted() shouldBe emptyList()
        }
        withClue("a field is excused from types.ts that its type no longer carries, so the excuse outlived it") {
            UNMODELLED_WIRE_FIELDS
                .flatMap { (serialName, excused) ->
                    val carried = pinned[serialName]?.let(::wireFieldNames).orEmpty()
                    (excused - carried).map { "$serialName.$it" }
                }.sorted() shouldBe emptyList()
        }
    }

    test("the emitted TypeScript assertions match the file tsc reads") {
        val committed = File(ASSERTIONS_PATH)
        val actual = wireContractAssertions(WIRE_TYPES)
        if (committed.exists() && committed.readText() == actual) return@test

        val regenerated = File(REGENERATED_ASSERTIONS_PATH).also { it.parentFile.mkdirs() }
        regenerated.writeText(actual)
        fail(
            """
            |$ASSERTIONS_PATH is stale,
            |so tsc is checking types.ts against a wire shape the backend no longer has. Regenerate it, then
            |make types.ts match what tsc reports. Rerunning needs -x uiBuild until it does: :test compiles
            |the UI first, and failing that compile is the point.
            |
            |    cp ${regenerated.path} $ASSERTIONS_PATH
            """.trimMargin(),
        )
    }
})

private fun <T> encodedKeys(
    serializer: KSerializer<T>,
    value: T,
): Set<String> = appJson.encodeToJsonElement(serializer, value).jsonObject.keys

private fun variantKeys(
    hierarchy: KSerializer<*>,
    variantName: String,
): Set<String> {
    val descriptor = hierarchy.descriptor
    check(descriptor.kind == PolymorphicKind.SEALED) { "${descriptor.serialName} is not a sealed hierarchy" }
    val variant = sealedVariants(descriptor).first { it.serialName == variantName }
    return setOf(sealedDiscriminator(descriptor)) + wireFieldNames(variant.descriptor)
}
