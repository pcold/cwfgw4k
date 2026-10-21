package com.cwfgw.teams

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferId
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedTeams
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jooq.exception.DataAccessException
import java.math.BigDecimal
import java.util.UUID

class TeamRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = TeamRepository()
    val golferRepo = GolferRepository()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val seasonRepo = SeasonRepository()
    val leagueRepo = LeagueRepository()
    var seasonId = SeasonId(UUID.randomUUID())

    beforeEach {
        seasonId =
            tx.update {
                val league = leagueRepo.create(CreateLeagueRequest(name = "Castlewood Fantasy Golf"))
                seasonRepo.create(
                    CreateSeasonRequest(leagueId = league.id, name = "2026 Season", seasonYear = 2026),
                )
            }.id
    }

    test("create persists the team under a season") {
        tx.update {
            val created = repository.create(seasonId, NewTeam(ownerName = "Alice", teamName = "Eagles"))

            created.seasonId shouldBe seasonId
            created.ownerName shouldBe "Alice"
            created.teamName shouldBe "Eagles"
            repository.findBySeason(seasonId).map { it.id } shouldContainExactly listOf(created.id)
        }
    }

    test("findBySeason orders by team_number asc nulls last, then team_name") {
        tx.update {
            val unnumberedLater = repository.create(seasonId, NewTeam("Carol", "Wolves"))
            val numbered2 = repository.create(seasonId, NewTeam("Bob", "Hawks", teamNumber = 2))
            val numbered1 = repository.create(seasonId, NewTeam("Alice", "Eagles", teamNumber = 1))
            val unnumberedEarly = repository.create(seasonId, NewTeam("Dave", "Bears"))

            val result = repository.findBySeason(seasonId)

            result.map { it.id } shouldContainExactly
                listOf(numbered1.id, numbered2.id, unnumberedEarly.id, unnumberedLater.id)
        }
    }

    test("addToRoster applies default acquired_via and ownership_pct") {
        tx.update {
            val team = repository.create(seasonId, NewTeam("Alice", "Eagles"))
            val golfer = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))

            val entry = repository.addToRoster(team.id, NewRosterEntry(golferId = golfer.id))

            entry.teamId shouldBe team.id
            entry.golferId shouldBe golfer.id
            entry.acquiredVia shouldBe "free_agent"
            entry.ownershipPct.compareTo(BigDecimal("100")) shouldBe 0
        }
    }

    suspend fun refusal(
        teamId: TeamId,
        entry: (GolferId) -> NewRosterEntry,
    ): String =
        shouldThrow<DataAccessException> {
            tx.update {
                val golfer = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
                repository.addToRoster(teamId, entry(golfer.id))
            }
        }.message.shouldNotBeNull()

    test("the schema refuses a draft round outside the draft, and admits both ends and two undrafted nulls") {
        val teamId = tx.update { seedTeams(seasonId) }.first()

        listOf(0, 9).forEach { round ->
            refusal(teamId) { golfer -> NewRosterEntry(golfer, draftRound = round) } shouldContain
                "team_rosters_draft_round_in_range"
        }
        val admitted =
            tx.update {
                listOf(1, 8, null, null).map { round ->
                    val golfer = golferRepo.create(CreateGolferRequest(firstName = "Round", lastName = "$round"))
                    repository.addToRoster(teamId, NewRosterEntry(golfer.id, draftRound = round))
                }
            }
        admitted.map { it.draftRound } shouldContainExactly listOf(1, 8, null, null)
    }

    test("the schema refuses an ownership of zero, below it, or past 100, and admits a sliver and the whole") {
        val teamId = tx.update { seedTeams(seasonId) }.first()

        listOf("0", "-50", "100.01").forEach { pct ->
            refusal(teamId) { golfer -> NewRosterEntry(golfer, ownershipPct = BigDecimal(pct)) } shouldContain
                "team_rosters_ownership_pct_in_range"
        }
        val admitted =
            tx.update {
                listOf("0.01", "100").map { pct ->
                    val golfer = golferRepo.create(CreateGolferRequest(firstName = "Share", lastName = pct))
                    repository.addToRoster(teamId, NewRosterEntry(golfer.id, ownershipPct = BigDecimal(pct)))
                }
            }
        admitted.map { it.ownershipPct.toPlainString() } shouldContainExactly listOf("0.01", "100.00")
    }

    test("findRostersBySeason orders a team's entries by draft_round asc nulls last") {
        tx.update {
            val team = repository.create(seasonId, NewTeam("Alice", "Eagles"))
            val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
            val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
            val phil = golferRepo.create(CreateGolferRequest(firstName = "Phil", lastName = "Mickelson"))

            val roundedScottie =
                repository.addToRoster(
                    team.id,
                    NewRosterEntry(golferId = scottie.id, draftRound = 1, acquiredVia = "draft"),
                )
            val roundedRory =
                repository.addToRoster(
                    team.id,
                    NewRosterEntry(golferId = rory.id, draftRound = 2, acquiredVia = "draft"),
                )
            val unrounded = repository.addToRoster(team.id, NewRosterEntry(golferId = phil.id))

            val roster = repository.findRostersBySeason(seasonId)

            roster.map { it.id } shouldContainExactly listOf(roundedScottie.id, roundedRory.id, unrounded.id)
        }
    }

    test("findRostersBySeason returns entries across every team in the season") {
        tx.update {
            val alice = repository.create(seasonId, NewTeam("Alice", "Eagles"))
            val bob = repository.create(seasonId, NewTeam("Bob", "Hawks"))
            val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
            val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
            val phil = golferRepo.create(CreateGolferRequest(firstName = "Phil", lastName = "Mickelson"))

            repository.addToRoster(alice.id, NewRosterEntry(golferId = rory.id, draftRound = 1))
            repository.addToRoster(alice.id, NewRosterEntry(golferId = scottie.id, draftRound = 2))
            repository.addToRoster(bob.id, NewRosterEntry(golferId = phil.id, draftRound = 1))

            val rosters = repository.findRostersBySeason(seasonId)

            rosters.map { it.golferId } shouldContainExactlyInAnyOrder listOf(rory.id, scottie.id, phil.id)
        }
    }

    test("findRostersBySeason excludes entries from other seasons") {
        val otherSeasonId =
            tx.update {
                seasonRepo.create(
                    CreateSeasonRequest(
                        leagueId =
                            leagueRepo.create(CreateLeagueRequest(name = "Sibling League")).id,
                        name = "Other 2026",
                        seasonYear = 2026,
                    ),
                )
            }.id

        tx.update {
            val team = repository.create(seasonId, NewTeam("Alice", "Eagles"))
            val otherTeam = repository.create(otherSeasonId, NewTeam("Carol", "Wolves"))
            val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
            val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
            repository.addToRoster(team.id, NewRosterEntry(golferId = rory.id))
            repository.addToRoster(otherTeam.id, NewRosterEntry(golferId = scottie.id))

            repository.findRostersBySeason(seasonId).map { it.golferId } shouldContainExactly listOf(rory.id)
        }
    }

    test("getRosterView groups picks by team, joining golfer names") {
        tx.update {
            val alice = repository.create(seasonId, NewTeam("Alice", "Eagles"))
            val bob = repository.create(seasonId, NewTeam("Bob", "Hawks"))
            val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
            val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))

            repository.addToRoster(
                alice.id,
                NewRosterEntry(golferId = rory.id, draftRound = 1, acquiredVia = "draft"),
            )
            repository.addToRoster(
                bob.id,
                NewRosterEntry(golferId = scottie.id, draftRound = 1, acquiredVia = "draft"),
            )

            val view = repository.getRosterView(seasonId)

            view shouldHaveSize 2
            view.first { it.teamId == alice.id }.picks.single().golferName shouldBe "Rory McIlroy"
            view.first { it.teamId == bob.id }.picks.single().golferName shouldBe "Scottie Scheffler"
        }
    }

    test("getRosterView orders teams by team_number asc nulls last and carries the number") {
        tx.update {
            // Created out of team_number order; the view must still come back numbered order.
            val hawks = repository.create(seasonId, NewTeam("Bob", "Hawks", teamNumber = 2))
            val eagles = repository.create(seasonId, NewTeam("Alice", "Eagles", teamNumber = 1))
            val bears = repository.create(seasonId, NewTeam("Dave", "Bears"))
            val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy"))
            val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
            val phil = golferRepo.create(CreateGolferRequest(firstName = "Phil", lastName = "Mickelson"))
            repository.addToRoster(
                hawks.id,
                NewRosterEntry(golferId = rory.id, draftRound = 1, acquiredVia = "draft"),
            )
            repository.addToRoster(
                eagles.id,
                NewRosterEntry(golferId = scottie.id, draftRound = 1, acquiredVia = "draft"),
            )
            repository.addToRoster(
                bears.id,
                NewRosterEntry(golferId = phil.id, draftRound = 1, acquiredVia = "draft"),
            )

            val view = repository.getRosterView(seasonId)

            view.map { it.teamId } shouldContainExactly listOf(eagles.id, hawks.id, bears.id)
            view.map { it.teamNumber } shouldContainExactly listOf(1, 2, null)
        }
    }
})
