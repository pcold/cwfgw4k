package com.cwfgw.teams

import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.records.TeamRostersRecord
import com.cwfgw.jooq.tables.records.TeamsRecord
import com.cwfgw.jooq.tables.references.GOLFERS
import com.cwfgw.jooq.tables.references.TEAMS
import com.cwfgw.jooq.tables.references.TEAM_ROSTERS
import com.cwfgw.seasons.SeasonId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jooq.DSLContext
import org.jooq.Field
import org.jooq.impl.DSL
import java.math.BigDecimal

interface TeamRepository {
    suspend fun findBySeason(seasonId: SeasonId): List<Team>

    suspend fun findById(id: TeamId): Team?

    suspend fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
    ): Team

    suspend fun update(
        id: TeamId,
        request: UpdateTeamRequest,
    ): Team?

    suspend fun getRoster(teamId: TeamId): List<RosterEntry>

    suspend fun addToRoster(
        teamId: TeamId,
        request: AddToRosterRequest,
    ): RosterEntry

    suspend fun dropFromRoster(
        teamId: TeamId,
        golferId: GolferId,
    ): Boolean

    suspend fun getRosterView(seasonId: SeasonId): List<RosterViewTeam>
}

fun TeamRepository(dsl: DSLContext): TeamRepository = JooqTeamRepository(dsl)

private const val DEFAULT_ACQUIRED_VIA = "free_agent"
private val DEFAULT_OWNERSHIP_PCT: BigDecimal = BigDecimal("100.00")

private data class RosterViewRow(
    val teamId: TeamId,
    val teamName: String,
    val round: Int,
    val firstName: String,
    val lastName: String,
    val ownershipPct: BigDecimal,
    val golferId: GolferId,
)

