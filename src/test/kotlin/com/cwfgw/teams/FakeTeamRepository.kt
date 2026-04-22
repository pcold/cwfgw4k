package com.cwfgw.teams

import com.cwfgw.golfers.GolferId
import com.cwfgw.seasons.SeasonId
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

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
    private val teams = ConcurrentHashMap<TeamId, Team>()
    private val rosters = ConcurrentHashMap<RosterEntryId, RosterEntry>()
    private val rosterViews = ConcurrentHashMap<SeasonId, List<RosterViewTeam>>()

    init {
        initialTeams.forEach { team -> teams[team.id] = team }
        initialRoster.forEach { entry -> rosters[entry.id] = entry }
        rosterViews.putAll(initialRosterView)
    }

    override suspend fun findBySeason(seasonId: SeasonId): List<Team> =
        teams.values
            .filter { it.seasonId == seasonId }
            .sortedWith(teamOrdering)

    override suspend fun findById(id: TeamId): Team? = teams[id]

    override suspend fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
    ): Team {
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

    override suspend fun update(
        id: TeamId,
        request: UpdateTeamRequest,
    ): Team? {
        val current = teams[id] ?: return null
        val touched = request.ownerName != null || request.teamName != null
        val updated =
            current.copy(
                ownerName = request.ownerName ?: current.ownerName,
                teamName = request.teamName ?: current.teamName,
                updatedAt = if (touched) clock() else current.updatedAt,
            )
        teams[id] = updated
        return updated
    }

    override suspend fun getRoster(teamId: TeamId): List<RosterEntry> =
        rosters.values
            .filter { it.teamId == teamId && it.droppedAt == null }
            .sortedWith(rosterOrdering)

    override suspend fun addToRoster(
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
                droppedAt = null,
                isActive = true,
            )
        rosters[entry.id] = entry
        return entry
    }

    override suspend fun dropFromRoster(
        teamId: TeamId,
        golferId: GolferId,
    ): Boolean {
        val target =
            rosters.values.firstOrNull { entry ->
                entry.teamId == teamId && entry.golferId == golferId && entry.droppedAt == null
            } ?: return false
        rosters[target.id] = target.copy(droppedAt = clock(), isActive = false)
        return true
    }

    override suspend fun getRosterView(seasonId: SeasonId): List<RosterViewTeam> = rosterViews[seasonId] ?: emptyList()
}
