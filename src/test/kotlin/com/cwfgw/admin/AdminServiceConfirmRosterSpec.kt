package com.cwfgw.admin

import com.cwfgw.db.Transactor
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
import com.cwfgw.testing.orFail
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.testEspnService
import com.cwfgw.tournamentLinks.TournamentLinkRepository
import com.cwfgw.tournamentLinks.TournamentLinkService
import com.cwfgw.tournaments.TournamentRepository
import com.cwfgw.tournaments.TournamentService
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.runBlocking

/**
 * Real-DB coverage for [AdminService.confirmRoster]. Validates the
 * transactional invariant — happy paths persist team + roster + new
 * golfers atomically; a mid-loop failure rolls back every prior write.
 * Uses [postgresHarness] because the invariant is Postgres's rollback; `FakeTransactor`'s would only prove itself.
 */
class AdminServiceConfirmRosterSpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val golferRepo = GolferRepository()

    fun newService(): AdminService {
        val leagueRepo = LeagueRepository()
        val seasonRepo = SeasonRepository()
        val tournamentRepo = TournamentRepository()
        val teamRepo = TeamRepository()
        val seasonService = SeasonService(seasonRepo, tx)
        val tournamentService = TournamentService(tournamentRepo, tx)
        val golferService = GolferService(golferRepo, tx)
        val linkRepo = TournamentLinkRepository()
        val espnService =
            testEspnService(
                client = FakeEspnClient(),
                seasonRepository = seasonRepo,
                golferRepository = golferRepo,
                teamRepository = teamRepo,
                tournamentRepository = tournamentRepo,
                linkRepository = linkRepo,
                tx = tx,
            )
        // Bootstrap a league + season so confirmRoster has a target.
        runBlocking {
            tx.update {
                val league = leagueRepo.create(CreateLeagueRequest(name = "Castlewood"))
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = league.id, name = "2026 Spring", seasonYear = 2026),
                )
            }
        }
        return AdminService(
            tx = tx,
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
        val golfers =
            runBlocking {
                tx.update {
                    DRAFT_ROUNDS.map { round ->
                        golferRepo.create(CreateGolferRequest(firstName = "Round", lastName = "Golfer$round"))
                    }
                }
            }
        val picks =
            DRAFT_ROUNDS.zip(golfers) { round, golfer ->
                ConfirmedPick(round = round, ownershipPct = 75, assignment = GolferAssignment.Existing(golfer.id))
            }

        val result =
            runBlocking {
                service.confirmRoster(
                    ConfirmRosterRequest(
                        seasonId = sid,
                        teams = listOf(ConfirmedTeam(teamNumber = 1, teamName = "BROWN", picks = picks)),
                    ),
                )
            }

        val body = result.orFail()
        body.teamsCreated shouldBe 1
        body.golfersCreated shouldBe 0
        val team = body.teams.single()
        team.teamName shouldBe "BROWN"

        val roster = runBlocking { tx.read { teamRepo().findRostersBySeason(sid) } }.filter { it.teamId == team.id }
        roster.map { it.golferId } shouldContainExactly golfers.map { it.id }
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
                                fullTeam(
                                    number = 1,
                                    name = "BROWN",
                                    ConfirmedPick(
                                        round = 1,
                                        ownershipPct = 100,
                                        assignment = GolferAssignment.New("Scottie Scheffler"),
                                    ),
                                ),
                            ),
                    ),
                )
            }

        val body = result.orFail()
        body.golfersCreated shouldBe DRAFT_ROUNDS.count()
        val created =
            runBlocking { tx.read { golferRepo.findAll(search = null) } }
                .single { it.firstName == "Scottie" && it.lastName == "Scheffler" }
        runBlocking { tx.read { teamRepo().findRostersBySeason(sid) } }
            .single { it.teamId == body.teams.single().id && it.draftRound == 1 }
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

        val result =
            runBlocking {
                service.confirmRoster(
                    ConfirmRosterRequest(
                        seasonId = sid,
                        teams =
                            listOf(
                                fullTeam(
                                    number = 1,
                                    name = "BROWN",
                                    ConfirmedPick(
                                        round = 1,
                                        ownershipPct = 100,
                                        assignment = GolferAssignment.New("First Golfer"),
                                    ),
                                ),
                                fullTeam(number = 2, name = "WOMBLE"),
                            ),
                    ),
                )
            }

        result shouldBe Result.Err(AdminError.TeamNameTaken(sid))
        val teams = runBlocking { tx.read { teamRepo().findBySeason(sid) } }
        teams.map { it.teamName } shouldContainExactly listOf("WOMBLE")
        val golfers =
            runBlocking { tx.read { golferRepo.findAll(search = null) } }
        golfers.filter { it.firstName == "First" }.shouldBeEmpty()
    }
})