private class JooqTeamRepository(private val dsl: DSLContext) : TeamRepository {
    override suspend fun findBySeason(seasonId: SeasonId): List<Team> =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(TEAMS)
                .where(TEAMS.SEASON_ID.eq(seasonId.value))
                .orderBy(TEAMS.TEAM_NUMBER.asc().nullsLast(), TEAMS.TEAM_NAME.asc())
                .fetch(::toTeam)
        }

    override suspend fun findById(id: TeamId): Team? =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(TEAMS)
                .where(TEAMS.ID.eq(id.value))
                .fetchOne()
                ?.let(::toTeam)
        }

    override suspend fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
    ): Team =
        withContext(Dispatchers.IO) {
            val inserted =
                dsl.insertInto(TEAMS)
                    .set(TEAMS.SEASON_ID, seasonId.value)
                    .set(TEAMS.OWNER_NAME, request.ownerName)
                    .set(TEAMS.TEAM_NAME, request.teamName)
                    .set(TEAMS.TEAM_NUMBER, request.teamNumber)
                    .returning()
                    .fetchOne() ?: error("INSERT RETURNING produced no row for teams")
            toTeam(inserted)
        }

    override suspend fun update(
        id: TeamId,
        request: UpdateTeamRequest,
    ): Team? =
        withContext(Dispatchers.IO) {
            val changes = updateAssignments(request)
            if (changes.isEmpty()) {
                dsl.selectFrom(TEAMS)
                    .where(TEAMS.ID.eq(id.value))
                    .fetchOne()
                    ?.let(::toTeam)
            } else {
                dsl.update(TEAMS)
                    .set(changes + (TEAMS.UPDATED_AT to DSL.currentOffsetDateTime()))
                    .where(TEAMS.ID.eq(id.value))
                    .returning()
                    .fetchOne()
                    ?.let(::toTeam)
            }
        }

    override suspend fun getRoster(teamId: TeamId): List<RosterEntry> =
        withContext(Dispatchers.IO) {
            dsl.selectFrom(TEAM_ROSTERS)
                .where(TEAM_ROSTERS.TEAM_ID.eq(teamId.value))
                .and(TEAM_ROSTERS.DROPPED_AT.isNull)
                .orderBy(TEAM_ROSTERS.DRAFT_ROUND.asc().nullsLast(), TEAM_ROSTERS.ACQUIRED_AT.asc())
                .fetch(::toRosterEntry)
        }

    override suspend fun addToRoster(
        teamId: TeamId,
        request: AddToRosterRequest,
    ): RosterEntry =
        withContext(Dispatchers.IO) {
            val inserted =
                dsl.insertInto(TEAM_ROSTERS)
                    .set(TEAM_ROSTERS.TEAM_ID, teamId.value)
                    .set(TEAM_ROSTERS.GOLFER_ID, request.golferId.value)
                    .set(TEAM_ROSTERS.ACQUIRED_VIA, request.acquiredVia ?: DEFAULT_ACQUIRED_VIA)
                    .set(TEAM_ROSTERS.DRAFT_ROUND, request.draftRound)
                    .set(TEAM_ROSTERS.OWNERSHIP_PCT, request.ownershipPct ?: DEFAULT_OWNERSHIP_PCT)
                    .returning()
                    .fetchOne() ?: error("INSERT RETURNING produced no row for team_rosters")
            toRosterEntry(inserted)
        }

    override suspend fun dropFromRoster(
        teamId: TeamId,
        golferId: GolferId,
    ): Boolean =
        withContext(Dispatchers.IO) {
            val affected =
                dsl.update(TEAM_ROSTERS)
                    .set(TEAM_ROSTERS.DROPPED_AT, DSL.currentOffsetDateTime())
                    .set(TEAM_ROSTERS.IS_ACTIVE, false)
                    .where(TEAM_ROSTERS.TEAM_ID.eq(teamId.value))
                    .and(TEAM_ROSTERS.GOLFER_ID.eq(golferId.value))
                    .and(TEAM_ROSTERS.DROPPED_AT.isNull)
                    .execute()
            affected > 0
        }

    override suspend fun getRosterView(seasonId: SeasonId): List<RosterViewTeam> =
        withContext(Dispatchers.IO) {
            val rows = fetchRosterViewRows(seasonId)
            rows.groupBy { it.teamId to it.teamName }.map { (key, picks) ->
                RosterViewTeam(
                    teamId = key.first,
                    teamName = key.second,
                    picks = picks.map(::toRosterViewPick),
                )
            }
        }

    private fun fetchRosterViewRows(seasonId: SeasonId): List<RosterViewRow> =
        dsl.select(
            TEAMS.ID,
            TEAMS.TEAM_NAME,
            TEAM_ROSTERS.DRAFT_ROUND,
            GOLFERS.FIRST_NAME,
            GOLFERS.LAST_NAME,
            TEAM_ROSTERS.OWNERSHIP_PCT,
            GOLFERS.ID,
            TEAMS.CREATED_AT,
        )
            .from(TEAM_ROSTERS)
            .join(TEAMS).on(TEAM_ROSTERS.TEAM_ID.eq(TEAMS.ID))
            .join(GOLFERS).on(TEAM_ROSTERS.GOLFER_ID.eq(GOLFERS.ID))
            .where(TEAMS.SEASON_ID.eq(seasonId.value))
            .and(TEAM_ROSTERS.DROPPED_AT.isNull)
            .orderBy(TEAMS.CREATED_AT.asc(), TEAM_ROSTERS.DRAFT_ROUND.asc().nullsLast())
            .fetch { record ->
                RosterViewRow(
                    teamId = TeamId(checkNotNull(record[TEAMS.ID])),
                    teamName = checkNotNull(record[TEAMS.TEAM_NAME]),
                    round = record[TEAM_ROSTERS.DRAFT_ROUND] ?: 0,
                    firstName = checkNotNull(record[GOLFERS.FIRST_NAME]),
                    lastName = checkNotNull(record[GOLFERS.LAST_NAME]),
                    ownershipPct = checkNotNull(record[TEAM_ROSTERS.OWNERSHIP_PCT]),
                    golferId = GolferId(checkNotNull(record[GOLFERS.ID])),
                )
            }

    private fun toRosterViewPick(row: RosterViewRow): RosterViewPick =
        RosterViewPick(
            round = row.round,
            golferName = if (row.firstName.isNotEmpty()) "${row.firstName} ${row.lastName}" else row.lastName,
            ownershipPct = row.ownershipPct,
            golferId = row.golferId,
        )

    private fun updateAssignments(request: UpdateTeamRequest): Map<Field<*>, Any?> =
        buildMap {
            request.ownerName?.let { put(TEAMS.OWNER_NAME, it) }
            request.teamName?.let { put(TEAMS.TEAM_NAME, it) }
        }

    private fun toTeam(record: TeamsRecord): Team =
        Team(
            id = TeamId(checkNotNull(record.id) { "teams.id is NOT NULL but returned null" }),
            seasonId = SeasonId(record.seasonId),
            ownerName = record.ownerName,
            teamName = record.teamName,
            teamNumber = record.teamNumber,
            createdAt =
                checkNotNull(record.createdAt) {
                    "teams.created_at is NOT NULL but returned null"
                }.toInstant(),
            updatedAt =
                checkNotNull(record.updatedAt) {
                    "teams.updated_at is NOT NULL but returned null"
                }.toInstant(),
        )

    private fun toRosterEntry(record: TeamRostersRecord): RosterEntry =
        RosterEntry(
            id = RosterEntryId(checkNotNull(record.id) { "team_rosters.id is NOT NULL but returned null" }),
            teamId = TeamId(record.teamId),
            golferId = GolferId(record.golferId),
            acquiredVia =
                checkNotNull(record.acquiredVia) {
                    "team_rosters.acquired_via is NOT NULL but returned null"
                },
            draftRound = record.draftRound,
            ownershipPct =
                checkNotNull(record.ownershipPct) {
                    "team_rosters.ownership_pct is NOT NULL but returned null"
                },
            acquiredAt =
                checkNotNull(record.acquiredAt) {
                    "team_rosters.acquired_at is NOT NULL but returned null"
                }.toInstant(),
            droppedAt = record.droppedAt?.toInstant(),
            isActive =
                checkNotNull(record.isActive) {
                    "team_rosters.is_active is NOT NULL but returned null"
                },
        )
}
