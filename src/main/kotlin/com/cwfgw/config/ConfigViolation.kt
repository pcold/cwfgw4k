package com.cwfgw.config

/**
 * [key] and [requirement] are fixed per variant, never built from what was configured, so a refusal names the
 * setting and cannot print a secret's value.
 */
sealed interface ConfigViolation {
    val key: String
    val requirement: String

    /** Blank would sign every session cookie with an empty HMAC key, which anyone can forge. */
    data object BlankSessionSecret : ConfigViolation {
        override val key: String = "auth.sessionSecret"
        override val requirement: String = "must be set (generate with: openssl rand -hex 32)"
    }

    /** Keyed on the half that is missing. Neither set is valid: the seeder then skips with a warning. */
    data object AdminPasswordWithoutUsername : ConfigViolation {
        override val key: String = "auth.adminUsername"
        override val requirement: String = "must be set when auth.adminPassword is"
    }

    /** @see AdminPasswordWithoutUsername */
    data object AdminUsernameWithoutPassword : ConfigViolation {
        override val key: String = "auth.adminPassword"
        override val requirement: String = "must be set when auth.adminUsername is"
    }

    /** Hikari refuses a pool under one, and the Transactor's dispatcher is sized from it. */
    data object PoolSizeNotPositive : ConfigViolation {
        override val key: String = "db.maxPoolSize"
        override val requirement: String = "must be at least 1"
    }

    /** Held to [PositiveSeconds]' bounds: zero expires an entry as it is stored, and past the cap it overflows. */
    data object RequestCacheTtlOutOfRange : ConfigViolation {
        override val key: String = "cache.defaultTtlSeconds"
        override val requirement: String = SECONDS_REQUIREMENT
    }

    /** @see RequestCacheTtlOutOfRange */
    data object ScoreboardCacheTtlOutOfRange : ConfigViolation {
        override val key: String = "espn.scoreboardCacheTtlSeconds"
        override val requirement: String = SECONDS_REQUIREMENT
    }

    /** Mirrors [com.cwfgw.users.LoginThrottle]'s `require`, so a bad value names its key at load. */
    data object LoginThrottleMaxFailuresNotPositive : ConfigViolation {
        override val key: String = "loginThrottle.maxFailures"
        override val requirement: String = "must be at least 1"
    }

    /** @see RequestCacheTtlOutOfRange */
    data object LoginThrottleWindowOutOfRange : ConfigViolation {
        override val key: String = "loginThrottle.windowSeconds"
        override val requirement: String = SECONDS_REQUIREMENT
    }
}

private val SECONDS_REQUIREMENT: String = "must be between 1 and ${PositiveSeconds.MAX.value} seconds"

/** @see ConfigViolation */
class InvalidConfigException(val violations: List<ConfigViolation>) : IllegalStateException(
    violations.joinToString(
        separator = "\n",
        prefix = "Configuration refused, ${violations.size} violation(s):\n",
    ) { violation -> "  ${violation.key} ${violation.requirement}" },
)

internal fun AppConfig.authViolations(): List<ConfigViolation> =
    listOfNotNull(
        ConfigViolation.BlankSessionSecret.takeIf { auth.sessionSecret.isBlank() },
        adminViolation(),
    )

internal fun AppConfig.sizingViolations(): List<ConfigViolation> =
    listOfNotNull(
        ConfigViolation.PoolSizeNotPositive.takeIf { db.maxPoolSize < 1 },
        ConfigViolation.RequestCacheTtlOutOfRange.takeIf { PositiveSeconds.of(cache.defaultTtlSeconds) == null },
        ConfigViolation.ScoreboardCacheTtlOutOfRange.takeIf {
            PositiveSeconds.of(espn.scoreboardCacheTtlSeconds) == null
        },
        ConfigViolation.LoginThrottleMaxFailuresNotPositive.takeIf { loginThrottle.maxFailures < 1 },
        ConfigViolation.LoginThrottleWindowOutOfRange.takeIf {
            PositiveSeconds.of(loginThrottle.windowSeconds) == null
        },
    )

private fun AppConfig.adminViolation(): ConfigViolation? {
    val usernameSet = auth.configuredAdminUsername != null
    val passwordSet = auth.configuredAdminPassword != null
    return when {
        usernameSet && !passwordSet -> ConfigViolation.AdminUsernameWithoutPassword
        passwordSet && !usernameSet -> ConfigViolation.AdminPasswordWithoutUsername
        else -> null
    }
}

internal fun requireNoViolations(violations: List<ConfigViolation>) {
    if (violations.isNotEmpty()) throw InvalidConfigException(violations)
}
