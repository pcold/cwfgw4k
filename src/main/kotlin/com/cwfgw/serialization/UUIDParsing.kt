package com.cwfgw.serialization

import java.util.UUID

fun String.toUUIDOrNull(): UUID? =
    try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }
