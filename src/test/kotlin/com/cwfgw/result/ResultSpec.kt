package com.cwfgw.result

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

private sealed interface TestError {
    data object Missing : TestError

    data class Malformed(val reason: String) : TestError
}

class ResultSpec : FunSpec({

    test("getOrElse on Ok returns the value and does not call the handler") {
        var handlerCalls = 0
        val out =
            Result.Ok<Int>(42).getOrElse {
                handlerCalls += 1
                -1
            }

        out shouldBe 42
        handlerCalls shouldBe 0
    }

    test("getOrElse on Err invokes the handler with the error") {
        val out: Int =
            (Result.Err(TestError.Malformed("nope")) as Result<Int, TestError>).getOrElse { err ->
                when (err) {
                    TestError.Missing -> 0
                    is TestError.Malformed -> err.reason.length
                }
            }

        out shouldBe 4
    }

    test("map on Ok transforms the value") {
        val out: Result<String, TestError> = Result.Ok<Int>(3).map { "x".repeat(it) }

        out shouldBe Result.Ok("xxx")
    }

    test("map on Err passes through without invoking the transform") {
        var transformCalls = 0
        val out: Result<Int, TestError> =
            (Result.Err(TestError.Missing) as Result<Int, TestError>).map {
                transformCalls += 1
                it + 1
            }

        out shouldBe Result.Err(TestError.Missing)
        transformCalls shouldBe 0
    }

    test("flatMap chains Ok into a new Result") {
        val out: Result<Int, TestError> = Result.Ok<Int>(3).flatMap { Result.Ok(it * 2) }

        out shouldBe Result.Ok(6)
    }

    test("flatMap on Ok can short-circuit to Err") {
        val out: Result<Int, TestError> =
            Result.Ok<Int>(3).flatMap { Result.Err(TestError.Malformed("stop")) }

        out shouldBe Result.Err(TestError.Malformed("stop"))
    }

    test("flatMap on Err passes through without invoking the transform") {
        var transformCalls = 0
        val out: Result<Int, TestError> =
            (Result.Err(TestError.Missing) as Result<Int, TestError>).flatMap {
                transformCalls += 1
                Result.Ok(it + 1)
            }

        out shouldBe Result.Err(TestError.Missing)
        transformCalls shouldBe 0
    }
})
