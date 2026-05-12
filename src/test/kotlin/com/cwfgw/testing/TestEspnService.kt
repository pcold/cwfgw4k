package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnService
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentRepository

/**
 * Build an [EspnService] for specs. Repositories default to empty fakes
 * so route/HTTP specs that don't care about ESPN state only need to
 * pass the [EspnClient] (typically a [com.cwfgw.espn.FakeEspnClient]).
 * Specs that exercise the import or preview paths should pass their
 * own repo instances so the gathers see the same state as the rest of
 * the fixture.
 */
@Suppress("LongParameterList")
internal fun testEspnService(
    client: EspnClient,
    seasonRepository: SeasonRepository = FakeSeasonRepository(),
    golferRepository: GolferRepository = FakeGolferRepository(),
    teamRepository: TeamRepository = FakeTeamRepository(),
    tournamentRepository: TournamentRepository = FakeTournamentRepository(),
    linkRepository: TournamentLinkRepository = FakeTournamentLinkRepository(),
    tx: Transactor = FakeTransactor(),
): EspnService =
    EspnService(
        client = client,
        seasonRepository = seasonRepository,
        golferRepository = golferRepository,
        teamRepository = teamRepository,
        tournamentRepository = tournamentRepository,
        linkRepository = linkRepository,
        tx = tx,
    )
