package com.cwfgw.admin

import com.cwfgw.db.Transactor
import com.cwfgw.espn.EspnService
import com.cwfgw.espn.FakeEspnClient
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.GolferService
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.result.Result
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.seasons.SeasonService
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.teams.TeamService
import com.cwfgw.testing.postgresHarness
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import kotlinx.coroutines.runBlocking

/**
 * Real-DB coverage for [AdminService.confirmRoster]. Validates the
 * transactional invariant — happy paths persist team + roster + new
 * golfers atomically; a mid-loop failure rolls back every prior write.
 * Fakes can't honestly model rollback so this spec uses [postgresHarness].
 */
class AdminServiceConfirmRosterSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl)
    val golferRepo = GolferRepository()

    fun newService(): AdminService {
        val leagueRepo = LeagueRepository(postgres.dsl)
        val seasonRepo = SeasonRepository()
        val tournamentRepo = TournamentRepository()
        val teamRepo = TeamRepository()
        val seasonService = SeasonService(seasonRepo, tx)
        val tournamentService = TournamentService(tournamentRepo, tx)
        val golferService = GolferService(golferRepo, tx)
        val teamService = TeamService(teamRepo, tx)
        val espnService =
            EspnService(
                client = FakeEspnClient(),
                tournamentService = tournamentService,
                golferService = golferService,
                teamService = teamService,
                seasonService = seasonService,
                tournamentLinkService =
                    TournamentLinkService(
                        TournamentLinkRepository(),
                        tournamentService,
                        golferService,
                        tx,
                    ),
            )
        // Bootstrap a league + season so confirmRoster has a target.
        val league =
            runBlocking {
                leagueRepo.create(CreateLeagueRequest(name = "Castlewood"))
            }
        runBlocking {
            tx.update {
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = league.id, name = "2026 Spring", seasonYear = 2026),
                )
            }
        }
        return AdminService(
            dsl = postgres.dsl,
            seasonService = seasonService,
            tournamentService = tournamentService,
            espnService = espnService,
            golferService = golferService,
            golferRepository = golferRepo,
            teamRepository = teamRepo,
        )
    }

    fun seasonId() =
        runBlocking {
            tx.read { SeasonRepository().findAll(leagueId = null, seasonYear = null) }.single().id
        }

    fun teamRepo() = TeamRepository()

    test("Existing-only assignments persist team + roster atomically") {
        val service = newService()
        val sid = seasonId()
        val scottie =
            runBlocking {
                tx.update {
                    golferRepo.create(
                        CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"),
                    )
                }
            }
        val rory =
            runBlocking {
                tx.update {
                    golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
                }
            }

        val result =
            runBlocking {
                service.confirmRoster(
                    ConfirmRosterRequest(
                        seasonId = sid,
                        teams =
                            listOf(
                                ConfirmedTeam(
                                    teamNumber = 1,
                                    teamName = "BROWN",
                                    picks =
                                        listOf(
                                            ConfirmedPick(
                                                round = 1,
                                                ownershipPct = 75,
                                                assignment = GolferAssignment.Existing(scottie.id),
                                            ),
                                            ConfirmedPick(
                                                round = 2,
                                                ownershipPct = 50,
                                                assignment = GolferAssignment.Existing(rory.id),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                )
            }

        val body = result.shouldBeInstanceOf<Result.Ok<RosterUploadResult>>().value
        body.teamsCreated shouldBe 1
        body.golfersCreated shouldBe 0
        val team = body.teams.single()
        team.teamName shouldBe "BROWN"

        val roster = runBlocking { tx.read { teamRepo().getRoster(team.id) } }
        roster shouldHaveSize 2
        roster.map { it.golferId } shouldContainExactly listOf(scottie.id, rory.id)
    }

    test("New assignments insert fresh golfers and link them to the roster in one transaction") {
        val service = newService()
        val sid = seasonId()

        val result =
            runBlocking {
                service.confirmRoster(
                    ConfirmRosterRequest(
                        seasonId = sid,
                        teams =
                            listOf(
                                ConfirmedTeam(
                                    teamNumber = 1,
                                    teamName = "BROWN",
                                    picks =
                                        listOf(
                                            ConfirmedPick(
                                                round = 1,
                                                ownershipPct = 100,
                                                assignment =
                                                    GolferAssignment.New("Scottie", "Scheffler"),
                                            ),
                                        ),
                                ),
                            ),
                    ),
                )
            }

        val body = result.shouldBeInstanceOf<Result.Ok<RosterUploadResult>>().value
        body.golfersCreated shouldBe 1
        val created =
            runBlocking { tx.read { golferRepo.findAll(activeOnly = false, search = null) } }
                .single { it.firstName == "Scottie" && it.lastName == "Scheffler" }
        runBlocking { tx.read { teamRepo().getRoster(body.teams.single().id) } }
            .single()
            .golferId shouldBe created.id
    }

    test("a failure mid-loop rolls the whole confirmRoster transaction back — no partial team or golfer rows") {
        val service = newService()
        val sid = seasonId()
        // teams has UNIQUE (season_id, team_name). Pre-insert "WOMBLE" so the
        // second team in the request collides and the INSERT fails.
        runBlocking {
            tx.update {
                teamRepo().create(
                    seasonId = sid,
                    request =
                        CreateTeamRequest(
                            ownerName = "Pre-existing",
                            teamName = "WOMBLE",
                            teamNumber = 99,
                        ),
                )
            }
        }

        shouldThrow<Exception> {
            runBlocking {
                service.confirmRoster(
                    ConfirmRosterRequest(
                        seasonId = sid,
                        teams =
                            listOf(
                                ConfirmedTeam(
                                    teamNumber = 1,
                                    teamName = "BROWN",
                                    picks =
                                        listOf(
                                            ConfirmedPick(
                                                round = 1,
                                                ownershipPct = 100,
                                                assignment =
                                                    GolferAssignment.New("First", "Golfer"),
                                            ),
                                        ),
                                ),
                                ConfirmedTeam(
                                    teamNumber = 2,
                                    teamName = "WOMBLE",
                                    picks = emptyList(),
                                ),
                            ),
                    ),
                )
            }
        }

        val teams = runBlocking { tx.read { teamRepo().findBySeason(sid) } }
        teams.map { it.teamName } shouldContainExactly listOf("WOMBLE")
        val golfers =
            runBlocking { tx.read { golferRepo.findAll(activeOnly = false, search = null) } }
        golfers.filter { it.firstName == "First" }.shouldBeEmpty()
    }
})
