package com.cwfgw.drafts

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.golfers.UpdateGolferRequest
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.seasons.CreateSeasonRequest
import com.cwfgw.seasons.SeasonId
import com.cwfgw.seasons.SeasonRepository
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.util.UUID

class DraftRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val repository = DraftRepository()
    val leagueRepo = LeagueRepository()
    val seasonRepo = SeasonRepository()
    val teamRepo = TeamRepository()
    val golferRepo = GolferRepository()
    val tx = Transactor(postgres.dsl)
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

    suspend fun teamIds(): List<TeamId> =
        tx.update {
            listOf("Alpha", "Bravo", "Charlie").map { name ->
                teamRepo.create(seasonId, CreateTeamRequest(ownerName = "Owner $name", teamName = name)).id
            }
        }

    test("create persists a draft with default status=pending and draftType=snake") {
        val created = tx.update { repository.create(seasonId, CreateDraftRequest()) }

        created.seasonId shouldBe seasonId
        created.status shouldBe "pending"
        created.draftType shouldBe "snake"
        created.startedAt.shouldBeNull()
    }

    test("findBySeason returns null when no draft exists") {
        tx.read { repository.findBySeason(seasonId) }.shouldBeNull()
    }

    test("findBySeason returns the created draft") {
        val created = tx.update { repository.create(seasonId, CreateDraftRequest()) }

        tx.read { repository.findBySeason(seasonId) } shouldBe created
    }

    test("updateStatus to in_progress sets started_at") {
        val created = tx.update { repository.create(seasonId, CreateDraftRequest()) }

        val updated = tx.update { repository.updateStatus(created.id, "in_progress") }

        updated.shouldNotBeNull()
        updated.status shouldBe "in_progress"
        updated.startedAt.shouldNotBeNull()
    }

    test("updateStatus to completed sets completed_at") {
        val completed =
            tx.update {
                val created = repository.create(seasonId, CreateDraftRequest())
                repository.updateStatus(created.id, "in_progress")
                repository.updateStatus(created.id, "completed")
            }

        completed.shouldNotBeNull()
        completed.completedAt.shouldNotBeNull()
    }

    test("createPicks persists every slot returned from the snake order and getPicks orders by pick_num") {
        val teams = teamIds()
        val (created, fetched) =
            tx.update {
                val draft = repository.create(seasonId, CreateDraftRequest())
                val slots =
                    listOf(
                        PickSlot(teams[0], 1, 1),
                        PickSlot(teams[1], 1, 2),
                        PickSlot(teams[2], 1, 3),
                        PickSlot(teams[2], 2, 4),
                        PickSlot(teams[1], 2, 5),
                        PickSlot(teams[0], 2, 6),
                    )
                val created = repository.createPicks(draft.id, slots)
                val fetched = repository.getPicks(draft.id)
                created to fetched
            }

        created.map { it.pickNum } shouldContainExactly listOf(1, 2, 3, 4, 5, 6)
        fetched.map { it.teamId } shouldContainExactly created.map { it.teamId }
        fetched.all { it.golferId == null } shouldBe true
    }

    test("makePick fills in the golfer_id and picked_at for an unfilled slot") {
        val teams = teamIds()
        val made =
            tx.update {
                val draft = repository.create(seasonId, CreateDraftRequest())
                repository.createPicks(draft.id, listOf(PickSlot(teams[0], 1, 1)))
                val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy")).id
                repository.makePick(draft.id, pickNum = 1, golferId = rory)
            }

        made.shouldNotBeNull()
        made.pickedAt.shouldNotBeNull()
    }

    test("makePick returns null when the pick is already filled") {
        val teams = teamIds()
        val secondAttempt =
            tx.update {
                val draft = repository.create(seasonId, CreateDraftRequest())
                repository.createPicks(draft.id, listOf(PickSlot(teams[0], 1, 1)))
                val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy")).id
                repository.makePick(draft.id, pickNum = 1, golferId = rory)
                val scottie =
                    golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler")).id
                repository.makePick(draft.id, pickNum = 1, golferId = scottie)
            }

        secondAttempt.shouldBeNull()
    }

    test("getAvailableGolfers excludes picked golfers and inactive golfers") {
        val teams = teamIds()
        val (draftId, scottie) =
            tx.update {
                val draft = repository.create(seasonId, CreateDraftRequest())
                repository.createPicks(draft.id, listOf(PickSlot(teams[0], 1, 1)))
                val rory = golferRepo.create(CreateGolferRequest(firstName = "Rory", lastName = "McIlroy")).id
                val scottie = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler")).id
                val tiger = golferRepo.create(CreateGolferRequest(firstName = "Tiger", lastName = "Woods")).id
                golferRepo.update(tiger, UpdateGolferRequest(active = false))
                repository.makePick(draft.id, pickNum = 1, golferId = rory)
                draft.id to scottie
            }

        val available = tx.read { repository.getAvailableGolfers(draftId) }

        available.map { it.id } shouldContainExactly listOf(scottie)
    }
})
