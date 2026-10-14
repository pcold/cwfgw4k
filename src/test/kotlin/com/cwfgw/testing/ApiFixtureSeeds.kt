package com.cwfgw.testing

import com.cwfgw.drafts.Draft
import com.cwfgw.drafts.DraftPick
import com.cwfgw.drafts.DraftService
import com.cwfgw.drafts.FakeDraftRepository
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.Golfer
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.FakeLeagueRepository
import com.cwfgw.leagues.League
import com.cwfgw.leagues.LeagueService
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.Season
import com.cwfgw.seasons.SeasonService
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.Tournament
import com.cwfgw.tournaments.TournamentService

/**
 * Replaces the domain's service, not its repository slot: services the fixture already built over the
 * default repository, such as [ApiFixture.adminService], don't see these rows.
 */
fun ApiFixture.seedLeagues(vararg leagues: League) {
    leagueService = LeagueService(FakeLeagueRepository(initial = leagues.toList()), transactor)
}

/** @see seedLeagues */
fun ApiFixture.seedGolfers(vararg golfers: Golfer) {
    golferService = GolferService(FakeGolferRepository(initial = golfers.toList()), transactor)
}

/** @see seedLeagues */
fun ApiFixture.seedSeasons(vararg seasons: Season) {
    seasonService = SeasonService(FakeSeasonRepository(initial = seasons.toList()), transactor)
}

/** @see seedLeagues */
fun ApiFixture.seedTournaments(vararg tournaments: Tournament) {
    tournamentService = TournamentService(FakeTournamentRepository(initial = tournaments.toList()), transactor)
}

/** @see seedLeagues */
fun ApiFixture.seedDrafts(
    drafts: List<Draft>,
    picks: List<DraftPick> = emptyList(),
) {
    val repository = FakeDraftRepository(initialDrafts = drafts, initialPicks = picks)
    draftService = DraftService(repository, teamRepository, transactor)
}
