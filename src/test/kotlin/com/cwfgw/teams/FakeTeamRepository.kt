package com.cwfgw.teams

import com.cwfgw.db.TransactionContext
import com.cwfgw.db.WriteTransactionContext
import com.cwfgw.seasons.SeasonId
import com.cwfgw.testing.fakeTableOf
import com.cwfgw.testing.uniqueViolation
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

private val teamOrdering: Comparator<Team> =
    compareBy<Team, Int?>(nullsLast()) { it.teamNumber }
        .thenBy { it.teamName }

private val rosterOrdering: Comparator<RosterEntry> =
    compareBy<RosterEntry, Int?>(nullsLast()) { it.draftRound }
        .thenBy { it.acquiredAt }

class FakeTeamRepository(
    initialTeams: List<Team> = emptyList(),
    initialRoster: List<RosterEntry> = emptyList(),
    initialRosterView: Map<SeasonId, List<RosterViewTeam>> = emptyMap(),
    private val teamIdFactory: () -> TeamId = { TeamId(UUID.randomUUID()) },
    private val rosterIdFactory: () -> RosterEntryId = { RosterEntryId(UUID.randomUUID()) },
    private val clock: () -> Instant = Instant::now,
) : TeamRepository {
    private val teams = fakeTableOf<TeamId, Team>()
    private val rosters = fakeTableOf<RosterEntryId, RosterEntry>()
    private val rosterViews = fakeTableOf<SeasonId, List<RosterViewTeam>>()

    init {
        initialTeams.forEach { team -> teams[team.id] = team }
        initialRoster.forEach { entry -> rosters[entry.id] = entry }
        rosterViews.putAll(initialRosterView)
    }

    context(ctx: TransactionContext)
    override fun findBySeason(seasonId: SeasonId): List<Team> =
        teams.values
            .filter { it.seasonId == seasonId }
            .sortedWith(teamOrdering)

    context(ctx: WriteTransactionContext)
    override fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
    ): Team {
        if (teams.values.any { it.seasonId == seasonId && it.teamName == request.teamName }) {
            throw uniqueViolation(UNIQUE_TEAM_NAME_PER_SEASON)
        }
        val now = clock()
        val team =
            Team(
                id = teamIdFactory(),
                seasonId = seasonId,
                ownerName = request.ownerName,
                teamName = request.teamName,
                teamNumber = request.teamNumber,
                createdAt = now,
                updatedAt = now,
            )
        teams[team.id] = team
        return team
    }

    context(ctx: TransactionContext)
    override fun findRostersBySeason(seasonId: SeasonId): List<RosterEntry> {
        val teamIdsInSeason = teams.values.filter { it.seasonId == seasonId }.map { it.id }.toSet()
        return rosters.values
            .filter { it.teamId in teamIdsInSeason }
            .sortedWith(compareBy<RosterEntry> { it.teamId.value }.then(rosterOrdering))
    }

    context(ctx: WriteTransactionContext)
    override fun addToRoster(
        teamId: TeamId,
        request: AddToRosterRequest,
    ): RosterEntry {
        val entry =
            RosterEntry(
                id = rosterIdFactory(),
                teamId = teamId,
                golferId = request.golferId,
                acquiredVia = request.acquiredVia ?: "free_agent",
                draftRound = request.draftRound,
                ownershipPct = request.ownershipPct ?: BigDecimal("100.00"),
                acquiredAt = clock(),
                isActive = true,
            )
        rosters[entry.id] = entry
        return entry
    }

    context(ctx: TransactionContext)
    override fun getRosterView(seasonId: SeasonId): List<RosterViewTeam> =
        rosterViews[seasonId] ?: emptyList()
}
