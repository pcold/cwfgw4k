package com.cwfgw.users

import com.cwfgw.serialization.InstantSerializer
import com.cwfgw.serialization.UUIDSerializer
import com.cwfgw.serialization.toUUIDOrNull
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant
import java.util.UUID

@Serializable
@JvmInline
value class UserId(
    @Serializable(with = UUIDSerializer::class) val value: UUID,
)

fun UUID.toUserId(): UserId = UserId(this)

fun String.toUserId(): UserId? = toUUIDOrNull()?.toUserId()

/**
 * The set of recognized user roles. Stays as an enum (not sealed interface)
 * because roles are pure tags with no per-variant state — when permission
 * checks land they'll be exhaustive `when` branches over this set.
 *
 * [fromValue] defaults unrecognized DB values to [User] rather than failing
 * — fail-closed: if the DB grew a role we don't understand, the safer
 * outcome is "least privilege" rather than "treat as admin." Migrations
 * are write-controlled by us, so this is defense-in-depth for stray writes
 * rather than a regular code path.
 */
@Serializable(with = UserRoleSerializer::class)
enum class UserRole(val value: String) {
    Admin("admin"),
    User("user"),
    ;

    companion object {
        fun fromValue(value: String): UserRole = entries.firstOrNull { it.value == value } ?: User
    }
}

internal object UserRoleSerializer : KSerializer<UserRole> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("com.cwfgw.users.UserRole", PrimitiveKind.STRING)

    override fun serialize(
        encoder: Encoder,
        value: UserRole,
    ) {
        encoder.encodeString(value.value)
    }

    override fun deserialize(decoder: Decoder): UserRole = UserRole.fromValue(decoder.decodeString())
}

/**
 * Public user shape — never carries `password_hash`. The hash lives only in
 * the DB and in [NewUser] requests on the way in. Login lookups go through a
 * dedicated `findCredentials` method that returns the hash separately so this
 * type can never accidentally leak it.
 */
@Serializable
data class User(
    val id: UserId,
    val username: String,
    val role: UserRole,
    @Serializable(with = InstantSerializer::class) val createdAt: Instant,
)

/**
 * Repository-level insert payload. Already carries the BCrypt hash —
 * hashing is a service-level concern done before this type is constructed.
 * Keeping the field name `passwordHash` (not `password`) makes it impossible
 * to accidentally pass plaintext through.
 */
data class NewUser(
    val username: String,
    val passwordHash: String,
    val role: UserRole = UserRole.User,
)
