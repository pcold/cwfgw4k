package com.cwfgw.testing

import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.leagues.FakeLeagueRepository
import com.cwfgw.leagues.League
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.Season
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament

/** Replaces the domain's repository slot, so rows an earlier write put in the one it held are dropped. */
fun ApiFixture.seedLeagues(vararg leagues: League) {
    leagueRepository = FakeLeagueRepository(initial = leagues.toList())
}

/** @see seedLeagues */
fun ApiFixture.seedGolfers(vararg golfers: Golfer) {
    golferRepository = FakeGolferRepository(initial = golfers.toList())
}

/** @see seedLeagues */
fun ApiFixture.seedSeasons(vararg seasons: Season) {
    seasonRepository = FakeSeasonRepository(initial = seasons.toList())
}

/** @see seedLeagues */
fun ApiFixture.seedTournaments(vararg tournaments: Tournament) {
    tournamentRepository = FakeTournamentRepository(initial = tournaments.toList())
}
