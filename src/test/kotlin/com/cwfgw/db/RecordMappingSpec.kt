package com.cwfgw.db

import com.cwfgw.jooq.tables.records.GolfersRecord
import com.cwfgw.jooq.tables.references.GOLFERS
import com.cwfgw.jooq.tables.references.TOURNAMENTS
import com.cwfgw.tournaments.TournamentStatus
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.util.UUID

class RecordMappingSpec : FunSpec({
    fun golfer(id: UUID?): GolfersRecord = GolfersRecord(id = id, firstName = "Scottie", lastName = "Scheffler")

    test("returns the value when the default-valued column is present") {
        val id = UUID.randomUUID()

        golfer(id).required(GOLFERS.ID) shouldBe id
    }

    test("throws naming the qualified column when the value is null") {
        val error = shouldThrow<ColumnNullViolation> { golfer(id = null).required(GOLFERS.ID) }

        error.message shouldBe "golfers.id was null"
    }

    test("returns the decoded value when the domain names the stored one") {
        val status = decoded(TOURNAMENTS.STATUS, "completed", TournamentStatus::fromValueOrNull)

        status shouldBe TournamentStatus.Completed
    }

    test("refuses a stored value outside the domain, naming the qualified column and nothing else") {
        val error =
            shouldThrow<ColumnDomainViolation> {
                decoded(TOURNAMENTS.STATUS, "rained_out", TournamentStatus::fromValueOrNull)
            }

        error.message shouldBe "tournaments.status held a value outside its domain"
    }

    test("carries the refused value as a field") {
        val error =
            shouldThrow<ColumnDomainViolation> {
                decoded(TOURNAMENTS.STATUS, "rained_out", TournamentStatus::fromValueOrNull)
            }

        error.stored shouldBe "rained_out"
    }
})
