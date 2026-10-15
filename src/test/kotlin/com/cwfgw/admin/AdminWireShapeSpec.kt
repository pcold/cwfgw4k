package com.cwfgw.admin

import com.cwfgw.golfers.GolferId
import com.cwfgw.http.appJson
import io.kotest.core.spec.style.FunSpec
import io.kotest.datatest.withData
import io.kotest.matchers.shouldBe
import kotlinx.serialization.KSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

private const val FLEETWOOD_UUID = "00000000-0000-0000-0000-000000000100"
private val FLEETWOOD_ID = GolferId(UUID.fromString(FLEETWOOD_UUID))

/** The UI switches on each sealed type's `type` tag, so a variant's tag and field names are the contract. */
class AdminWireShapeSpec : FunSpec({

    context("a pick match encodes under its type tag and decodes back") {
        withData(
            mapOf<String, WireCase<PickMatch>>(
                "matched" to
                    WireCase(
                        PickMatch.Matched(FLEETWOOD_ID, "Tommy Fleetwood"),
                        """{"type": "matched", "golfer_id": "$FLEETWOOD_UUID", "golfer_name": "Tommy Fleetwood"}""",
                    ),
                "matched_espn" to
                    WireCase(
                        PickMatch.MatchedEspn(espnId = "300", golferName = "Ludvig Aberg"),
                        """{"type": "matched_espn", "espn_id": "300", "golfer_name": "Ludvig Aberg"}""",
                    ),
                "matched_backfill" to
                    WireCase(
                        PickMatch.MatchedBackfill(espnId = "100", golferName = "Tommy Fleetwood"),
                        """{"type": "matched_backfill", "espn_id": "100", "golfer_name": "Tommy Fleetwood"}""",
                    ),
                "ambiguous" to
                    WireCase(
                        PickMatch.Ambiguous(
                            listOf(
                                GolferCandidate.Existing(FLEETWOOD_ID, "Tommy Fleetwood"),
                                GolferCandidate.Espn(espnId = "200", name = "Tom Fleetwood"),
                            ),
                        ),
                        """
                        {"type": "ambiguous", "candidates": [
                            {"type": "existing", "golfer_id": "$FLEETWOOD_UUID", "name": "Tommy Fleetwood"},
                            {"type": "espn", "espn_id": "200", "name": "Tom Fleetwood"}
                        ]}
                        """,
                    ),
                "no_match" to WireCase(PickMatch.NoMatch, """{"type": "no_match"}"""),
            ),
        ) { case -> case.shouldRoundTrip(PickMatch.serializer()) }
    }

    context("a golfer candidate encodes under its type tag and decodes back") {
        withData(
            mapOf<String, WireCase<GolferCandidate>>(
                "existing" to
                    WireCase(
                        GolferCandidate.Existing(FLEETWOOD_ID, "Tommy Fleetwood"),
                        """{"type": "existing", "golfer_id": "$FLEETWOOD_UUID", "name": "Tommy Fleetwood"}""",
                    ),
                "espn" to
                    WireCase(
                        GolferCandidate.Espn(espnId = "200", name = "Tom Fleetwood"),
                        """{"type": "espn", "espn_id": "200", "name": "Tom Fleetwood"}""",
                    ),
            ),
        ) { case -> case.shouldRoundTrip(GolferCandidate.serializer()) }
    }

    context("a golfer assignment encodes under its type tag and decodes back") {
        withData(
            mapOf<String, WireCase<GolferAssignment>>(
                "existing" to
                    WireCase(
                        GolferAssignment.Existing(FLEETWOOD_ID),
                        """{"type": "existing", "golfer_id": "$FLEETWOOD_UUID"}""",
                    ),
                "new" to
                    WireCase(GolferAssignment.New("Tommy Fleetwood"), """{"type": "new", "name": "Tommy Fleetwood"}"""),
                "espn" to
                    WireCase(
                        GolferAssignment.Espn(espnId = "100", name = "Tommy Fleetwood"),
                        """{"type": "espn", "espn_id": "100", "name": "Tommy Fleetwood"}""",
                    ),
            ),
        ) { case -> case.shouldRoundTrip(GolferAssignment.serializer()) }
    }
})

private data class WireCase<T>(val value: T, val json: String)

private fun <T> WireCase<T>.shouldRoundTrip(serializer: KSerializer<T>) {
    appJson.encodeToJsonElement(serializer, value) shouldBe Json.parseToJsonElement(json)
    appJson.decodeFromString(serializer, json) shouldBe value
}
