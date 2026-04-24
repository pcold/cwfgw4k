package com.cwfgw.testing

import com.cwfgw.AppServices
import com.cwfgw.drafts.DraftService
import com.cwfgw.drafts.FakeDraftRepository
import com.cwfgw.espn.EspnImportService
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.FakeGolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.health.HealthProbe
import com.cwfgw.leagues.FakeLeagueRepository
import com.cwfgw.leagues.LeagueService
import com.cwfgw.module
import com.cwfgw.scoring.FakeScoringRepository
import com.cwfgw.scoring.ScoringService
import com.cwfgw.seasons.FakeSeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.tournaments.FakeTournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.ktor.client.HttpClient
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.testing.ApplicationTestBuilder
import io.ktor.server.testing.testApplication
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

@OptIn(ExperimentalSerializationApi::class)
private val TEST_JSON =
    Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        namingStrategy = JsonNamingStrategy.SnakeCase
    }

/**
 * Mutable fixture that each test overrides in its `apiTest { ... }` configure block. Defaults every slot
 * to an empty fake-backed service so a spec only has to override the domain it's actually exercising.
 */
class ApiFixture {
    var healthProbe: HealthProbe = HealthProbe { true }
    var leagueService: LeagueService = LeagueService(FakeLeagueRepository())
    var golferService: GolferService = GolferService(FakeGolferRepository())
    var seasonService: SeasonService = SeasonService(FakeSeasonRepository())
    var teamService: TeamService = TeamService(FakeTeamRepository())
    var tournamentService: TournamentService = TournamentService(FakeTournamentRepository())
    var draftService: DraftService = DraftService(FakeDraftRepository(), teamService)
    var scoringService: ScoringService =
        ScoringService(
            repository = FakeScoringRepository(),
            seasonService = seasonService,
            tournamentService = tournamentService,
            teamService = teamService,
        )
    var espnImportService: EspnImportService =
        EspnImportService(
            client = FakeEspnClient(),
            tournamentService = tournamentService,
            golferService = golferService,
            teamService = teamService,
        )
}

/**
 * Spin up a Ktor test application with every domain wired to fake-backed services; the optional
 * `configure` block overrides specific services (typically the one under test), and `block` runs
 * with an HttpClient configured to use the same SnakeCase JSON serialization as production.
 */
fun apiTest(
    configure: ApiFixture.() -> Unit = {},
    block: suspend ApplicationTestBuilder.(HttpClient) -> Unit,
) {
    val fixture = ApiFixture().apply(configure)
    testApplication {
        application {
            module(
                AppServices(
                    healthProbe = fixture.healthProbe,
                    leagueService = fixture.leagueService,
                    golferService = fixture.golferService,
                    seasonService = fixture.seasonService,
                    teamService = fixture.teamService,
                    tournamentService = fixture.tournamentService,
                    draftService = fixture.draftService,
                    scoringService = fixture.scoringService,
                    espnImportService = fixture.espnImportService,
                ),
            )
        }
        val client =
            createClient {
                install(ContentNegotiation) {
                    json(TEST_JSON)
                }
            }
        block(client)
    }
}
