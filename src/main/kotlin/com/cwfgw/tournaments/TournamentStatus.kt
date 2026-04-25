package com.cwfgw.tournaments

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * The lifecycle states a tournament can be in. Modeled as an `enum class`
 * (not a sealed interface) for the same reason as [com.cwfgw.users.UserRole]:
 * statuses are pure tags with no per-variant state, and exhaustive `when`
 * branches are the primary use site. The wire/DB representation stays the
 * same string values previously used (`"upcoming"` / `"in_progress"` /
 * `"completed"`), preserving compatibility with the existing API consumers
 * and SQL fixtures.
 */
@Serializable(with = TournamentStatusSerializer::class)
enum class TournamentStatus(val value: String) {
    Upcoming("upcoming"),
    InProgress("in_progress"),
    Completed("completed"),
    ;

    companion object {
        /**
         * Parse a wire/DB string into a status, returning `null` for
         * unrecognized input. Routes use this to reject malformed
         * `?status=` query parameters at the HTTP boundary; the
         * repository uses it to translate column values, asserting
         * non-null because the DB is write-controlled by us.
         */
        fun fromValueOrNull(value: String): TournamentStatus? = entries.firstOrNull { it.value == value }
    }
}

internal object TournamentStatusSerializer : KSerializer<TournamentStatus> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.cwfgw.tournaments.TournamentStatus", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: TournamentStatus,
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): TournamentStatus {
        val raw = decoder.decodeString()
        return TournamentStatus.fromValueOrNull(raw)
            ?: throw IllegalArgumentException("Unknown tournament status: '$raw'")
    }
}
