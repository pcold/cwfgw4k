package com.cwfgw.config

import com.sksamuel.hoplite.ConfigFailure
import com.sksamuel.hoplite.ConfigResult
import com.sksamuel.hoplite.DecoderContext
import com.sksamuel.hoplite.Node
import com.sksamuel.hoplite.PrimitiveNode
import com.sksamuel.hoplite.decoder.NullHandlingDecoder
import com.sksamuel.hoplite.fp.invalid
import com.sksamuel.hoplite.fp.valid
import kotlin.reflect.KType

/**
 * [toString] is masked, so a config object in a log line or exception message never prints the secret.
 * Named [cleartext], not `value` like Hoplite's `Secret` and every domain id, so each unwrap greps as one chokepoint.
 */
@JvmInline
value class ConfigSecret(val cleartext: String) {
    fun isBlank(): Boolean = cleartext.isBlank()

    fun isNotBlank(): Boolean = cleartext.isNotBlank()

    override fun toString(): String = MASK
}

private const val MASK = "****"

/**
 * Without it, Hoplite's inline-class fallback reports a nested node under a secret key with the node's
 * contents printed. Takes every primitive, not only strings, because `DecodeError` prints a refused one.
 * A blank string stays a blank secret, never a failure or null: what blank means belongs to each setting's reader.
 */
internal class ConfigSecretDecoder : NullHandlingDecoder<ConfigSecret> {
    override fun supports(type: KType): Boolean = type.classifier == ConfigSecret::class

    override fun safeDecode(
        node: Node,
        type: KType,
        context: DecoderContext,
    ): ConfigResult<ConfigSecret> =
        when (node) {
            is PrimitiveNode -> ConfigSecret(node.value.toString()).valid()
            else -> ConfigFailure.DecodeError(node, type).invalid()
        }
}
