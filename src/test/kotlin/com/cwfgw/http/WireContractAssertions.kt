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
internal fun wireContractAssertions(
    responded: List<SerialDescriptor>,
    received: List<SerialDescriptor>,
): String {
    val members = assertionMembers(responded = responded, received = received)
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
 * type argument, and `types.ts` declares the class once. Optionality is asserted only on a type no response
 * reaches: with `encodeDefaults` a response sends every key, so one type cannot be held to both.
 */
private fun assertionMembers(
    responded: List<SerialDescriptor>,
    received: List<SerialDescriptor>,
): List<AssertionMember> {
    val receivedOnly = serialNamesReached(received) - serialNamesReached(responded)
    return wireReachableDescriptors(responded + received)
        .entries
        .groupBy({ it.key.substringBefore('<') }, { it.value })
        .toSortedMap()
        .filterKeys { it !in UNMODELLED_WIRE_TYPES }
        .filterValues { instantiations -> !instantiations.first().isInline }
        .flatMap { (serialName, instantiations) ->
            membersOf(serialName, instantiations.first(), receivedOnly = serialName in receivedOnly)
        }
}

private fun serialNamesReached(roots: List<SerialDescriptor>): Set<String> =
    wireReachableDescriptors(roots).keys.mapTo(mutableSetOf()) { it.substringBefore('<') }

private fun membersOf(
    serialName: String,
    descriptor: SerialDescriptor,
    receivedOnly: Boolean,
): List<AssertionMember> {
    val modelled = TYPESCRIPT_WIRE_TYPES[serialName] ?: serialName.substringAfterLast('.')
    val excused = UNMODELLED_WIRE_FIELDS[serialName].orEmpty()
    val fields = { key: String, typeScriptType: String, keys: List<WireKey> ->
        fieldsMember(
            key = key,
            typeScriptType = typeScriptType,
            importedType = modelled,
            keys = keys.filterNot { it.name in excused },
            receivedOnly = receivedOnly,
        )
    }
    val enumValues = wireEnumValues(descriptor)
    return when {
        descriptor.kind == PolymorphicKind.SEALED -> variantMembers(serialName, descriptor, modelled, fields)
        enumValues != null ->
            listOf(
                AssertionMember(
                    key = serialName,
                    verdict = "ExactValues",
                    typeScriptType = modelled,
                    importedType = modelled,
                    unions = listOf(enumValues),
                ),
            )
        else -> {
            val typeArguments = List(wireTypeParameterCount(descriptor)) { "unknown" }
            val typeScriptType = if (typeArguments.isEmpty()) modelled else "$modelled<${typeArguments.joinToString()}>"
            listOf(fields(serialName, typeScriptType, wireKeys(descriptor)))
        }
    }
}

private fun variantMembers(
    serialName: String,
    descriptor: SerialDescriptor,
    modelled: String,
    fields: (key: String, typeScriptType: String, keys: List<WireKey>) -> AssertionMember,
): List<AssertionMember> {
    val discriminator = sealedDiscriminator(descriptor)
    val variants = sealedVariants(descriptor)
    // Per-variant members name only the wire's variants, so an arm types.ts kept after one is dropped needs this.
    val armSet =
        AssertionMember(
            key = serialName,
            verdict = "ExactValues",
            typeScriptType = "$modelled['$discriminator']",
            importedType = modelled,
            unions = listOf(variants.map { it.serialName }),
        )
    return listOf(armSet) +
        variants.map { variant ->
            val keys = listOf(WireKey(discriminator, optional = false)) + wireKeys(variant.descriptor)
            fields(
                "$serialName#${variant.serialName}",
                "Extract<$modelled, { $discriminator: '${variant.serialName}' }>",
                keys.distinctBy(WireKey::name),
            )
        }
}

/** A received type's verdict takes its required keys and its defaulted ones as two unions. */
private fun fieldsMember(
    key: String,
    typeScriptType: String,
    importedType: String,
    keys: List<WireKey>,
    receivedOnly: Boolean,
): AssertionMember {
    val names = { half: List<WireKey> -> half.map(WireKey::name) }
    val (optional, required) = keys.partition(WireKey::optional)
    return AssertionMember(
        key = key,
        verdict = if (receivedOnly) "ExactRequestFields" else "ExactFields",
        typeScriptType = typeScriptType,
        importedType = importedType,
        unions = if (receivedOnly) listOf(names(required), names(optional)) else listOf(names(keys)),
    )
}

/** [unions] are the verdict's type arguments after the type: JSON keys, or an enum's serial names. */
private data class AssertionMember(
    val key: String,
    val verdict: String,
    val typeScriptType: String,
    val importedType: String,
    val unions: List<List<String>>,
) {
    /** One name per line however few there are, so a key added on the wire is a one-line diff. */
    fun render(): String =
        "  '$key': $verdict<\n    $typeScriptType,\n${unions.joinToString(",\n", transform = ::union)}\n  >;"

    private fun union(names: List<String>): String =
        if (names.isEmpty()) "    never" else names.joinToString("\n") { "    | '$it'" }
}

/** [optional] is whether the Kotlin property has a default, so a decoder accepts the key's absence. */
private data class WireKey(val name: String, val optional: Boolean)

private fun wireKeys(descriptor: SerialDescriptor): List<WireKey> =
    descriptor.elementIndices().map { index ->
        WireKey(name = descriptor.getElementName(index), optional = descriptor.isElementOptional(index))
    }

/** Flat, as an accounting question over a serial name needs: a hierarchy's discriminator and every variant's keys. */
internal fun wireFieldNames(descriptor: SerialDescriptor): Set<String> =
    if (descriptor.kind == PolymorphicKind.SEALED) {
        sealedVariants(descriptor).flatMapTo(mutableSetOf(sealedDiscriminator(descriptor))) { variant ->
            wireKeys(variant.descriptor).map(WireKey::name)
        }
    } else {
        wireKeys(descriptor).mapTo(mutableSetOf(), WireKey::name)
    }
