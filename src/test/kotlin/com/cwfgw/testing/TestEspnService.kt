package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnClient
import com.cwfgw.espn.EspnService
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.tournamentLinks.FakeTournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService

/**
 * Build an [EspnService] for specs that need one for downstream wiring but
 * don't directly exercise the previewByDate gather. The new repository
 * parameters default to empty fakes — fine for tests whose subject isn't
 * the live-overlay path. Specs that do test previewByDate (today only
 * [com.cwfgw.espn.EspnServiceSpec]) should pass their own repo instances
 * so the single-snapshot gather sees the same state as the rest of the
 * fixture.
 */
@Suppress("LongParameterList")
internal fun testEspnService(
    client: EspnClient,
    tournamentService: TournamentService,
    golferService: GolferService,
    teamService: TeamService,
    tournamentLinkService: TournamentLinkService,
    seasonRepository: SeasonRepository = FakeSeasonRepository(),
    golferRepository: GolferRepository = FakeGolferRepository(),
    teamRepository: TeamRepository = FakeTeamRepository(),
    tournamentRepository: TournamentRepository = FakeTournamentRepository(),
    linkRepository: TournamentLinkRepository = FakeTournamentLinkRepository(),
    tx: Transactor = FakeTransactor(),
): EspnService =
    EspnService(
        client = client,
        tournamentService = tournamentService,
        golferService = golferService,
        teamService = teamService,
        tournamentLinkService = tournamentLinkService,
        seasonRepository = seasonRepository,
        golferRepository = golferRepository,
        teamRepository = teamRepository,
        tournamentRepository = tournamentRepository,
        linkRepository = linkRepository,
        tx = tx,
    )
