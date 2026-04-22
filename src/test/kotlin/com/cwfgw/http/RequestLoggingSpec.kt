package com.cwfgw.http

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class RequestLoggingSpec : FunSpec({

    test("normalizeRoute passes simple paths through unchanged") {
        normalizeRoute("/api/v1/leagues") shouldBe "/api/v1/leagues"
    }

    test("normalizeRoute collapses a UUID segment to :id") {
        normalizeRoute("/api/v1/leagues/00000000-0000-0000-0000-000000000001") shouldBe
            "/api/v1/leagues/:id"
    }

    test("normalizeRoute collapses multiple UUID segments on one path") {
        normalizeRoute(
            "/api/v1/seasons/11111111-1111-1111-1111-111111111111/teams/22222222-2222-2222-2222-222222222222",
        ) shouldBe "/api/v1/seasons/:id/teams/:id"
    }

    test("normalizeRoute collapses a pure numeric segment to :n") {
        normalizeRoute("/api/v1/items/42") shouldBe "/api/v1/items/:n"
    }

    test("normalizeRoute collapses consecutive numeric segments independently") {
        normalizeRoute("/a/1/2/3") shouldBe "/a/:n/:n/:n"
    }

    test("normalizeRoute buckets /assets/* regardless of suffix") {
        normalizeRoute("/assets/index-abc123.js") shouldBe "/assets/*"
        normalizeRoute("/assets/vendor.d41d8cd9.css") shouldBe "/assets/*"
    }

    test("normalizeRoute does not touch alphanumeric segments that happen to contain digits") {
        normalizeRoute("/api/v1/items/abc123") shouldBe "/api/v1/items/abc123"
    }
})
