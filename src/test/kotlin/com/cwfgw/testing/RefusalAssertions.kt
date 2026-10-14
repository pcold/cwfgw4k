package com.cwfgw.testing

import io.kotest.matchers.shouldBe
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Read from the raw JSON, not decoded into `RefusalBody`: decoding through the class the server encodes
 * with still passes when a rename moves the wire key a client branches on.
 */
internal suspend fun HttpResponse.shouldBeRefused(code: String) {
    status shouldBe HttpStatusCode.Conflict
    Json.parseToJsonElement(bodyAsText()).jsonObject["refusal"]?.jsonPrimitive?.content shouldBe code
}
