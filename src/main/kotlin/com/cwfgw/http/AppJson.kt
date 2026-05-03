package com.cwfgw.http

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

/**
 * Shared JSON config used by Ktor's [io.ktor.server.plugins.contentnegotiation.ContentNegotiation]
 * plugin AND by anything that needs to pre-serialize a domain value to the
 * exact byte sequence the framework would emit (e.g. the request cache,
 * which stores serialized response bodies and replays them on hit).
 *
 * SnakeCase here is load-bearing: domain types declare camelCase property
 * names but the API contract is snake_case.
 */
@OptIn(ExperimentalSerializationApi::class)
val appJson: Json =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }
