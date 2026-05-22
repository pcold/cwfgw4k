package com.cwfgw.teams

import com.cwfgw.db.TransactionContext
import com.cwfgw.golfers.GolferId
import com.cwfgw.jooq.tables.records.TeamRostersRecord
import com.cwfgw.jooq.tables.records.TeamsRecord
import com.cwfgw.jooq.tables.references.GOLFERS
import com.cwfgw.jooq.tables.references.TEAMS
import com.cwfgw.jooq.tables.references.TEAM_ROSTERS
import com.cwfgw.seasons.SeasonId
import org.jooq.Field
import org.jooq.impl.DSL
import java.math.BigDecimal

interface TeamRepository {
    context(ctx: TransactionContext)
    fun findBySeason(seasonId: SeasonId): List<Team>

    context(ctx: TransactionContext)
    fun findById(id: TeamId): Team?

    context(ctx: TransactionContext)
    fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
    ): Team

    context(ctx: TransactionContext)
    fun update(
        id: TeamId,
        request: UpdateTeamRequest,
    ): Team?

    context(ctx: TransactionContext)
    fun getRoster(teamId: TeamId): List<RosterEntry>

    /**
     * Single-query alternative to `findBySeason(seasonId).flatMap { getRoster(it.id) }`.
     * Returns every active (non-dropped) roster entry across every team in the
     * season, ordered by `(team_id, draft_round, acquired_at)` for deterministic
     * grouping at the call site. Replaces the N+1 pattern in `WeeklyReportService`,
     * `ScoringService`, and `EspnService`.
     */
    context(ctx: TransactionContext)
    fun findRostersBySeason(seasonId: SeasonId): List<RosterEntry>

    context(ctx: TransactionContext)
    fun addToRoster(
        teamId: TeamId,
        request: AddToRosterRequest,
    ): RosterEntry

    context(ctx: TransactionContext)
    fun dropFromRoster(
        teamId: TeamId,
        golferId: GolferId,
    ): Boolean

    context(ctx: TransactionContext)
    fun getRosterView(seasonId: SeasonId): List<RosterViewTeam>
}

fun TeamRepository(): TeamRepository = JooqTeamRepository()

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

private class JooqTeamRepository : TeamRepository {
    context(ctx: TransactionContext)
    override fun findBySeason(seasonId: SeasonId): List<Team> =
        ctx.dsl.selectFrom(TEAMS)
            .where(TEAMS.SEASON_ID.eq(seasonId.value))
            .orderBy(TEAMS.TEAM_NUMBER.asc().nullsLast(), TEAMS.TEAM_NAME.asc())
            .fetch(::toTeam)

    context(ctx: TransactionContext)
    override fun findById(id: TeamId): Team? =
        ctx.dsl.selectFrom(TEAMS)
            .where(TEAMS.ID.eq(id.value))
            .fetchOne()
            ?.let(::toTeam)

    context(ctx: TransactionContext)
    override fun create(
        seasonId: SeasonId,
        request: CreateTeamRequest,
    ): Team {
        val inserted =
            ctx.dsl.insertInto(TEAMS)
                .set(TEAMS.SEASON_ID, seasonId.value)
                .set(TEAMS.OWNER_NAME, request.ownerName)
                .set(TEAMS.TEAM_NAME, request.teamName)
                .set(TEAMS.TEAM_NUMBER, request.teamNumber)
                .returning()
                .fetchOne() ?: error("INSERT RETURNING produced no row for teams")
        return toTeam(inserted)
    }

    context(ctx: TransactionContext)
    override fun update(
        id: TeamId,
        request: UpdateTeamRequest,
    ): Team? {
        val changes = updateAssignments(request)
        return if (changes.isEmpty()) {
            ctx.dsl.selectFrom(TEAMS)
                .where(TEAMS.ID.eq(id.value))
                .fetchOne()
                ?.let(::toTeam)
        } else {
            ctx.dsl.update(TEAMS)
                .set(changes + (TEAMS.UPDATED_AT to DSL.currentOffsetDateTime()))
                .where(TEAMS.ID.eq(id.value))
                .returning()
                .fetchOne()
                ?.let(::toTeam)
        }
    }

    context(ctx: TransactionContext)
    override fun getRoster(teamId: TeamId): List<RosterEntry> =
        ctx.dsl.selectFrom(TEAM_ROSTERS)
            .where(TEAM_ROSTERS.TEAM_ID.eq(teamId.value))
            .and(TEAM_ROSTERS.DROPPED_AT.isNull)
            .orderBy(TEAM_ROSTERS.DRAFT_ROUND.asc().nullsLast(), TEAM_ROSTERS.ACQUIRED_AT.asc())
            .fetch(::toRosterEntry)

    context(ctx: TransactionContext)
    override fun findRostersBySeason(seasonId: SeasonId): List<RosterEntry> =
        ctx.dsl.selectFrom(TEAM_ROSTERS)
            .where(
                TEAM_ROSTERS.TEAM_ID.`in`(
                    DSL.select(TEAMS.ID).from(TEAMS).where(TEAMS.SEASON_ID.eq(seasonId.value)),
                ),
            )
            .and(TEAM_ROSTERS.DROPPED_AT.isNull)
            .orderBy(
                TEAM_ROSTERS.TEAM_ID.asc(),
                TEAM_ROSTERS.DRAFT_ROUND.asc().nullsLast(),
                TEAM_ROSTERS.ACQUIRED_AT.asc(),
            )
            .fetch(::toRosterEntry)

    context(ctx: TransactionContext)
    override fun addToRoster(
        teamId: TeamId,
        request: AddToRosterRequest,
    ): RosterEntry {
        val inserted =
            ctx.dsl.insertInto(TEAM_ROSTERS)
                .set(TEAM_ROSTERS.TEAM_ID, teamId.value)
                .set(TEAM_ROSTERS.GOLFER_ID, request.golferId.value)
                .set(TEAM_ROSTERS.ACQUIRED_VIA, request.acquiredVia ?: DEFAULT_ACQUIRED_VIA)
                .set(TEAM_ROSTERS.DRAFT_ROUND, request.draftRound)
                .set(TEAM_ROSTERS.OWNERSHIP_PCT, request.ownershipPct ?: DEFAULT_OWNERSHIP_PCT)
                .returning()
                .fetchOne() ?: error("INSERT RETURNING produced no row for team_rosters")
        return toRosterEntry(inserted)
    }

    context(ctx: TransactionContext)
    override fun dropFromRoster(
        teamId: TeamId,
        golferId: GolferId,
    ): Boolean {
        val affected =
            ctx.dsl.update(TEAM_ROSTERS)
                .set(TEAM_ROSTERS.DROPPED_AT, DSL.currentOffsetDateTime())
                .set(TEAM_ROSTERS.IS_ACTIVE, false)
                .where(TEAM_ROSTERS.TEAM_ID.eq(teamId.value))
                .and(TEAM_ROSTERS.GOLFER_ID.eq(golferId.value))
                .and(TEAM_ROSTERS.DROPPED_AT.isNull)
                .execute()
        return affected > 0
    }

    context(ctx: TransactionContext)
    override fun getRosterView(seasonId: SeasonId): List<RosterViewTeam> {
        val rows = fetchRosterViewRows(seasonId)
        return rows.groupBy { it.teamId to it.teamName }.map { (key, picks) ->
            RosterViewTeam(
                teamId = key.first,
                teamName = key.second,
                picks = picks.map(::toRosterViewPick),
            )
        }
    }

    context(ctx: TransactionContext)
    private fun fetchRosterViewRows(seasonId: SeasonId): List<RosterViewRow> =
        ctx.dsl.select(
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
