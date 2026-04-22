package com.cwfgw.http

import io.ktor.server.routing.RoutingContext

/**
 * Extract an optional query parameter, running [parser] against the raw value
 * if one was provided. If [parser] rejects the value (returns null), throws
 * [DomainError.Validation] rather than silently treating the malformed input
 * as "not provided" — a missing parameter and a garbage parameter are
 * different and only the former should map to null.
 */
internal fun <T : Any> RoutingContext.optionalQueryParam(
    name: String,
    parser: (String) -> T?,
): T? =
    call.request.queryParameters[name]?.let { raw ->
        parser(raw) ?: throw DomainError.Validation("invalid query parameter $name: $raw")
    }
