package com.cwfgw.seasons

import com.cwfgw.db.Transactor
import com.cwfgw.golfers.CreateGolferRequest
import com.cwfgw.golfers.GolferRepository
import com.cwfgw.jooq.tables.references.TEAM_ROSTERS
import com.cwfgw.leagues.CreateLeagueRequest
import com.cwfgw.leagues.LeagueId
import com.cwfgw.leagues.LeagueRepository
import com.cwfgw.teams.AddToRosterRequest
import com.cwfgw.teams.CreateTeamRequest
import com.cwfgw.teams.TeamRepository
import com.cwfgw.testing.postgresHarness
import com.cwfgw.testing.seedSeasonRules
import com.cwfgw.tournaments.CreateTournamentRequest
import com.cwfgw.tournaments.TournamentFilter
import com.cwfgw.tournaments.TournamentRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import org.jooq.exception.DataAccessException
import java.math.BigDecimal
import java.util.UUID

private fun seasonRequest(leagueId: LeagueId): CreateSeasonRequest =
    CreateSeasonRequest(leagueId = leagueId, name = "Rules", seasonYear = 2026)

private fun stakedRequest(
    leagueId: LeagueId,
    sideBetAmount: BigDecimal,
): CreateSeasonRequest =
    seasonRequest(leagueId).copy(rules = SeasonRules.defaults().copy(sideBetAmount = sideBetAmount))

/** A create without rules writes the default rounds, so a spec seeding its own needs the tables left empty. */
private fun emptyJoinTables(): SeasonRules =
    SeasonRules.defaults().copy(payouts = emptyList(), sideBetRounds = emptyList())

