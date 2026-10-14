package com.cwfgw.users

import com.cwfgw.config.AuthConfig
import com.cwfgw.db.Transactor
import io.github.oshai.kotlinlogging.KotlinLogging

private val log = KotlinLogging.logger {}

/**
 * Seed an initial admin user at boot if the users table is empty AND the
 * config supplies both `adminUsername` and `adminPassword`. We deliberately
 * never bake in a default password — if neither is set, log a warning and
 * skip; [com.cwfgw.config.AppConfig.load] refuses a config that sets only one.
 * The first deploy must set the env vars; later deploys are no-ops because
 * [UserRepository.countAll] returns > 0.
 */
suspend fun seedAdminIfEmpty(
    authService: AuthService,
    userRepository: UserRepository,
    tx: Transactor,
    config: AuthConfig,
) {
    if (tx.get { userRepository.countAll() } > 0) return

    val username = config.configuredAdminUsername
    val password = config.configuredAdminPassword
    if (username == null || password == null) {
        log.warn {
            "Users table is empty but auth.adminUsername / auth.adminPassword are not configured; " +
                "skipping admin seed. Set AUTH_ADMIN_USERNAME and AUTH_ADMIN_PASSWORD to bootstrap an admin."
        }
        return
    }

    val hash = authService.hashPassword(password.cleartext)
    tx.update {
        userRepository.create(NewUser(username = username, passwordHash = hash, role = UserRole.Admin))
    }
    log.info { "Seeded initial admin user '$username'" }
}
