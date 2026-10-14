package com.cwfgw.http

import io.ktor.http.ContentType
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.install
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.CompressionConfig
import io.ktor.server.plugins.compression.condition
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.matchContentType
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.request.path

/** Under one TCP segment, gzip framing and CPU buy nothing. */
private const val MINIMUM_COMPRESSIBLE_SIZE = 1024L

private const val API_SEGMENT = "api"

/** No Brotli: Ktor ships no encoder for it. */
internal fun Application.installCompression() {
    install(Compression) {
        // The default mode also decodes request bodies with an unbounded maxDecodedContentLength.
        mode = CompressionConfig.Mode.CompressResponse
        gzip()
        deflate()
        condition { !mayReachApi() }
        // Default-refuse: a new body type ships raw until widened here. PNG is already deflated.
        matchContentType(
            ContentType.Text.Any,
            ContentType.Application.JavaScript,
            ContentType.Image.SVG,
        )
        minimumSize(MINIMUM_COMPRESSIBLE_SIZE)
    }
}

/**
 * `/api` stays uncompressed: a body echoing request input beside anything private is a BREACH oracle.
 * True on any `%` too, since routing percent-decodes (`/%61pi/v1/…` reaches the API) and this parse does not.
 */
private fun ApplicationCall.mayReachApi(): Boolean {
    val firstSegment = request.path().split('/').firstOrNull { it.isNotEmpty() } ?: return false
    return firstSegment == API_SEGMENT || firstSegment.contains('%')
}
