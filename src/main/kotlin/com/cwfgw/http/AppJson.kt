package com.cwfgw.http

import kotlinx.serialization.json.Json

/**
 * Shared JSON config used by Ktor's [io.ktor.server.plugins.contentnegotiation.ContentNegotiation]
 * plugin AND by anything that needs to pre-serialize a domain value to the
 * exact byte sequence the framework would emit (e.g. the request cache,
 * which stores serialized response bodies and replays them on hit).
 *
 * A JSON key is the property's serial name unrenamed: the UI's types spell the same names and convert none,
 * so a naming strategy here renames every key out from under them.
 */
val appJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }
