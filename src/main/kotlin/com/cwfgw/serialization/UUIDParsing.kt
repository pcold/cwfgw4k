package com.cwfgw.serialization

import java.util.UUID

/**
 * Parse a string as a UUID, returning null on bad input. Deliberately silent —
 * unlike most caught-and-translated exceptions in this codebase, we don't log
 * the IllegalArgumentException at WARN here because (a) every per-resource
 * route uses this against path params, so any URL crawler hitting random paths
 * would flood the log, and (b) the exception's stack and message are
 * informationally identical every call ("Invalid UUID string: <raw>") and the
 * raw value is already echoed in the eventual 400 response. The standard
 * "log at WARN with throwable" rule applies elsewhere; this is the carve-out.
 */
fun String.toUUIDOrNull(): UUID? =
    try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }
