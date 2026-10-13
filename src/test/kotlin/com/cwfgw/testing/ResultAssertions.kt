package com.cwfgw.testing

import com.cwfgw.result.Result
import com.cwfgw.result.getOrElse

internal fun <T, E> Result<T, E>.orFail(): T = getOrElse { error("expected Result.Ok, got Result.Err($it)") }
