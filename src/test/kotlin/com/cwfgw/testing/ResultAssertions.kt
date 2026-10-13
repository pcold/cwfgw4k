package com.cwfgw.testing

import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse

/**
 * Not an `AssertionError`: inside `shouldThrow<Exception> { }` the `IllegalStateException` from an
 * unexpected `Err` passes as the expected throw, so unwrap outside the block.
 */
internal fun <T, E> Result<T, E>.orFail(): T = getOrElse { error("expected Result.Ok, got Result.Err($it)") }
