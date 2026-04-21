package com.cwfgw.health

fun interface HealthProbe {
    suspend fun isDatabaseConnected(): Boolean
}
