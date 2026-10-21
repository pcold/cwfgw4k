@file:OptIn(ExperimentalSerializationApi::class)

package com.cwfgw.http

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.PolymorphicKind
import kotlinx.serialization.descriptors.SerialDescriptor

private const val GENERATED_HEADER =
    """/*
 * Generated from the Kotlin serializer descriptors by WireContractSpec. Do not hand-edit: a key that
 * belongs here is a property on a type under src/main/kotlin. A conflict is regenerated, never merged.
 *
 *     ./gradlew test -x uiBuild -Dkotest.filter.specs='*WireContractSpec'
 *     cp build/wireContract.generated.ts ui/src/shared/api/wireContract.generated.ts
 *
 * `-x uiBuild` because :test compiles this file before running, and a stale one is what fails that
 * compile; without it the command that regenerates the file cannot reach the code that writes it.
 */
"""

private const val WIRE_CONTRACT_DOC =
    """/**
 * Nothing imports this, and it still fails the build: `tsconfig.app.json` includes every file under
 * `src`, and each member is a verdict standing under an index signature of `true`.
 */
"""

/**
 * One assertion per asserted wire type, over the walk the snapshot renders, so the two cannot disagree
 * about which types reach a client. A sealed hierarchy gets one for its discriminator values and one per
 * variant, named through `Extract`: the wire sends a variant's own keys beside the discriminator, never
 * the hierarchy's union of them.
 */
internal fun wireContractAssertions(roots: List<SerialDescriptor>): String {
    val members = assertionMembers(roots)
    val imports = members.mapTo(sortedSetOf()) { it.importedType }
    // Only the verdicts in use: an unused import is a noUnusedLocals error naming nothing about its cause.
    val verdicts = members.mapTo(sortedSetOf()) { it.verdict }
    return GENERATED_HEADER +
        "\nimport type { ${verdicts.joinToString()} } from './exactFields';\n" +
        imports.joinToString(separator = "\n", prefix = "import type {\n", postfix = "\n} from './types';\n") {
            "  $it,"
        } +
        "\n" + WIRE_CONTRACT_DOC +
        "export interface WireContract extends Record<string, true> {\n" +
        members.joinToString("\n", transform = AssertionMember::render) +
        "\n}\n"
}

/**
 * Instantiations of one generic class collapse to one member: their keys are the class's, whatever the
 * type argument, and `types.ts` declares the class once.
 */
private fun assertionMembers(roots: List<SerialDescriptor>): List<AssertionMember> =
    wireReachableDescriptors(roots)
        .entries
        .groupBy({ it.key.substringBefore('<') }, { it.value })
        .toSortedMap()
        .filterKeys { it !in UNMODELLED_WIRE_TYPES }
        .filterValues { instantiations -> !instantiations.first().isInline }
        .flatMap { (serialName, instantiations) -> membersOf(serialName, instantiations.first()) }

private fun membersOf(
    serialName: String,
    descriptor: SerialDescriptor,
): List<AssertionMember> {
    val modelled = TYPESCRIPT_WIRE_TYPES[serialName] ?: serialName.substringAfterLast('.')
    val declared = { keys: List<String> -> keys - UNMODELLED_WIRE_FIELDS[serialName].orEmpty() }
    val enumValues = wireEnumValues(descriptor)
    return when {
        descriptor.kind == PolymorphicKind.SEALED -> variantMembers(serialName, descriptor, modelled, declared)
        enumValues != null ->
            listOf(
                AssertionMember(
                    key = serialName,
                    verdict = "ExactValues",
                    typeScriptType = modelled,
                    importedType = modelled,
                    names = enumValues,
                ),
            )
        else -> {
            val typeArguments = List(wireTypeParameterCount(descriptor)) { "unknown" }
            val typeScriptType = if (typeArguments.isEmpty()) modelled else "$modelled<${typeArguments.joinToString()}>"
            listOf(
                AssertionMember(
                    key = serialName,
                    verdict = "ExactFields",
                    typeScriptType = typeScriptType,
                    importedType = modelled,
                    names = declared(wireKeys(descriptor)),
                ),
            )
        }
    }
}

private fun variantMembers(
    serialName: String,
    descriptor: SerialDescriptor,
    modelled: String,
    declared: (List<String>) -> List<String>,
): List<AssertionMember> {
    val discriminator = sealedDiscriminator(descriptor)
    // `camelizeKeys` renames the discriminator key too, so a multi-word one would name no types.ts field.
    check('_' !in discriminator) { "$serialName: discriminator '$discriminator' would need camelizing here" }
    val variants = sealedVariants(descriptor)
    // Per-variant members name only the wire's variants, so an arm types.ts kept after one is dropped needs this.
    val armSet =
        AssertionMember(
            key = serialName,
            verdict = "ExactValues",
            typeScriptType = "$modelled['$discriminator']",
            importedType = modelled,
            names = variants.map { it.serialName },
        )
    return listOf(armSet) +
        variants.map { variant ->
            AssertionMember(
                key = "$serialName#${variant.serialName}",
                verdict = "ExactFields",
                typeScriptType = "Extract<$modelled, { $discriminator: '${variant.serialName}' }>",
                importedType = modelled,
                names = declared((listOf(discriminator) + wireKeys(variant.descriptor)).distinct()),
            )
        }
}

/** [names] are JSON keys for an `ExactFields` verdict and serial names for an `ExactValues` one. */
private data class AssertionMember(
    val key: String,
    val verdict: String,
    val typeScriptType: String,
    val importedType: String,
    val names: List<String>,
) {
    /** One name per line however few there are, so a key added on the wire is a one-line diff. */
    fun render(): String = "  '$key': $verdict<\n    $typeScriptType,\n$nameUnion\n  >;"

    private val nameUnion: String
        get() = if (names.isEmpty()) "    never" else names.joinToString("\n") { "    | '$it'" }
}

private fun wireKeys(descriptor: SerialDescriptor): List<String> =
    descriptor.elementIndices().map { wireElementName(descriptor, it) }

/** Flat, as an accounting question over a serial name needs: a hierarchy's discriminator and every variant's keys. */
internal fun wireFieldNames(descriptor: SerialDescriptor): Set<String> =
    if (descriptor.kind == PolymorphicKind.SEALED) {
        sealedVariants(descriptor).flatMapTo(mutableSetOf(sealedDiscriminator(descriptor))) { wireKeys(it.descriptor) }
    } else {
        wireKeys(descriptor).toSet()
    }
