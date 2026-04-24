package com.cwfgw.espn

import java.time.LocalDate

/**
 * Test double for [EspnClient]. Returns canned tournaments per date and can be
 * configured to simulate an upstream failure. Tracks invocations for
 * call-count assertions.
 */
class FakeEspnClient(
    private val tournamentsByDate: Map<LocalDate, List<EspnTournament>> = emptyMap(),
    private val upstreamError: EspnUpstreamException? = null,
) : EspnClient {
    val fetchCalls = mutableListOf<LocalDate>()

    override suspend fun fetchScoreboard(date: LocalDate): List<EspnTournament> {
        fetchCalls += date
        if (upstreamError != null) throw upstreamError
        return tournamentsByDate[date] ?: emptyList()
    }
}
