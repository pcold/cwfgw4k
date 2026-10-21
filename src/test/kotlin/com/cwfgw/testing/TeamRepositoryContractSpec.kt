package com.cwfgw.testing

import com.cwfgw.db.Transactor
import com.cwfgw.jooq.keys.TEAM_ROSTERS_TEAM_ID_DRAFT_ROUND_KEY
import com.cwfgw.result.Result
import com.cwfgw.teams.FakeTeamRepository
import com.cwfgw.teams.NewRosterEntry
import com.cwfgw.teams.NewTeam
import com.cwfgw.teams.RosterEntry
import com.cwfgw.teams.TeamId
import com.cwfgw.teams.TeamRepository
import com.cwfgw.teams.UNIQUE_TEAM_NAME_PER_SEASON
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

private val ROSTER_ORDER: Comparator<RosterEntry> =
    compareBy(POSTGRES_UUID_ORDER) { entry: RosterEntry -> entry.teamId.value }
        .thenBy(nullsLast()) { it.draftRound }
        .thenBy { it.acquiredAt }

/** `getRosterView` is left out: the fake answers a stub its caller seeds rather than joining its own rows. */
class TeamRepositoryContractSpec : FunSpec({
    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)

    runContract("Postgres", teamContract) { ContractSubject(tx, TeamRepository(), PostgresSeeds(tx)) }
    runContract("fake", teamContract) {
        val ids = signStraddlingUuids()
        ContractSubject(FakeTransactor(), FakeTeamRepository(teamIdFactory = { TeamId(ids()) }), FakeSeeds)
    }
})

private val teamContract: List<ContractCase<TeamRepository>> =
    listOf(
        ContractCase("findBySeason lists the season's teams by number with the unnumbered last, then by name") {
            val seasonId = seeds.season()
            tx.update {
                repository.create(seasonId, NewTeam(ownerName = "Dee", teamName = "Delta"))
                repository.create(seasonId, NewTeam(ownerName = "Bea", teamName = "Bravo", teamNumber = 2))
                repository.create(seasonId, NewTeam(ownerName = "Cal", teamName = "Charlie"))
                repository.create(seasonId, NewTeam(ownerName = "Al", teamName = "Alpha", teamNumber = 1))
            }
            val otherSeason = seeds.season()
            tx.update { repository.create(otherSeason, NewTeam(ownerName = "Eve", teamName = "Echo")) }
            val unusedSeason = seeds.season()

            val listed = tx.get { repository.findBySeason(seasonId) }

            listed.map { it.teamName } shouldContainExactly listOf("Alpha", "Bravo", "Charlie", "Delta")
            listed.first().ownerName shouldBe "Al"
            tx.get { repository.findBySeason(unusedSeason) }.shouldBeEmpty()
        },
        ContractCase("a second team on a name taken in the season is outraced, and another season may reuse it") {
            val seasonId = seeds.season()
            val brown = NewTeam(ownerName = "Pete", teamName = "BROWN")
            tx.update { repository.create(seasonId, brown) }

            tx.updateUnlessOutraced(UNIQUE_TEAM_NAME_PER_SEASON) { repository.create(seasonId, brown) } shouldBe
                Result.Err(Outraced)
            val nextSeason = seeds.season()
            tx.update { repository.create(nextSeason, brown) }.seasonId shouldBe nextSeason
        },
        ContractCase("a roster entry defaults to a free agent at 100.00 percent; an explicit share keeps 2 places") {
            val seasonId = seeds.season()
            val team =
                tx.update {
                    repository.create(seasonId, NewTeam(ownerName = "Pete", teamName = "BROWN"))
                }
            val bare = NewRosterEntry(seeds.golfer())
            val half =
                NewRosterEntry(seeds.golfer(), acquiredVia = "draft", draftRound = 1, ownershipPct = BigDecimal(50))

            val defaulted = tx.update { repository.addToRoster(team.id, bare) }
            val drafted = tx.update { repository.addToRoster(team.id, half) }

            defaulted.acquiredVia shouldBe "free_agent"
            defaulted.ownershipPct.toPlainString() shouldBe "100.00"
            drafted.acquiredVia shouldBe "draft"
            drafted.draftRound shouldBe 1
            drafted.ownershipPct.toPlainString() shouldBe "50.00"
        },
        ContractCase("a second pick in one round of a team is outraced; another team, or an undrafted entry, is not") {
            val seasonId = seeds.season()
            val (brown, green) =
                listOf("BROWN", "GREEN").map { name ->
                    tx.update { repository.create(seasonId, NewTeam(ownerName = name, teamName = name)) }
                }
            val firstThirdRounder = NewRosterEntry(seeds.golfer(), draftRound = 3)
            tx.update { repository.addToRoster(brown.id, firstThirdRounder) }
            val secondThirdRounder = NewRosterEntry(seeds.golfer(), draftRound = 3)

            tx.updateUnlessOutraced(TEAM_ROSTERS_TEAM_ID_DRAFT_ROUND_KEY.name) {
                repository.addToRoster(brown.id, secondThirdRounder)
            } shouldBe Result.Err(Outraced)
            tx.update { repository.addToRoster(green.id, secondThirdRounder) }.draftRound shouldBe 3
            val undrafted = List(2) { NewRosterEntry(seeds.golfer()) }
            undrafted.forEach { entry -> tx.update { repository.addToRoster(brown.id, entry) } }
            tx.get { repository.findRostersBySeason(seasonId) }.count { it.teamId == brown.id } shouldBe 3
        },
        ContractCase("findRostersBySeason orders by team id as Postgres orders uuid, then by round, undrafted last") {
            val seasonId = seeds.season()
            val seasonTeams =
                (1..4).map { number ->
                    tx.update {
                        repository.create(seasonId, NewTeam(ownerName = "O$number", teamName = "T$number"))
                    }
                }
            val otherSeason = seeds.season()
            val elsewhere =
                tx.update {
                    repository.create(otherSeason, NewTeam(ownerName = "X", teamName = "X"))
                }
            val rounds = listOf(null, 2, 1)
            val added =
                seasonTeams.flatMap { team ->
                    rounds.map { round ->
                        val golfer = seeds.golfer()
                        tx.update { repository.addToRoster(team.id, NewRosterEntry(golfer, draftRound = round)) }
                    }
                }
            val stray = NewRosterEntry(seeds.golfer())
            tx.update { repository.addToRoster(elsewhere.id, stray) }

            tx.get { repository.findRostersBySeason(seasonId) } shouldContainExactly added.sortedWith(ROSTER_ORDER)
        },
    )