class SeasonRepositorySpec : FunSpec({

    val postgres = postgresHarness()
    val tx = Transactor(postgres.dsl, postgres.maxPoolSize)
    val repository = SeasonRepository()
    val leagueRepo = LeagueRepository()
    var castlewoodId = LeagueId(UUID.randomUUID())

    beforeEach {
        castlewoodId = tx.update { leagueRepo.create(CreateLeagueRequest(name = "Castlewood Fantasy Golf")) }.id
    }

    test("create uses defaults and starts season_number at 1 when request omits optional fields") {
        tx.update {
            val created =
                repository.create(
                    CreateSeasonRequest(leagueId = castlewoodId, name = "2026 Season", seasonYear = 2026),
                )

            created.leagueId shouldBe castlewoodId
            created.name shouldBe "2026 Season"
            created.seasonYear shouldBe 2026
            created.seasonNumber shouldBe 1
            created.maxTeams shouldBe 10
            created.tieFloor.compareTo(BigDecimal.ONE) shouldBe 0
            created.sideBetAmount.compareTo(BigDecimal(15)) shouldBe 0
        }
    }

    test("create auto-increments season_number for the same league and year") {
        tx.update {
            val first =
                repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "Spring", seasonYear = 2026))
            val second =
                repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "Fall", seasonYear = 2026))

            first.seasonNumber shouldBe 1
            second.seasonNumber shouldBe 2
        }
    }

    test("create restarts season_number at 1 for a different year or league") {
        tx.update {
            repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "2026 S1", seasonYear = 2026))
            repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "2026 S2", seasonYear = 2026))
            val nextYear =
                repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "2027 S1", seasonYear = 2027))

            val otherLeague = leagueRepo.create(CreateLeagueRequest(name = "Other League")).id
            val otherLeagueSeason =
                repository.create(CreateSeasonRequest(leagueId = otherLeague, name = "2026 S1", seasonYear = 2026))

            nextYear.seasonNumber shouldBe 1
            otherLeagueSeason.seasonNumber shouldBe 1
        }
    }

    test("create respects explicitly supplied values") {
        tx.update {
            val created =
                repository.create(
                    CreateSeasonRequest(
                        leagueId = castlewoodId,
                        name = "Custom",
                        seasonYear = 2027,
                        seasonNumber = 2,
                        maxTeams = 13,
                        rules =
                            SeasonRules.defaults().copy(tieFloor = BigDecimal("0.5"), sideBetAmount = BigDecimal("20")),
                    ),
                )

            created.seasonNumber shouldBe 2
            created.maxTeams shouldBe 13
            created.tieFloor.compareTo(BigDecimal("0.5")) shouldBe 0
            created.sideBetAmount.compareTo(BigDecimal("20")) shouldBe 0
        }
    }

    test("create past the route guard is refused a negative side-bet amount by the CHECK") {
        val refusal =
            shouldThrow<DataAccessException> {
                tx.update {
                    repository.create(
                        CreateSeasonRequest(
                            leagueId = castlewoodId,
                            name = "Inverted",
                            seasonYear = 2026,
                            rules = SeasonRules.defaults().copy(sideBetAmount = BigDecimal("-15")),
                        ),
                    )
                }
            }

        refusal.message shouldContain "seasons_side_bet_amount_non_negative"
        tx.read { repository.findAll(leagueId = castlewoodId, seasonYear = null) }.shouldBeEmpty()
    }

    test("create stores a zero side-bet amount, the season that stakes no side bets") {
        val created =
            tx.update {
                repository.create(
                    CreateSeasonRequest(
                        leagueId = castlewoodId,
                        name = "No Side Bets",
                        seasonYear = 2026,
                        rules = SeasonRules.defaults().copy(sideBetAmount = BigDecimal.ZERO),
                    ),
                )
            }

        created.sideBetAmount.signum() shouldBe 0
    }

    test("create past the route guard is refused a name of nothing but ASCII whitespace by the CHECK") {
        listOf("   ", "\t\t", "\n", " \t\n\r\u000B\u000C ").forEach { blank ->
            val refusal =
                shouldThrow<DataAccessException> {
                    tx.update {
                        repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = blank, seasonYear = 2026))
                    }
                }

            refusal.message shouldContain "seasons_name_not_blank"
        }
        tx.read { repository.findAll(leagueId = castlewoodId, seasonYear = null) }.shouldBeEmpty()
    }

    test("create stores the names the route guard passes: a padded name, a lone v, and a no-break space") {
        val names = listOf("\tSpring \n", "v", " ")
        val stored =
            tx.update {
                names.map { name ->
                    repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = name, seasonYear = 2026)).name
                }
            }

        stored shouldBe names
    }

    test("create past the route guard is refused a negative tie floor, a negative payout, or no teams by the CHECKs") {
        val negativePayout = SeasonRules.defaults().copy(payouts = listOf(BigDecimal(18), BigDecimal("-0.01")))
        val negativeFloor = SeasonRules.defaults().copy(tieFloor = BigDecimal(-1))
        val refusals =
            listOf(
                seasonRequest(castlewoodId).copy(rules = negativeFloor) to "seasons_tie_floor_non_negative",
                seasonRequest(castlewoodId).copy(rules = negativePayout) to "season_rule_payouts_amount_non_negative",
                seasonRequest(castlewoodId).copy(maxTeams = 0) to "seasons_max_teams_positive",
            )
        refusals.forEach { (outOfRange, constraint) ->
            val refusal = shouldThrow<DataAccessException> { tx.update { repository.create(outOfRange) } }

            refusal.message shouldContain constraint
        }
        tx.read { repository.findAll(leagueId = castlewoodId, seasonYear = null) }.shouldBeEmpty()
    }

    test("create stores a zero tie floor, a zero payout place, and a single team") {
        val zeroes =
            SeasonRules.defaults().copy(payouts = listOf(BigDecimal(18), BigDecimal.ZERO), tieFloor = BigDecimal.ZERO)
        val created = tx.update { repository.create(seasonRequest(castlewoodId).copy(maxTeams = 1, rules = zeroes)) }

        created.tieFloor.signum() shouldBe 0
        created.maxTeams shouldBe 1
        tx.read { repository.getRules(created.id) }?.payouts?.map(BigDecimal::signum) shouldBe listOf(1, 0)
    }

    test("create stores the largest amount the route guard passes, and the least it refuses overflows the column") {
        val largest = BigDecimal("999999.9999")
        val stored = tx.update { repository.create(stakedRequest(castlewoodId, sideBetAmount = largest)) }
        stored.sideBetAmount shouldBe largest

        val leastRefused = stakedRequest(castlewoodId, sideBetAmount = BigDecimal("999999.99995"))
        val overflow = shouldThrow<DataAccessException> { tx.update { repository.create(leastRefused) } }
        overflow.message shouldContain "numeric field overflow"
    }

    test("findAll orders by year desc, number desc, created_at desc") {
        tx.update {
            val older =
                repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "2025", seasonYear = 2025))
            val newer =
                repository.create(
                    CreateSeasonRequest(
                        leagueId = castlewoodId,
                        name = "2026 S1",
                        seasonYear = 2026,
                        seasonNumber = 1,
                    ),
                )
            val newest =
                repository.create(
                    CreateSeasonRequest(
                        leagueId = castlewoodId,
                        name = "2026 S2",
                        seasonYear = 2026,
                        seasonNumber = 2,
                    ),
                )

            val result = repository.findAll(leagueId = null, seasonYear = null)

            result.map { it.id } shouldContainExactly listOf(newest.id, newer.id, older.id)
        }
    }

    test("findAll filters by league_id") {
        tx.update {
            val otherLeague = leagueRepo.create(CreateLeagueRequest(name = "Other League")).id

            val castlewood =
                repository.create(
                    CreateSeasonRequest(leagueId = castlewoodId, name = "Castlewood 2026", seasonYear = 2026),
                )
            repository.create(
                CreateSeasonRequest(leagueId = otherLeague, name = "Other 2026", seasonYear = 2026),
            )

            val result = repository.findAll(leagueId = castlewoodId, seasonYear = null)

            result.map { it.id } shouldContainExactly listOf(castlewood.id)
        }
    }

    test("findAll filters by season year") {
        tx.update {
            repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "2025", seasonYear = 2025))
            val match =
                repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "2026", seasonYear = 2026))

            val result = repository.findAll(leagueId = null, seasonYear = 2026)

            result.map { it.id } shouldContainExactly listOf(match.id)
        }
    }

    test("findById returns null for unknown id") {
        tx.read { repository.findById(SeasonId(UUID.randomUUID())) }.shouldBeNull()
    }

    test("getRules returns defaults for a season created without rules") {
        tx.update {
            val created =
                repository.create(CreateSeasonRequest(leagueId = castlewoodId, name = "2026", seasonYear = 2026))

            val rules = repository.getRules(created.id)

            rules.shouldNotBeNull()
            rules.payouts shouldBe SeasonRules.DEFAULT_PAYOUTS
            rules.sideBetRounds shouldBe SeasonRules.DEFAULT_SIDE_BET_ROUNDS
            rules.tieFloor.compareTo(BigDecimal.ONE) shouldBe 0
            rules.sideBetAmount.compareTo(BigDecimal(15)) shouldBe 0
        }
    }

    test("getRules returns custom payouts and rounds when seeded in join tables") {
        tx.update {
            val created = repository.create(seasonRequest(castlewoodId).copy(rules = emptyJoinTables()))
            seedSeasonRules(
                created.id,
                payouts = listOf(1 to BigDecimal("50"), 2 to BigDecimal("25"), 3 to BigDecimal("10")),
                sideBetRounds = listOf(3, 4),
            )

            val rules = repository.getRules(created.id)

            rules.shouldNotBeNull()
            rules.payouts shouldContainExactly
                listOf(BigDecimal("50.0000"), BigDecimal("25.0000"), BigDecimal("10.0000"))
            rules.sideBetRounds shouldContainExactly listOf(3, 4)
        }
    }

    test("a season created with no side-bet rounds reads back none, not the defaults") {
        tx.update {
            val created =
                repository.create(
                    seasonRequest(castlewoodId).copy(rules = SeasonRules.defaults().copy(sideBetRounds = emptyList())),
                )

            repository.getRules(created.id).shouldNotBeNull().sideBetRounds.shouldBeEmpty()
        }
    }

    test("the round CHECK refuses a side-bet round outside 1..8 written past validation") {
        val created = tx.update { repository.create(seasonRequest(castlewoodId).copy(rules = emptyJoinTables())) }

        listOf(0, 9).forEach { round ->
            val failure =
                shouldThrow<DataAccessException> {
                    tx.update { seedSeasonRules(created.id, payouts = emptyList(), sideBetRounds = listOf(round)) }
                }
            failure.message.shouldNotBeNull() shouldContain "season_rule_side_bet_rounds_round_in_range"
        }
    }

    test("getRules returns null for unknown season") {
        tx.read { repository.getRules(SeasonId(UUID.randomUUID())) }.shouldBeNull()
    }

    test("create with rules persists payouts + side-bet rounds and uses rules' tieFloor/sideBetAmount") {
        tx.update {
            val customRules =
                SeasonRules(
                    payouts = listOf(BigDecimal("100"), BigDecimal("50"), BigDecimal("25")),
                    tieFloor = BigDecimal("2"),
                    sideBetRounds = listOf(2, 3),
                    sideBetAmount = BigDecimal("20"),
                )

            val created =
                repository.create(
                    CreateSeasonRequest(
                        leagueId = castlewoodId,
                        name = "2026 With Rules",
                        seasonYear = 2026,
                        rules = customRules,
                    ),
                )

            created.tieFloor.compareTo(BigDecimal("2")) shouldBe 0
            created.sideBetAmount.compareTo(BigDecimal("20")) shouldBe 0

            val rules = repository.getRules(created.id)
            rules.shouldNotBeNull()
            rules.payouts shouldContainExactly
                listOf(BigDecimal("100.0000"), BigDecimal("50.0000"), BigDecimal("25.0000"))
            rules.sideBetRounds shouldContainExactly listOf(2, 3)
            rules.tieFloor.compareTo(BigDecimal("2")) shouldBe 0
            rules.sideBetAmount.compareTo(BigDecimal("20")) shouldBe 0
        }
    }

    test("delete cascades through teams + tournaments + team_rosters via the V010 FKs") {
        val teamRepo = TeamRepository()
        val tournamentRepo = TournamentRepository()
        val golferRepo = GolferRepository()

        val (seasonId, teamId) =
            tx.update {
                val season =
                    repository.create(
                        CreateSeasonRequest(leagueId = castlewoodId, name = "2026 Summer", seasonYear = 2026),
                    )
                val team =
                    teamRepo.create(
                        seasonId = season.id,
                        request = CreateTeamRequest(ownerName = "Owner", teamName = "Birdies"),
                    )
                tournamentRepo.create(
                    CreateTournamentRequest(
                        name = "Sony Open",
                        seasonId = season.id,
                        startDate = java.time.LocalDate.parse("2026-01-15"),
                        endDate = java.time.LocalDate.parse("2026-01-18"),
                    ),
                )
                val golfer = golferRepo.create(CreateGolferRequest(firstName = "Scottie", lastName = "Scheffler"))
                teamRepo.addToRoster(team.id, AddToRosterRequest(golferId = golfer.id))
                season.id to team.id
            }

        // Sanity: rows are present before we cascade them away.
        postgres.dsl.fetchCount(TEAM_ROSTERS, TEAM_ROSTERS.TEAM_ID.eq(teamId.value)) shouldBe 1

        tx.update { repository.delete(seasonId) }.shouldBeTrue()

        tx.read { repository.findById(seasonId) }.shouldBeNull()
        tx.read { teamRepo.findBySeason(seasonId) }.shouldBeEmpty()
        tx.read { tournamentRepo.findAll(TournamentFilter(seasonId = seasonId)) }.shouldBeEmpty()
        postgres.dsl.fetchCount(TEAM_ROSTERS, TEAM_ROSTERS.TEAM_ID.eq(teamId.value)) shouldBe 0
    }

    test("delete returns false for unknown id") {
        tx.update { repository.delete(SeasonId(UUID.randomUUID())) }.shouldBeFalse()
    }
})
