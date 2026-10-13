package com.cwfgw.testing

import com.cwfgw.result.Result
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

private data class TestError(val reason: String)

class ResultAssertionsSpec : FunSpec({

    test("orFail on Ok returns the value") {
        val outcome: Result<Int, TestError> = Result.Ok(42)

        outcome.orFail() shouldBe 42
    }

    test("orFail on Err fails the test with a message that carries the error") {
        val outcome: Result<Int, TestError> = Result.Err(TestError("golfer 7 is not on the roster"))

        val failure = shouldThrow<IllegalStateException> { outcome.orFail() }

        failure.message shouldContain "golfer 7 is not on the roster"
    }
})
